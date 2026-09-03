# Code reuse guide

Search before adding a helper, constant, domain mapping, request wrapper, or UI primitive. Rabbit already centralizes several behaviors, and a second implementation can drift from the first without an obvious compile error.

## Find the current owner

Search by concept and by serialized value before writing code:

```bash
rg -n "requestId|X-House-Id|permissions|Asia/Shanghai" backend admin app
rg -n "class .*Service|interface .*Repository|Provider<|NotifierProvider" backend app
rg -n "export (function|const|type)|components/ui" admin/src
```

Check these common ownership locations first:

- Backend infrastructure: `backend/rabbit-platform/src/main/java/com/rabbit/app/`
- Backend access rules: `backend/rabbit-access/src/main/java/com/rabbit/app/security/`
- Admin helpers and API contracts: `admin/src/lib/`, `admin/src/api/`, and `admin/src/types/`
- Admin UI primitives: `admin/src/components/ui/`
- Flutter domain rules: `app/lib/src/domain/`
- Flutter transport and repositories: `app/lib/src/data/`
- Flutter state ownership: feature `view_models/` directories under `app/lib/src/ui/`

## Reuse boundaries

Reuse within an application when the behavior has the same contract. Do not create cross-language abstractions merely because Java, TypeScript, and Dart implement the same server contract. Keep the contract synchronized, but let each application use its native structure.

Good existing owners include:

- `ApiResponse`, `DateUtil`, `RequestIdUtil`, and `RequestDedupService` in `rabbit-platform`.
- `admin/src/lib/request.ts`, `admin/src/lib/date.ts`, and `admin/src/lib/farm-request.ts` for web-client transport behavior.
- `app/lib/src/data/services/network/client.dart` and domain-specific repository implementations for Flutter transport behavior.

## Patterns to avoid

- Repeating raw API-envelope decoding in pages, widgets, controllers, or services.
- Copying permission strings or role-to-capability mappings into several features.
- Reimplementing UTC+8 business-date conversion beside a feature form.
- Creating a second request ID lifecycle for one write path.
- Copying a shared UI primitive into a page because one visual variant is missing.
- Moving domain-specific logic into a generic utility only to reduce line count.

## When to extract

Extract shared behavior when at least two callers use the same contract and a clear ownership layer already exists. Keep behavior local when the similarity is only visual or when domain rules differ.

Before extracting, check:

1. Whether the inputs, failures, and side effects are genuinely the same.
2. Whether the proposed owner can depend on every required type without violating module or layer rules.
3. Whether focused tests can describe the shared contract.
4. Whether callers become clearer after extraction.

## Review checklist

- [ ] Searched all three maintained applications for the concept and serialized values.
- [ ] Reused the established helper, repository, primitive, or domain owner where appropriate.
- [ ] Reused established permission, farm-date, pagination, and request-ID contracts; view code owns only the state and presentation its workflow needs.
- [ ] Avoided a cross-module dependency that violates backend or Flutter architecture tests.
- [ ] Updated every client model affected by a backend contract change.
- [ ] Added tests at the shared owner's boundary when behavior changed.
