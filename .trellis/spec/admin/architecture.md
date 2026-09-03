# Admin architecture

`admin/src/main.tsx` mounts `StrictMode`, `BrowserRouter`, and `App`. `admin/src/App.tsx` owns the route tree, authenticated shells, lazy workspace pages, and session-level navigation.

## Two application surfaces

Keep platform and workspace identities separate:

- `RequireAdminAuth` sends unauthenticated platform users to `/login`; platform routes use `AppShell`.
- `RequireWorkspaceAuth` sends business users to `/workspace/login`; workspace routes use `WorkspaceProvider` and `WorkspaceShell`.
- Platform requests use `/api/admin/**` and never depend on `X-House-Id`.
- Workspace requests use business contracts and add `X-House-Id` when the operation is house-scoped.
- The `/accounts` route checks `platform:accounts:list` before mounting its page; `AppShell` uses the same permission to filter navigation.
- Unknown platform paths return to `/dashboard`, while unknown workspace paths return to `/workspace/dashboard`.

Do not merge tokens, request clients, route guards, logout behavior, or permission logic. Client route checks and hidden navigation improve usability but do not replace backend authorization. `/merchant/**` is a compatibility redirect that preserves search and hash data, not a third application surface. `admin/src/App.tsx` and `admin/src/components/app-shell.tsx` are the evidence for these route contracts.

## Directory ownership

- `src/pages/` owns route workflows: loading, filters, permissions, pagination, dialogs, and request coordination.
- `src/components/` owns reusable layout and domain display.
- `src/components/ui/` owns generic prop-driven primitives built from Radix or local HTML.
- `src/api/` owns business API methods. Pages and components do not call transport clients directly.
- `src/lib/` owns request and session infrastructure plus pure domain helpers.
- `src/types/` owns shared transport and domain contracts.
- `src/index.css` owns semantic colors, motion tokens, and global animation classes.

`admin/src/pages/farms-page.tsx`, `admin/src/components/ui/button.tsx`, and `admin/src/lib/request.ts` show these boundaries.

Use kebab-case filenames, named exports, the `@/` alias across directories, and `import type` for type-only dependencies. `admin/.rules` specifies two-space indentation, single quotes, and no semicolons. Some workspace-era files, including `admin/src/api/workspace.ts`, use double quotes and semicolons. That is existing drift, not a second style. Avoid unrelated formatting churn.

## Change discipline

Use existing Tailwind, Radix, Lucide, Alova, and Sonner dependencies before adding another package. Do not move backend or Flutter changes into an admin task without an owned contract change. Platform screens may inspect production summaries but must not write farm production data through platform routes; `admin/.rules` states that product boundary.
