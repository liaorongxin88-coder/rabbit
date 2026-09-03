# Admin guidelines

`admin/` is a React 19 and TypeScript operational console with two security surfaces. Platform administration uses `/login`, `/api/admin/**`, `AppShell`, and a platform token. Farm work uses `/workspace/**`, business APIs, `WorkspaceShell`, a business token, and `X-House-Id` on house-scoped requests. They share UI primitives but not identity or authorization state.

`admin/.rules` is the engineering contract and `admin/DESIGN.md` defines visible behavior. Current code and tests settle implementation details when older prose has drifted.

## Pre-Development Checklist

- Read [Architecture](./architecture.md) before adding a route, page, dependency, or shared module.
- Read [Components and state](./components-and-state.md) before changing components, forms, loaders, mutations, context, or request IDs.
- Read [Data access and types](./data-access-and-types.md) before changing an API method, session, permission, response, or model.
- Read [Design and accessibility](./design-and-accessibility.md) for any visible or interactive change.
- Read [Testing](./testing.md) before adding reusable logic or choosing browser checks.
- Read [Rabbit cross-application contracts](../guides/rabbit-cross-application-contracts.md) for authentication, house scope, API envelopes, pagination, idempotency, or dates.

## File index

| File | Scope |
| --- | --- |
| [Architecture](./architecture.md) | Two-surface boundary, routing, shells, directories, imports, and dependencies |
| [Components and state](./components-and-state.md) | Page composition, primitives, forms, loaders, mutations, and state ownership |
| [Data access and types](./data-access-and-types.md) | Alova clients, envelopes, sessions, house scope, permissions, and TypeScript models |
| [Design and accessibility](./design-and-accessibility.md) | Tokens, layout, responsive behavior, states, motion, semantics, and cage maps |
| [Testing](./testing.md) | Node tests, browser scripts, CI gates, and current gaps |

## Quality Check

Use the pinned pnpm 11.22.0 toolchain. For admin code changes, run:

```bash
corepack enable
corepack prepare pnpm@11.22.0 --activate
pnpm --dir admin install --frozen-lockfile
pnpm --dir admin lint
pnpm --dir admin test
pnpm --dir admin build
```

Run the relevant browser command listed in [Testing](./testing.md) for visible or workflow changes. Check desktop `1440x900` and narrow `390x844` layouts for page overflow, text overlap, focus, reachable dialog actions, and console errors. The real-backend cage operations script also requires its MySQL fixture and local backend.

For changes limited to these guidelines, do not run product checks. Validate the Markdown links, search `.trellis/spec/admin/` for template markers, and run `git diff --check -- .trellis/spec/admin/`.
