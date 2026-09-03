# Security and tenancy

Rabbit uses separate JWT domains for business users and platform administrators. `backend/rabbit-access/src/main/java/com/rabbit/app/security/JwtAuthenticationFilter.java` handles business JWTs; `PlatformAdminAuthenticationFilter.java` handles platform JWTs. `/api/admin/**` does not use `X-House-Id`, while house-scoped business APIs do.

`backend/rabbit-boot/src/main/java/com/rabbit/app/config/SecurityConfig.java` broadly permits framework requests. Custom filters and MVC interceptors carry the effective authentication and authorization policy, so do not infer that an endpoint is public from the Spring Security matcher alone.

## Permission boundary

Every non-excluded controller route must declare `@RequiresPermission` on its method or class. `backend/rabbit-access/src/main/java/com/rabbit/app/security/AuthorizationInterceptor.java` fails closed when permission metadata is missing. `backend/rabbit-boot/src/test/java/com/rabbit/app/security/PermissionAnnotationCoverageTest.java` scans controller coverage.

House-scoped authorization validates a positive `X-House-Id`, then checks account, farm, membership, role rank, and permission before binding `HouseContext`. Client-side visibility never replaces this backend check. Use `permissions` as the capability contract; do not derive authorization from display roles.

## SQL isolation

Pass `houseId` through services and include `house_id` in tenant-owned reads, writes, joins, and uniqueness assumptions. `backend/rabbit-production/src/main/resources/mapper/modules/sale/SaleOrderMapper.xml` is a house-scoped example. Reporting across allowed farms uses an authorized house list, as shown in `backend/rabbit-reporting/src/main/resources/mapper/modules/report/DashboardReportMapper.xml`.

Two MyBatis interceptors provide partial runtime checks:

- `backend/rabbit-access/src/main/java/com/rabbit/app/security/HouseSqlGuardInterceptor.java` rejects update/delete SQL without `WHERE`, checks recognized `houseId` parameter maps for a `house_id` predicate, and caps affected rows.
- `backend/rabbit-access/src/main/java/com/rabbit/app/security/HouseSelectGuardInterceptor.java` applies the same recognized-`houseId` predicate check to selects when `app.mybatis.select-guard.enabled` is true.

Both guards inspect normalized SQL text only when MyBatis exposes `houseId` in a parameter map. They do not prove that the predicate is semantically correct, do not cover entity-only parameters or queries without a recognized `houseId`, allow primary-key access to `rabbit_houses`, and support configured exceptions. Treat them as defense in depth, not proof of tenant safety.

Do not trust a path, body, or stored client house ID as authorization. Path and body IDs must agree with the authorized scope. A child table without `house_id` must join through its house-owned parent, as described in `docs/backend/modules/direct-house-access.md`.

## Idempotent writes

Retryable business writes commonly accept a client `requestId`. `RequestDedupService` owns the platform key `(house_id, user_id, api, request_id)` and the `PROCESSING`, `DONE`, and `FAILED` lifecycle. Reuse a request ID for an unchanged retry. Calls to `begin(..., payloadHash)` reject a changed payload under the same ID; legacy `markProcessing` flows do not store a payload hash and cannot make that guarantee. See `backend/rabbit-platform/src/main/resources/mapper/modules/dedup/RequestDedupMapper.xml` and migration `backend/rabbit-boot/src/main/resources/db/migration/V24__request_dedup_payload_hash.sql`.

New tracked operations may set `dedup = true` on `@TrackedOperation` only when the service does not already run manual deduplication. The annotation `code` must match the historical API key. Combining aspect and manual ownership can make a request collide with its own `PROCESSING` record.

Legacy manual dedup remains in `backend/rabbit-production/src/main/java/com/rabbit/app/modules/sale/service/SaleService.java`. Its failure mark can roll back with the business transaction. Keep this as a documented exception. New outbound flows use the stronger `REQUIRES_NEW` lifecycle in `OutboundSubmitCoordinator` and `OutboundRequestLifecycleService`.
