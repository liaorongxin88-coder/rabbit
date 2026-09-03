# Rabbit admin conventions

## Scope and evidence

The `admin/` package contains two operational surfaces in one React application:

- the platform console under `/login` and the platform routes;
- the rabbit-farm workspace under `/workspace/**`.

They share layout and UI primitives, but they do not share identities, tokens, route guards, request clients, or authorization rules. Repository guidance sets the broad package boundary. `admin/.rules` is the engineering and product contract, and `admin/DESIGN.md` is the canonical source for visible and interactive behavior.

Representative evidence:

- `AGENTS.md`
- `admin/.rules`
- `admin/DESIGN.md`

## Package and directory ownership

Use the existing directory split rather than moving route, transport, and reusable UI concerns into the same module.

- `src/App.tsx` owns the route tree, authenticated shell wiring, and session-level navigation.
- `src/pages/` contains route-level pages. Pages coordinate requests, permissions, loading, filters, pagination, dialogs, and reusable components.
- `src/components/` contains shared domain and layout components.
- `src/components/ui/` contains generic, prop-driven shadcn-style primitives.
- `src/api/` contains business API methods. Components and pages call these functions instead of an HTTP client directly.
- `src/lib/` contains request and session infrastructure plus pure domain helpers.
- `src/types/` contains shared transport and domain types.
- `src/index.css` owns semantic color tokens, motion tokens, and global animation classes.

Files generally use kebab-case names and named exports. Cross-directory imports normally use the `@/` alias. Type-only dependencies use `import type`.

The written style in `admin/.rules` is two-space indentation, single quotes, and no semicolons. Some workspace-era files use double quotes and semicolons. This is existing drift, not a second convention. New work should follow the written rule and avoid unrelated formatting churn.

Representative evidence:

- `admin/.rules`
- `admin/src/pages/farms-page.tsx`
- `admin/src/components/ui/button.tsx`

Observed exception:

- `admin/src/api/workspace.ts`
- `admin/src/components/batch-statistics.tsx`

## Entry point, routing, and shells

`src/main.tsx` mounts `StrictMode > BrowserRouter > App`. `App.tsx` keeps platform and workspace routing deliberately separate.

- `RequireAdminAuth` redirects unauthenticated platform users to `/login`.
- `RequireWorkspaceAuth` redirects unauthenticated business users to `/workspace/login`.
- Platform routes render inside `AppShell`.
- Workspace routes render inside `WorkspaceProvider` and `WorkspaceShell`.
- Workspace pages are lazy-loaded behind a shared `Suspense` fallback. Current platform pages use static imports.
- Permission-sensitive routes redirect rather than rendering forbidden controls. The accounts page checks `platform:accounts:list` before it mounts.
- `/merchant/**` exists only as a compatibility redirect to `/workspace/**`; it preserves search and hash data.
- Unknown paths return to the dashboard for their current surface.

Do not combine the two shells or treat the workspace as a renamed platform console. Their audiences, authorization scopes, and writable operations differ.

Representative evidence:

- `admin/src/main.tsx`
- `admin/src/App.tsx`
- `admin/src/components/workspace-shell.tsx`

## Page and component composition

Pages own route workflows. A typical page uses `useCallback` for an async loader, calls it from `useEffect`, stores the result and loading flag in local state, and reloads after a successful mutation. Filters that should not refetch on every keystroke keep draft input separate from submitted criteria.

Reusable behavior moves down only when it is shared or independently meaningful:

- layout and navigation belong in shell components;
- domain display belongs in components such as status badges, metric cards, cage maps, and event streams;
- generic mechanics belong in `components/ui`;
- pure calculation, normalization, labels, paths, and idempotency logic belong in `lib`.

Write actions disable their controls while pending. Dialogs reset or initialize their draft state when opened. Most successful mutations close the dialog and call the owning loader or `workspace.refresh()`; the codebase does not use optimistic cache updates.

Representative evidence:

- `admin/src/pages/farms-page.tsx`
- `admin/src/pages/workspace-members-page.tsx`
- `admin/src/components/admin-account-form-dialog.tsx`

## UI primitives and forms

Shared primitives wrap Radix or local HTML behavior and expose variants through props. `Button` uses CVA for `variant`, `size`, and `asChild`. Dialog, Select, Tabs, Label, and Separator build on Radix. Tables, fields, cards, badges, skeletons, spinners, and empty states are small local components.

Compose these primitives instead of rebuilding their focus, disabled, spacing, and semantic behavior in pages. Do not add another UI kit for a routine screen.

Forms use `FieldGroup`, `Field`, `FieldLabel`, `Input`, `Textarea`, and the shared Select. Browser constraints and explicit validation toasts are both present. Keep labels associated with their controls. Some current Select labels do not have matching trigger IDs; this is an accessibility gap, not a pattern to copy.

Dialogs include a title and description. The shared dialog content is bounded by the viewport and offsets itself from the desktop sidebar. Long forms keep the header and footer reachable while the field area scrolls.

Tables are compact and horizontally scrollable. The first columns establish row identity, status uses badges or text, actions stay understandable on narrow screens, and pagination sits below the table.

Representative evidence:

- `admin/src/components/ui/button.tsx`
- `admin/src/components/ui/dialog.tsx`
- `admin/src/components/ui/field.tsx`

## Visual design and copy

The admin is a compact operational tool. It is not a landing page or a marketing surface.

- Use the semantic utilities defined in `index.css`, including `bg-background`, `bg-card`, `bg-secondary`, `text-muted-foreground`, `border`, `bg-primary`, `text-warning`, and `text-destructive`.
- Primary teal communicates intent or selection. Green is positive state, amber is warning, and red is error or destructive action.
- Do not scatter raw Tailwind status colors through page code.
- Keep the main card radius at 8px or less and controls at 6px.
- Body copy is 14px by default. Letter spacing remains zero or `tracking-normal`.
- Use `lucide-react` for icons. Decorative icons are hidden from assistive technology. Icon-only controls need an accessible name.
- Operational copy is concise Chinese. Buttons use verbs, destructive actions name the object or consequence, and empty states explain what is missing without tutorial text.

Prohibited visual patterns include decorative gradients, blobs, bokeh, illustration backgrounds, oversized hero sections, broad tinted page bands, nested cards, floating page-section cards, and excessive empty space.

Representative evidence:

- `admin/DESIGN.md`
- `admin/src/index.css`
- `admin/src/components/metric-card.tsx`

## Layout, responsive behavior, and motion

Desktop uses a fixed 16rem sidebar, an `lg:pl-64` content offset, and content constrained to `max-w-7xl` (80rem). Mobile replaces the sidebar with a compact header or horizontal workspace navigation. Headers, filters, and actions stack before switching to horizontal layouts.

At the 320px minimum width:

- controls may wrap but must not overlap;
- tables may scroll horizontally;
- primary row identity and actions must remain clear;
- dialog actions must remain reachable;
- text must not cover adjacent content.

Motion uses the shared 140ms, 220ms, and 340ms tokens. It is reserved for page entry, section entry, dialogs, hover lift, and press feedback. Avoid looping motion, bounce, parallax, and movement of layout-critical surfaces beyond 2px. `prefers-reduced-motion` reduces animation and transition durations and removes transforms.

Representative evidence:

- `admin/DESIGN.md`
- `admin/src/components/app-shell.tsx`
- `admin/src/index.css`

## Accessibility and UI states

Visible focus rings use the semantic ring token. Invalid inputs use `aria-invalid`. Radix dialog title and description semantics are retained. Icon-only buttons provide screen-reader labels, while decorative Lucide icons use `aria-hidden="true"`.

State must not be communicated by color alone. Loading views use skeletons or a spinner, mutation controls are disabled while saving, and empty views distinguish missing data from filtered results when the workflow needs that distinction. Components with a recoverable child request expose a local error and retry control instead of discarding unrelated data.

`BatchStatisticsSummary` labels its loading region with `aria-live="polite"` and uses `role="alert"` for a failed load. `CageMap` provides text and icon equivalents, `aria-pressed` for selection, and full cage descriptions in `aria-label`.

An older platform-page pattern clears data after a failed load and falls through to the empty branch, leaving the shared toast as the only error signal. Do not treat this as the preferred error-state design for new work.

Representative evidence:

- `admin/src/components/batch-statistics.tsx`
- `admin/src/components/cage-map.tsx`
- `admin/src/pages/farms-page.tsx`

## Cage map exception

The cage map is the explicit exception to the otherwise restrained use of status color. It may use low-opacity semantic state colors because operators need to scan spatial attention states. The exception has strict rules:

- primary teal means selection only and never encodes cage status;
- every status color has an icon and Chinese text label;
- one prioritized attention state is shown per cage;
- filters dim nonmatching cells instead of removing them;
- layers are ordered bottom-up, one layer is shown at a time, rows stay separate, and positions remain ascending;
- missing positions remain visible so the spatial coordinate system does not collapse;
- the table view remains available for dense lookup.

Malformed coordinates and duplicate positions are not silently dropped. Pure layout logic places those cages in an unplaced bucket, and tests pin this behavior.

Representative evidence:

- `admin/DESIGN.md`
- `admin/src/components/cage-map.tsx`
- `admin/src/lib/cage-map.ts`

## API modules and request contracts

Business API methods live under `src/api`. They build typed requests through the shared helpers in `src/lib/request.ts`. Components must not use bare `fetch`, ad hoc Axios clients, inline base URLs, or direct Alova client calls.

The response contract is `{ code, message, data }`. The shared response interceptor reads and parses the body, requires a numeric `code`, treats only `code === 0` as success, and returns the unwrapped `data`. A nonzero business code becomes `ApiError`. Code `501` is intentionally not toasted because callers may use it for supported fallback behavior, such as deployments without captcha.

Empty and non-JSON responses are errors. A 404 non-JSON response adds a proxy/base URL hint. GET caching is disabled with `cacheFor: 0`; screens own their snapshots and refresh explicitly.

Two calling styles currently exist:

- platform API modules call `.send()` and return a promise;
- `src/api/workspace.ts` returns the Alova method object directly and consumers await its thenable behavior.

This is a real exception. New guidance should avoid claiming that every API function calls `.send()` until the package is made consistent.

Representative evidence:

- `admin/src/lib/request.ts`
- `admin/src/api/farms.ts`
- `admin/src/api/workspace.ts`

## Security, sessions, and farm scope

Platform APIs stay under `/api/admin/**`. Business workspace APIs stay under the existing `/api/**` contracts. Platform admins may inspect business production summaries but must not edit houses, cages, rabbits, feed, treatment, breeding, or sales data through platform routes. Workspace writes must use existing business contracts and their role and permission checks.

The shared request module creates separate platform and workspace Alova clients. Each request reads its current bearer token from `localStorage`. Workspace requests add `X-House-Id` when the operation is house-scoped; platform requests must not send or depend on that header.

A `401` clears only the matching session scope. Workspace `403` clears the workspace session only for the exact backend message `账号已停用`; ordinary permission and farm-access failures retain the session. This exact localized-message match is current behavior and is covered by tests.

Session writes dispatch custom browser events. Hooks in `App.tsx` subscribe to those events and to cross-tab `storage` changes, causing route guards to rerender. Stored session objects are parsed with type assertions rather than runtime schema validation, so their existence is the current guard criterion.

Farm selection is persisted per user. A stored house ID is accepted only if it still appears in the signed-in user's accessible-house response. Access must never be inferred from a phone number, caller-supplied user ID, or stale selection; the backend must authorize each scoped request.

Representative evidence:

- `admin/src/lib/request.ts`
- `admin/src/lib/auth.ts`
- `admin/test/request-auth.test.mjs`

## React state and request lifecycle

The package does not use Redux, TanStack Query, SWR, or another shared server cache. Most server data, filters, forms, pagination, selection, and dialog state live in component `useState`. Workspace houses, selected house, and permission are the main shared server snapshot in context. Sessions and the per-user selected house are the persistent client state.

The standard read flow is:

1. define a loader with `useCallback`;
2. call it from `useEffect` with `void load()`;
3. set loading before the request;
4. update or clear the snapshot in `try/catch`;
5. clear loading in `finally`;
6. call the same loader after a successful mutation.

Race protection is selective. The workspace provider uses an `active` flag for permission loading, and some newer components use request-generation counters. Many page loaders do not abort or reject stale responses. New concurrent loaders should guard against an older request overwriting newer filters, route params, or selected-house data.

The workspace provider retains an `error` field, but the current workspace shell does not render it directly. Feature components such as the operation event stream provide stronger local retry behavior and preserve already loaded rows on pagination failure.

Representative evidence:

- `admin/src/components/workspace-context.tsx`
- `admin/src/pages/workspace-members-page.tsx`
- `admin/src/components/operation-event-stream.tsx`

## Types and compatibility

`src/types/api.ts` is the main shared type catalog. It contains the response envelope, sessions, pagination, core entities, request/result types, and cursor pages. Focused feature contracts may use their own files. API-local payload and parameter types are also common when they are not reused elsewhere.

Use explicit unions for stable roles, statuses, and actions. Some workflow fields remain plain `string` or `Record<string, unknown>` because the backend contract is still broad; preserve that looseness only where the source contract requires it.

Optional fields often represent compatibility with older backend data. Normalizers and display helpers prefer the current field but retain legacy fallbacks. Unknown server enum values remain visible instead of disappearing or being coerced into a known label.

Representative evidence:

- `admin/src/types/api.ts`
- `admin/src/types/rabbit-sale.ts`
- `admin/src/lib/rabbits.ts`

## Idempotency and mutation behavior

High-risk and retryable writes use request IDs. Pure helpers keep the same `requestId` when the user retries an unchanged action, generate a new ID when meaningful input changes, and clear the saved draft after success. This behavior is tested independently of React.

Do not generate a fresh idempotency key on every click if the click is a retry of the same unresolved write. Keep helper logic in `lib` so it can be tested without rendering a component.

The product boundary also matters: farm status, business-user status, owner membership, and member-role changes are high-risk. Changing a user's state affects access to every farm, while changing a farm's state must not silently change user state.

Representative evidence:

- `admin/src/lib/farm-request.ts`
- `admin/src/lib/batch-workflow.ts`
- `admin/test/batch-workflow.test.mjs`

## Unit testing

`pnpm --dir admin test` runs Node's built-in test runner over `test/*.test.mjs`. Tests import erasable `.ts` helpers directly, use `node:assert/strict`, and keep flat deterministic cases without a React rendering harness or mocking library.

Unit tests concentrate on pure domain behavior:

- Asia/Shanghai date parsing and serialization;
- idempotency-key reuse and rotation;
- compatibility labels and legacy-field precedence;
- exact session-clearing rules;
- cage layout, malformed coordinates, attention priority, and occupancy text;
- cursor deduplication and raw fallback values for unknown operation types.

New reusable domain logic belongs in `lib` with focused tests. The current glob only discovers test files directly under `admin/test`; nested test directories would not run.

There is no current React Testing Library, Vitest, Jest, coverage threshold, snapshot suite, or component-level keyboard/accessibility harness. Do not document those as required current checks.

Representative evidence:

- `admin/package.json`
- `admin/test/date.test.mjs`
- `admin/test/cage-map.test.mjs`

## Browser E2E and visual checks

The browser scripts use Playwright directly rather than the Playwright test runner. They start or reuse Vite, launch the installed system Chrome through `channel: "chrome"`, collect console and page errors, and exercise desktop `1440x900` and narrow `390x844` viewports.

The batch-statistics and operation-event scripts intercept `/api/**` and cover ready, empty, failed, retry, and narrow-screen states without a backend. Retry assertions ensure that only the failed child resource reloads. These scripts do not verify backend contracts, CORS, authentication, or persistence.

The cage-operations script uses a real backend and MySQL fixture. It checks login, CORS, stale-backend detection, UI actions, screenshots, overflow, table scrolling, reachable dialog actions, console/page errors, and final database state. It mutates fixture data and has local container assumptions, so it is not part of the default CI gate.

CI currently runs install, lint, unit tests, and build for admin changes. It does not run any browser script.

Representative evidence:

- `admin/scripts/batch-statistics-browser-e2e.mjs`
- `admin/scripts/operation-events-browser-e2e.mjs`
- `admin/scripts/admin_cage_ops_browser_e2e.mjs`

## Toolchain and dependency rules

The package pins pnpm 11.22.0 and uses Node 24 in CI. The build is `tsc -b && vite build`. TypeScript uses bundler module resolution, the `@/*` path alias, erasable syntax, no unused locals or parameters, no switch fallthrough, and `skipLibCheck`.

Oxlint enables React, TypeScript, and Oxc rules. React hook violations are errors. Fast Refresh warnings for files that export components and helpers are accepted in the primitive layer after review; they are not a reason to split a cohesive primitive mechanically.

Use existing dependencies before adding packages: Tailwind and local primitives for styling, Radix for present primitive families, Lucide for icons, Alova for requests, and Sonner for toast feedback. Keep Vite on the configured major unless a concrete build failure requires a version change.

Representative evidence:

- `admin/package.json`
- `admin/tsconfig.app.json`
- `admin/.oxlintrc.json`

## Enforced anti-patterns

The admin specs should state these as prohibitions because repository documents or code boundaries support them:

- Do not change backend contracts or Flutter code as a side effect of admin work.
- Do not merge platform and workspace tokens, clients, route guards, or logout flows.
- Do not omit `X-House-Id` accidentally from a house-scoped workspace request.
- Do not add an organization or account-group layer between a business user and farm membership.
- Do not infer farm authorization in the browser.
- Do not issue HTTP requests directly from components.
- Do not add routine UI, CSS, request, or state dependencies when the current stack covers the need.
- Do not make platform routes write business production data.
- Do not add billing, plans, public registration, support impersonation, cross-farm editing, or hardware-dependent controls without explicit scope.
- Do not use color as the only state signal.
- Do not remove or reflow spatial cage positions when filtering.
- Do not use decorative landing-page composition in the operational console.
- Do not perform unrelated cleanup or formatting while changing a focused workflow.

Representative evidence:

- `admin/.rules`
- `admin/DESIGN.md`
- `admin/src/lib/request.ts`

## Current exceptions and gaps to document accurately

- `src/api/workspace.ts` returns Alova method objects while platform API modules call `.send()`.
- Several workspace-era files use double quotes and semicolons despite the written style rule.
- Some Select labels are not programmatically associated with their trigger.
- Some page loaders collapse a failed request into the ordinary empty view after the shared toast.
- Race protection exists in selected loaders but is not a package-wide pattern.
- Session JSON uses type assertions without runtime shape or expiry validation.
- The request layer clears a disabled workspace session by matching one exact Chinese error message.
- No component-test harness or accessibility automation is configured.
- CI does not run browser E2E.
- `admin/.rules` and the root `AGENTS.md` understate the current unit-test gate; package scripts, admin docs, and CI do run it.

Representative evidence:

- `admin/src/api/workspace.ts`
- `admin/src/components/workspace-context.tsx`
- `.github/workflows/_quality-gates.yml`

## Recommended admin spec files

The admin layer should use a small set of topic files with minimal overlap:

- `admin/index.md`: architecture summary, pre-development checklist, topic index, and quality checks.
- `admin/architecture.md`: two-surface boundary, directory ownership, route and shell composition, imports, and change discipline.
- `admin/components-and-state.md`: page/component responsibilities, primitives, forms, local versus shared state, async loaders, mutations, and idempotency.
- `admin/data-access-and-types.md`: API modules, Alova clients, envelopes, errors, cache behavior, sessions, `X-House-Id`, permissions, type ownership, and compatibility fields.
- `admin/design-and-accessibility.md`: semantic tokens, layout, responsive behavior, motion, icons, loading/error/empty states, dialogs, tables, copy, and the cage-map exception.
- `admin/testing.md`: Node unit-test style, browser scripts, CI checks, known gaps, and when each check is required.

## Recommended validation checks

Run the package gate for admin code changes:

```bash
corepack enable
corepack prepare pnpm@11.22.0 --activate
pnpm --dir admin install --frozen-lockfile
pnpm --dir admin lint
pnpm --dir admin test
pnpm --dir admin build
```

Run the relevant browser script for visible or workflow changes:

```bash
pnpm --dir admin e2e:browser
pnpm --dir admin e2e:browser:batch-statistics
pnpm --dir admin e2e:browser:operation-events
```

For visible changes, also inspect desktop and narrow viewports for console errors, horizontal page overflow, text overlap, dialog placement, keyboard focus, and reachable actions. The cage operations script requires a compatible backend and MySQL fixture and should not be treated as an isolated frontend check.

For the generated Trellis specs, verify that every topic is linked from `admin/index.md`, every firm rule has evidence, exceptions are not rewritten as universal conventions, and no placeholder language remains:

```bash
python3 ./.trellis/scripts/get_context.py --mode packages
rg -n "To fill|TODO|TBD|your project|placeholder" .trellis/spec
python3 -m pytest .trellis/tests -q
```

The Trellis pytest command is conditional on `.trellis/tests/` existing.
