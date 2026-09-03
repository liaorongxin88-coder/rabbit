# Cross-layer change guide

Use this guide when a change affects more than one of the database, backend, admin, or Flutter boundaries.

## Map the contract

Write down the concrete flow before editing:

```text
Flyway schema
-> MyBatis mapper and XML
-> service transaction and domain rules
-> controller DTO and permission boundary
-> API envelope
-> admin and Flutter transport models
-> feature state
-> visible UI behavior
```

Not every change uses every step. Mark the actual producers, transformations, and consumers rather than assuming a full-stack path.

## Identify the source of truth

- Flyway migrations own deployed schema history.
- Backend request and response DTOs own wire field names and nullability.
- `PermissionCode` and backend access checks own authorization semantics.
- Server-provided labels, action dictionaries, and derived fields remain server-owned.
- Client domain models may narrow presentation behavior but must not invent a different wire contract.

Representative paths:

- `backend/rabbit-boot/src/main/resources/db/migration/`
- `backend/rabbit-access/src/main/java/com/rabbit/app/security/permission/PermissionCode.java`
- `admin/src/types/api.ts`
- `app/lib/src/domain/`

## Check every boundary

For each changed field or enum, verify:

- serialized name, type, nullability, and default behavior;
- success and failure envelope handling;
- permission and house-scope requirements;
- date/time representation;
- retry and `requestId` behavior for writes;
- pagination shape and cursor ownership;
- backward compatibility for stored local state or older clients.

Do not infer one endpoint's pagination or date shape from another endpoint. Rabbit currently uses several explicit variants.

## Security changes

Client-side visibility is not authorization. A protected action still needs the appropriate backend permission declaration, defensive service behavior where applicable, and house-isolated SQL.

Authentication and tenancy changes require focused review of:

- ordinary versus platform JWT routing;
- `X-House-Id` selection and membership validation;
- disabled account, house, and membership states;
- session behavior on `401` and `403`;
- path/body identifiers that must agree with the selected house.

Read [Rabbit Cross-Application Contracts](./rabbit-cross-application-contracts.md) before making these changes.

## Verification

Start with the root gate:

```bash
scripts/ci/check.sh
```

Then add checks for the affected contract:

- Backend unit tests in the owning Maven module.
- Backend `*IT.java` tests through `bash scripts/e2e-local.sh -Dit.test=<TestName>IT` when MySQL, HTTP security, idempotency, or full application wiring matters. Use the script because `docs/project/testing.md` warns that the raw Maven profile has unsafe local database defaults.
- Admin lint, tests, build, and browser checks for visible UI changes.
- Flutter `./rabbit check` and focused integration tests for device workflows.

A root check alone is insufficient for MySQL-backed cross-layer behavior because it does not run the backend E2E profile.

## Review checklist

- [ ] Traced the actual producer and every maintained consumer.
- [ ] Kept wire models synchronized across Java, TypeScript, and Dart.
- [ ] Verified house isolation and permissions at the backend.
- [ ] Preserved API envelope, date, pagination, and idempotency semantics.
- [ ] Tested the narrow contract and the relevant full workflow.
- [ ] Updated architecture or contract documentation when the source of truth changed.
