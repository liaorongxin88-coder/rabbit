# Data access and types

Business API methods live in `admin/src/api/` and use the shared Alova clients in `admin/src/lib/request.ts`. Components and pages must not use bare `fetch`, create ad hoc Axios or Alova clients, or embed base URLs.

## Request contract

The response shape is `{ code, message, data }`. The response interceptor requires a numeric `code`, treats only `code === 0` as success, and returns `data`. Nonzero codes become `ApiError`. Code `501` is not toasted because callers use it for supported fallback behavior. Empty and non-JSON responses are errors; a non-JSON 404 includes a proxy or base URL hint. GET caching is disabled with `cacheFor: 0`, so screens own snapshots and refresh them explicitly.

Platform API modules such as `admin/src/api/farms.ts` call `.send()`. `admin/src/api/workspace.ts` returns Alova method objects and consumers await their thenable behavior. This mixed style is a current exception. Preserve the local API module's style during focused work; do not claim every method uses `.send()`.

## Sessions and house scope

The request module creates separate platform and workspace clients. Each request reads its current token from `localStorage`. A `401` clears only the matching session. Workspace `403` clears its session only for the exact backend message `账号已停用`; ordinary denial retains authentication. `admin/test/request-auth.test.mjs` enforces these cases.

Workspace requests add `X-House-Id` only when house-scoped. `workspaceConfig` currently adds the header for any truthy `houseId`; callers must pass a positive accessible-house ID rather than treating that helper as validation. A saved selection is accepted only if it remains in the signed-in user's accessible-house response. Never infer access from a phone number, supplied user ID, display role, or stale selection; the backend authorizes every scoped request.

Use `permissions` as the authoritative client capability list. `role` is display information, while `perms` and `isAdmin` remain compatibility fields. `admin/src/lib/permissions.ts` checks only `permissions`; hiding or disabling a client control never replaces backend enforcement.

Session writes dispatch custom browser events, and `App.tsx` hooks also listen for cross-tab storage changes. Stored JSON currently uses TypeScript assertions without runtime shape or expiry validation. Treat that as an existing gap.

## Types and compatibility

`admin/src/types/api.ts` is the main catalog for envelopes, sessions, pagination, core entities, request results, and cursor pages. Focused feature contracts may live in their own type file; one-off payload types may remain next to an API method.

Use explicit unions for stable roles, statuses, and actions. Keep `string` or `Record<string, unknown>` only where the backend contract is intentionally broad. Compatibility normalizers may prefer a current field and retain a legacy fallback. Unknown server enum values must remain visible instead of disappearing or being coerced to a known label; `admin/src/lib/rabbits.ts` demonstrates that approach.

The declared `ApiResponse` type currently models `data` as required even though error envelopes may omit it. The backend also returns `X-Trace-Id`, but the shared response parser currently unwraps only the body and does not expose that header to callers. These are compatibility and diagnostic gaps documented in [Rabbit cross-application contracts](../guides/rabbit-cross-application-contracts.md), not reasons to alter unrelated calls.

## Protected binary downloads

House-scoped reports use `workspaceDownloadBlob` in `admin/src/lib/request.ts`. It is the one approved raw `fetch` boundary: it reads the current business token, sends `Authorization`, `X-House-Id`, and the expected media type, applies the same session-invalidating rules as JSON requests, and parses error envelopes before exposing bytes. Components and pages still must not call `fetch` directly.

Validate the exact response MIME type and reject empty files. Parse the RFC 5987 `filename*` value first, fall back to the ASCII filename, sanitize it, and always revoke object URLs after triggering a browser download. A download failure must leave the last successful page data intact.
