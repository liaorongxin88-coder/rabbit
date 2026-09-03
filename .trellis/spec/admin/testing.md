# Admin testing

`pnpm --dir admin test` runs Node's built-in test runner over `admin/test/*.test.mjs`. Tests import erasable TypeScript helpers directly, use `node:assert/strict`, and keep deterministic cases without a React render harness or mocking library.

Put reusable domain behavior in `src/lib/` and test dates, compatibility mappings, request-ID reuse, session clearing, cage layout, cursor handling, and similar pure contracts there. `admin/test/date.test.mjs`, `batch-workflow.test.mjs`, and `cage-map.test.mjs` are representative. The current glob does not discover nested test directories.

There is no Vitest, Jest, React Testing Library, snapshot suite, coverage threshold, or automated accessibility harness. Do not list those as current requirements.

## Browser checks

Browser scripts use Playwright directly with installed Chrome. They start or reuse Vite, capture page and console errors, and exercise desktop `1440x900` and narrow `390x844` layouts.

- `admin/scripts/batch-statistics-browser-e2e.mjs` intercepts APIs and covers ready, empty, failed, retry, and narrow states.
- `admin/scripts/operation-events-browser-e2e.mjs` verifies cursor views and local retry without reloading unrelated data.
- `admin/scripts/admin_cage_ops_browser_e2e.mjs` uses a real backend and MySQL fixture, mutates data, checks CORS and stale backends, captures screenshots, and verifies final database state.

The mocked scripts do not verify backend contracts, authentication, CORS, or persistence. The cage script is not an isolated frontend check and is not part of the default CI gate.

## Commands

```bash
pnpm --dir admin lint
pnpm --dir admin test
pnpm --dir admin build

pnpm --dir admin e2e:browser
pnpm --dir admin e2e:browser:batch-statistics
pnpm --dir admin e2e:browser:operation-events
```

CI uses Node 24 and pinned pnpm 11.22.0, then runs install, lint, unit tests, and build for admin changes. `admin/package.json`, `admin/tsconfig.app.json`, `admin/.oxlintrc.json`, and `.github/workflows/_quality-gates.yml` define the gate. React hook violations are lint errors; reviewed Fast Refresh warnings in cohesive primitive files are accepted.
