# Rabbit cross-application contracts

Read this guide before changing authentication, permissions, house selection, API responses, write retries, pagination, or business dates.

## Authentication domains

Rabbit has separate platform and business authentication domains.

- `/api/admin/**` accepts the platform administrator JWT and does not use `X-House-Id`.
- Business `/api/**` endpoints use the ordinary user JWT. Do not send a platform token through this client path.
- A valid business account may have no houses. Preserve its JWT so it can use account, house discovery, house creation, and workspace endpoints.
- Clear the relevant session on `401`. A general `403` means denied access and normally preserves the session. Admin requires the exact disabled-account message `账号已停用`; Flutter trims the message before comparing it with that value.

Examples:

- `backend/rabbit-access/src/main/java/com/rabbit/app/security/JwtAuthenticationFilter.java`
- `backend/rabbit-access/src/main/java/com/rabbit/app/security/PlatformAdminAuthenticationFilter.java`
- `admin/src/lib/request.ts`
- `app/lib/src/data/services/network/client.dart`

## House scope

`X-House-Id` selects a scope; it is not proof of membership or permission.

- Send a positive house ID on house-scoped requests.
- Recheck account, house, membership, role, and permission state on the backend.
- Require path and body house IDs to agree with the selected scope.
- Filter SQL by `house_id`, joining through the owning house-scoped parent when a child table has no house column.
- Treat a locally stored current house as a preference. Refresh accessible houses and discard stale selections.

Examples:

- `backend/rabbit-access/src/main/java/com/rabbit/app/security/AuthorizationInterceptor.java`
- `backend/rabbit-access/src/main/java/com/rabbit/app/security/AccessControlService.java`
- `docs/backend/modules/direct-house-access.md`

## Permissions

Use `permissions` as the authoritative client capability list. `role` is display information. `perms` and `isAdmin` are compatibility fields and should not become dependencies for new controls.

Hiding an action in a client does not replace backend enforcement. Keep controller, service, and SQL checks aligned with the operation's risk.

Examples:

- `backend/rabbit-access/src/main/java/com/rabbit/app/security/permission/PermissionCode.java`
- `admin/src/lib/permissions.ts`
- `app/lib/src/domain/houses/permission.dart`

## API envelope

The shared response shape is:

```text
{ code, message, data }
```

`code === 0` means business success. Error envelopes may omit `data`, and an HTTP success status does not by itself mean the operation succeeded. Preserve business codes when callers need recovery behavior.

The backend returns `X-Trace-Id`; preserve it in diagnostic paths.

Examples:

- `backend/rabbit-platform/src/main/java/com/rabbit/app/common/ApiResponse.java`
- `backend/rabbit-platform/src/main/java/com/rabbit/app/common/GlobalExceptionHandler.java`
- `backend/rabbit-platform/src/main/java/com/rabbit/app/common/TraceIdFilter.java`

Current compatibility notes:

- `admin/src/types/api.ts` models `data` as always present although failures may omit it.
- Flutter currently accepts a missing `code` as success, while the admin client requires a numeric code.

Treat these as existing behavior during unrelated work. Change them only in a task that owns the compatibility and rollout decision.

## Idempotent writes

Bind `requestId` to the logical draft, not to an HTTP attempt.

- Generate the ID when the draft starts.
- Reuse it after a timeout, connection failure, or unchanged retry.
- Replace it when fingerprinted payload data changes or a completed write starts a new draft.
- A payload-hashed flow rejects the same ID with different payload data. `RequestDedupService.begin(..., payloadHash)` and outbound submission enforce this.
- Replay behavior is endpoint-specific. Outbound stores enough state to reconstruct the result, while some legacy flows load an existing record or return without repeating a void write.
- Keep IDs within the backend's 64-character limit. Use `RequestIdUtil` for derived child IDs.

Legacy `markProcessing` flows do not persist a payload hash, so they cannot detect every changed-payload retry. This is current behavior, not a contract to copy into new write paths.

Examples:

- `backend/rabbit-platform/src/main/java/com/rabbit/app/modules/dedup/service/RequestDedupService.java`
- `backend/rabbit-platform/src/main/java/com/rabbit/app/util/RequestIdUtil.java`
- `admin/src/lib/farm-request.ts`
- `app/lib/src/data/repositories/batches/repository.dart`

## Pagination

Use the endpoint's declared shape:

- Admin resources commonly use `{ items, total, page, pageSize }`.
- Reproduction tasks use `{ items, total, page, size }`.
- Some production lists return arrays and stop when a page is shorter than the requested size.
- Operation events use `{ items, nextCursor, hasMore }`. Treat `nextCursor` as opaque.

Do not add a generic pagination adapter that guesses field names.

Examples:

- `backend/rabbit-reporting/src/main/java/com/rabbit/app/modules/admin/dto/PageResult.java`
- `backend/rabbit-production/src/main/java/com/rabbit/app/modules/repro/dto/TaskPage.java`
- `backend/rabbit-production/src/main/java/com/rabbit/app/modules/operation/dto/OperationEventPage.java`

## Date and time

Rabbit business dates use `Asia/Shanghai`.

- Keep natural business dates as `yyyy-MM-dd`.
- Follow each endpoint's declared date-time representation, currently ISO instants or epoch milliseconds.
- Do not derive farm dates directly from the browser or device timezone.
- Interpret business wall-clock input as UTC+8 and convert returned instants back for display.
- Keep server-derived values such as `overdue` authoritative.

Examples:

- `backend/rabbit-platform/src/main/java/com/rabbit/app/util/DateUtil.java`
- `admin/src/lib/date.ts`
- `app/lib/src/domain/reproduction/date_policy.dart`

Some current call sites still depend on the machine timezone. `DashboardReportService` and `OutboundSubmitService` use the JVM default zone for parts of report and sale-date handling. `admin/src/components/workspace-outbound-dialog.tsx` constructs a timestamp in the browser zone, and `app/lib/src/ui/rabbits/widgets/vaccinations.dart` calls `toLocal()` for display. These are compatibility gaps, not preferred examples. When changing one, add an endpoint-specific regression test and move it to the shared farm-time helpers without changing the wire representation by accident.

## Contract review

When a backend DTO, enum, or response changes, compare it with both clients. Pay particular attention to authentication session fields, house permissions, pagination variants, reproduction actions and results, invitation behavior, and server-owned labels.

Run the relevant application checks plus backend E2E coverage for authentication, permissions, tenant isolation, idempotency, and API contract changes. The root `scripts/ci/check.sh` does not run the MySQL-backed E2E profile.
