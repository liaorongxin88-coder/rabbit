# Cross-Application Contract Research

## Sources

- `AGENTS.md`
- `docs/project/architecture.md`
- `docs/project/testing.md`
- `docs/backend/modules/api-and-permissions.md`
- `docs/backend/modules/direct-house-access.md`
- `scripts/ci/check.sh`
- Backend authentication, access-control, response, deduplication, and date utilities
- Admin request, permission, API type, request ID, and date helpers
- Flutter network, permission, repository, event, and date-policy code

## Authentication Boundaries

Rabbit has two separate authentication domains:

- `/api/admin/**` accepts only a platform administrator JWT and does not use `X-House-Id`.
- Business `/api/**` endpoints use the ordinary user JWT. Platform tokens must not enter this client path.
- A business account with no houses remains authenticated and may use account, house discovery, house creation, and workspace endpoints.
- Clients clear the relevant session on `401`. A general `403` is an authorization result and normally preserves the session. Both clients also handle the specific disabled-account response separately.

Evidence:

- `backend/rabbit-access/src/main/java/com/rabbit/app/security/JwtAuthenticationFilter.java`
- `backend/rabbit-access/src/main/java/com/rabbit/app/security/PlatformAdminAuthenticationFilter.java`
- `admin/src/lib/request.ts`
- `app/lib/src/data/services/network/client.dart`

## House Isolation

`X-House-Id` selects the active business scope but does not prove access.

- House-scoped requests require a positive house ID.
- The backend rechecks user, house, membership, role, and permission state.
- Path and body house IDs must agree with the selected scope.
- SQL filters by `house_id`; child records without that column join through the owning house-scoped parent.
- A locally stored house ID is only a preference. Clients refresh accessible houses and discard stale selections.

Evidence:

- `docs/backend/modules/direct-house-access.md`
- `backend/rabbit-access/src/main/java/com/rabbit/app/security/AuthorizationInterceptor.java`
- `backend/rabbit-access/src/main/java/com/rabbit/app/security/AccessControlService.java`
- `docs/app/modules/auth-phone-wechat-flow.md`

## Permission Contract

- `permissions` is the authoritative client capability list.
- `role` is display information, not the basis for feature authorization.
- `perms` and `isAdmin` are compatibility fields for older clients.
- UI visibility improves usability but never replaces controller annotations, service checks, or SQL isolation.

Evidence:

- `backend/rabbit-access/src/main/java/com/rabbit/app/security/permission/PermissionCode.java`
- `backend/rabbit-access/src/main/java/com/rabbit/app/modules/house/dto/HousePermissionInfo.java`
- `admin/src/lib/permissions.ts`
- `app/lib/src/domain/houses/permission.dart`

## API Envelope and Trace IDs

The common response envelope is `{ code, message, data }`, with `code === 0` for success.

- Business failure envelopes may omit `data`.
- HTTP success alone does not imply business success.
- Preserve business codes when callers need recovery behavior.
- Current business meanings include invalid input, unauthenticated, denied or disabled, conflict, deleted house, and duplicate request still processing.
- The backend returns `X-Trace-Id`; diagnostic paths should preserve it.

Evidence:

- `backend/rabbit-platform/src/main/java/com/rabbit/app/common/ApiResponse.java`
- `backend/rabbit-platform/src/main/java/com/rabbit/app/common/GlobalExceptionHandler.java`
- `backend/rabbit-platform/src/main/java/com/rabbit/app/common/TraceIdFilter.java`

Observed compatibility gaps to document rather than silently normalize during bootstrap:

- `admin/src/types/api.ts` models `data` as always present even though errors may omit it.
- Flutter currently treats a missing `code` as success, while the admin client requires a numeric code.

## Idempotent Writes

`requestId` belongs to a logical write draft rather than an individual HTTP attempt.

- Generate one ID when the draft begins.
- Reuse it after timeout, connection failure, or an unchanged retry.
- Generate a new ID when fingerprinted payload data changes or a successful write starts a new draft.
- The same ID and payload may replay the original result.
- The same ID with a different payload returns a conflict.
- An in-progress duplicate may be reported as still processing.
- IDs must fit the backend's 64-character storage limit. Derived child IDs use the shared utility.

Evidence:

- `backend/rabbit-platform/src/main/java/com/rabbit/app/modules/dedup/service/RequestDedupService.java`
- `backend/rabbit-platform/src/main/java/com/rabbit/app/util/RequestIdUtil.java`
- `admin/src/lib/farm-request.ts`
- `app/lib/src/data/repositories/batches/repository.dart`

## Pagination Contracts

Pagination is endpoint-specific and must not be forced into one guessed shape.

- Admin resource pages use `{ items, total, page, pageSize }`; request parameter names vary by endpoint.
- Reproduction tasks use `{ items, total, page, size }`.
- Some production lists return arrays and stop when a page contains fewer items than requested.
- Operation events use opaque cursor pagination: `{ items, nextCursor, hasMore }`. Clients return the cursor unchanged and do not decode or construct it.

Evidence:

- `backend/rabbit-reporting/src/main/java/com/rabbit/app/modules/admin/dto/PageResult.java`
- `backend/rabbit-production/src/main/java/com/rabbit/app/modules/repro/dto/TaskPage.java`
- `backend/rabbit-production/src/main/java/com/rabbit/app/modules/operation/dto/OperationEventPage.java`
- `admin/src/types/api.ts`
- `app/lib/src/domain/operation_events/event.dart`

## Date and Time Policy

Business dates use `Asia/Shanghai`.

- Natural business dates remain `yyyy-MM-dd`.
- Date-time values follow the representation declared by the endpoint, currently ISO instants or epoch milliseconds.
- Clients must not derive a farm business date from the browser or device timezone.
- Picker wall-clock values are interpreted as UTC+8; returned instants are converted back to farm wall-clock time for display.
- Server-derived fields such as `overdue` remain authoritative.

Evidence:

- `backend/rabbit-platform/src/main/java/com/rabbit/app/util/DateUtil.java`
- `admin/src/lib/date.ts`
- `app/lib/src/domain/reproduction/date_policy.dart`
- `docker-compose.yml`
- `docs/project/testing.md`

## Model Synchronization Risks

Cross-layer reviews should compare backend DTOs and enums with both client models when a contract changes. Current areas needing explicit attention include:

- `AuthTokenResponse` and business session fields.
- `HousePermissionInfo`, role values, membership status, and permission strings.
- `PageResult`, reproduction task pages, and operation event pages.
- Reproduction actions and result fields. The admin model does not currently declare every backend value and result field.
- Invitation documentation, which is older than the current DTO and client behavior.
- Server-provided task labels, stage actions, and entry-point requirements, which should not be duplicated as client-owned mappings.

## Verification Floor

`scripts/ci/check.sh` covers backend unit and style checks, admin install/lint/test/build, and Flutter `./rabbit check`. It does not run MySQL-backed E2E tests.

Changes to authentication, permissions, tenant isolation, idempotency, or API contracts require the relevant backend `*IT.java` coverage and consumer contract checks in addition to the root check.

## Spec Recommendation

Add `.trellis/spec/guides/rabbit-cross-application-contracts.md` and link it from the shared guide index. Layer-specific indexes should route cross-application API or authorization changes to this guide before implementation.
