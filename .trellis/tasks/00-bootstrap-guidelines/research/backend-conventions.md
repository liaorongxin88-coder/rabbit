# Rabbit backend conventions

## Scope and evidence order

Rabbit's backend is a Java 21, Spring Boot 3.5 modular monolith built with Maven, MyBatis, MySQL, and Flyway. This note records current repository behavior. It does not propose a cleaner replacement architecture.

Use evidence in this order when sources disagree:

1. Architecture and placement tests that fail the build.
2. Current Java, mapper XML, Flyway, and Maven configuration.
3. Maintained backend and project documentation.
4. General repository guidance.

The root `AGENTS.md` is the nearest rule file for backend work. There is no backend-local `AGENTS.md` or equivalent rule file. Rules under `admin/` and `app/` belong to those applications and do not apply to Java code. The main documentation entry points are `docs/README.md`, `docs/backend/README.md`, and `docs/project/architecture.md`. Module-specific facts are also recorded in `docs/backend/modules/domain-modules.md` and `docs/backend/modules/data-and-migrations.md`.

When prose and implementation drift, follow the implementation. Route contracts come from controllers, schema history comes from Flyway, and test placement comes from the architecture tests.

## Maven modules and ownership

The reactor order in `backend/pom.xml` is also the dependency direction:

```text
rabbit-platform <- rabbit-access <- rabbit-production <- rabbit-reporting <- rabbit-boot
```

This line is shorthand, not a claim that each module depends only on its immediate predecessor. Production declares platform and access. Reporting declares all three lower modules. Boot declares all four library modules. Lower modules must not import a higher module, and no library module may import boot configuration. `backend/rabbit-boot/src/test/java/com/rabbit/app/architecture/FarmingModuleArchitectureTest.java` enforces those package boundaries. `docs/adr/0002-backend-maven-modules.md` explains the split.

### `rabbit-platform`

`rabbit-platform` owns low-level infrastructure shared by all business modules: API envelopes and exceptions, trace propagation, cache primitives, utilities, and persistent request deduplication. It must not depend on access, production, reporting, or boot.

Representative paths:

- `backend/rabbit-platform/pom.xml`
- `backend/rabbit-platform/src/main/java/com/rabbit/app/common/ApiResponse.java`
- `backend/rabbit-platform/src/main/java/com/rabbit/app/modules/dedup/service/RequestDedupService.java`

The name does not mean "persistence-free." Deduplication uses MyBatis, and cache implementations use Lettuce. Shared code belongs here only when its ownership is genuinely cross-module; platform is not a miscellaneous helper bucket.

### `rabbit-access`

`rabbit-access` owns authentication, users, rabbit houses, membership, workspace contracts and projections, authorization, security contexts, and operation-tracking annotations and aspects. Stable extension contracts that higher modules implement also live here. For example, access owns `HouseInitializer`, while production supplies `CageHouseInitializer`.

Representative paths:

- `backend/rabbit-access/src/main/java/com/rabbit/app/security/AccessControlService.java`
- `backend/rabbit-access/src/main/java/com/rabbit/app/modules/house/spi/HouseInitializer.java`
- `backend/rabbit-access/src/main/java/com/rabbit/app/tracking/TrackedOperation.java`

The workspace package has a narrower rule than the Maven module boundary. Concrete domains may use only `com.rabbit.app.modules.workspace.model` and `.spi`; they may not reach into workspace implementation packages. The same architecture test enforces this.

### `rabbit-production`

`rabbit-production` owns operational farming domains: rabbits, cages, batches, breeding and reproduction, events and reminders, outbound operations, feed, treatment, vaccination, weight, inventory, sales, settings, files, NFC, hardware, and app-update runtime behavior. Domain mapper XML stays in this module beside the Java mapper it implements.

Representative paths:

- `backend/rabbit-production/src/main/java/com/rabbit/app/modules/rabbit/service/RabbitService.java`
- `backend/rabbit-production/src/main/java/com/rabbit/app/modules/outbound/service/OutboundSubmitCoordinator.java`
- `backend/rabbit-production/src/main/resources/mapper/modules/sale/SaleOrderMapper.xml`

Cross-domain calls inside production are allowed when a workflow coordinates several operational aggregates. They still flow through services rather than importing another domain's mapper as the default integration mechanism.

### `rabbit-reporting`

`rabbit-reporting` owns reports, HTTP audit persistence and queries, platform-administration APIs, and administrative farm/account orchestration. It may read or orchestrate access and production services because it is above both modules.

Representative paths:

- `backend/rabbit-reporting/src/main/java/com/rabbit/app/modules/report/service/DashboardReportService.java`
- `backend/rabbit-reporting/src/main/java/com/rabbit/app/modules/admin/service/AdminFarmService.java`
- `backend/rabbit-reporting/src/main/java/com/rabbit/app/modules/audit/support/AuditLogInterceptor.java`

Platform administration is intentionally split. Access owns JWT parsing, contexts, roles, and general permission evaluation. Reporting owns platform-admin entities, endpoints, services, and its guard. Boot wires both sides together.

### `rabbit-boot`

`rabbit-boot` is the composition root and the only executable JAR. It owns the application entry point, cross-module Spring configuration, runtime YAML, all Flyway migrations and schema reference resources, architecture tests, and integration tests that require the full application context.

Representative paths:

- `backend/rabbit-boot/src/main/java/com/rabbit/app/RabbitBackendApplication.java`
- `backend/rabbit-boot/src/main/java/com/rabbit/app/config/WebConfig.java`
- `backend/rabbit-boot/src/main/resources/application.yml`

Do not move ordinary domain logic or module-local unit tests into boot for convenient classpath access. `backend/rabbit-boot/src/test/java/com/rabbit/app/architecture/BootTestPlacementTest.java` permits only an explained allowlist of all-module scans, boot configuration tests, and schema checks.

## HTTP, service, and persistence layers

The normal module-local dependency flow is:

```text
controller -> service -> mapper interface -> mapper XML -> MySQL
```

There is no separate repository abstraction over MyBatis.

Controllers unwrap HTTP input, read authentication or tenant context, declare permission requirements, and return response contracts. Services own semantic validation, orchestration, transactions, idempotency, and state transitions. Mapper interfaces define persistence operations; XML owns SQL, result maps, joins, tenant predicates, pagination, and projections.

Representative end-to-end paths:

- Access: `backend/rabbit-access/src/main/java/com/rabbit/app/modules/house/controller/HouseController.java`, `backend/rabbit-access/src/main/java/com/rabbit/app/modules/house/service/HouseService.java`, `backend/rabbit-access/src/main/resources/mapper/modules/house/RabbitHouseMapper.xml`
- Production: `backend/rabbit-production/src/main/java/com/rabbit/app/modules/sale/controller/SaleController.java`, `backend/rabbit-production/src/main/java/com/rabbit/app/modules/sale/service/SaleService.java`, `backend/rabbit-production/src/main/resources/mapper/modules/sale/SaleOrderMapper.xml`
- Reporting: `backend/rabbit-reporting/src/main/java/com/rabbit/app/modules/report/controller/ReportController.java`, `backend/rabbit-reporting/src/main/java/com/rabbit/app/modules/report/service/DashboardReportService.java`, `backend/rabbit-reporting/src/main/resources/mapper/modules/report/DashboardReportMapper.xml`

Constructor injection is the common service style and makes module-local tests cheap. Put `@Transactional` on service methods that coordinate multiple writes. Simple single-statement updates may remain nontransactional. Contended workflows use explicit row locks, duplicate-key recovery, or an isolation override where needed; examples are `HouseService`, `AdminFarmService`, and `RabbitHouseMapper.xml`.

Request DTOs use Jakarta Bean Validation for shape-level checks. Services still enforce business meaning, cross-field rules, and state-dependent validation. DTO/entity separation is partial rather than universal: report and admin endpoints usually return projection DTOs, while several operational endpoints return persistence entities directly. A spec must not claim that exposing entities is forbidden when current controllers such as `HouseController` and `SaleController` do it.

Known exception: `ReportController` injects mappers directly for several export queries and contains CSV paging and serialization. Treat this as existing debt, not a pattern for new controllers.

## API responses and errors

JSON endpoints normally return `ApiResponse<T>` from `backend/rabbit-platform/src/main/java/com/rabbit/app/common/ApiResponse.java`. Success uses `code = 0`, `message = "ok"`, and a data payload. Business failures use `BizException(code, message)` from the same common package.

`backend/rabbit-platform/src/main/java/com/rabbit/app/common/GlobalExceptionHandler.java` maps business exceptions, unreadable JSON, bean-validation errors, missing headers or parameters, type mismatches, and uncaught exceptions into the envelope. It logs unreadable bodies at warn and uncaught exceptions at error. The handlers return objects rather than `ResponseEntity`, so envelope errors normally keep HTTP status 200. Preserve that current client contract unless a coordinated API migration explicitly changes it.

Raw file and CSV responses are an intentional exception. For example, `backend/rabbit-reporting/src/main/java/com/rabbit/app/modules/report/controller/ReportController.java` returns `ResponseEntity<StreamingResponseBody>` for exports rather than wrapping bytes in `ApiResponse`.

The global handler does not have specific mappings for every infrastructure failure. Constraint violations outside MVC argument binding, persistence failures, upload-size errors, and asynchronous streaming failures may reach the generic 500 path. Do not describe those as first-class error contracts.

## Authentication, permissions, and tenant isolation

Spring Security is stateless, but `backend/rabbit-boot/src/main/java/com/rabbit/app/config/SecurityConfig.java` broadly permits requests at the framework authorization layer. Custom JWT filters and MVC interceptors carry the real authentication and authorization policy. Business JWTs and platform-admin JWTs are separate.

Every non-excluded controller route must declare `@RequiresPermission` on the method or class. `backend/rabbit-access/src/main/java/com/rabbit/app/security/AuthorizationInterceptor.java` fails closed with code 500 when a route lacks permission metadata. House-scoped permissions parse a positive `X-House-Id`, validate the active account, farm and membership, compare the role rank, and bind `HouseContext`. Build-time coverage is checked by `backend/rabbit-boot/src/test/java/com/rabbit/app/security/PermissionAnnotationCoverageTest.java`.

Tenant safety is defense in depth:

- Controllers and the authorization interceptor establish the authorized house.
- Services pass `houseId` through queries and writes.
- Mapper XML includes `house_id` in tenant-owned predicates and uniqueness assumptions.
- `backend/rabbit-access/src/main/java/com/rabbit/app/security/HouseSqlGuardInterceptor.java` rejects update/delete SQL without `WHERE`, rejects recognized `houseId` parameter maps whose SQL omits `house_id`, and caps affected rows.

The SQL guard is not proof that every query is tenant-safe. It inspects updates and deletes only, recognizes `houseId` only in MyBatis parameter maps, and has configured exceptions. Reads and entity-only writes still depend on careful mapper review. `backend/rabbit-production/src/main/resources/mapper/modules/sale/SaleOrderMapper.xml` and `backend/rabbit-reporting/src/main/resources/mapper/modules/report/DashboardReportMapper.xml` are useful examples of house-scoped and authorized-house-list queries.

Some controllers repeat login or house checks already performed by interceptors. `SaleController` is one example. Preserve behavior when editing those paths, but do not multiply redundant checks in new endpoints without a concrete reason.

## Idempotency and operation tracking

Write APIs use a client `requestId`. The platform deduplication key is `(house_id, user_id, api, request_id)`, with `PROCESSING`, `DONE`, and `FAILED` states. `RequestDedupService` claims work with insert-ignore semantics and rejects payload changes when a payload hash is present. Its mapper and migration evidence are:

- `backend/rabbit-platform/src/main/java/com/rabbit/app/modules/dedup/service/RequestDedupService.java`
- `backend/rabbit-platform/src/main/resources/mapper/modules/dedup/RequestDedupMapper.xml`
- `backend/rabbit-boot/src/main/resources/db/migration/V24__request_dedup_payload_hash.sql`

Newer operations use `@TrackedOperation` from access. The annotation binds operation context, may manage deduplication outside the business transaction, and may write a domain event inside it. Its `code` must match the historical manual API key exactly. Set `dedup = true` only when the service does not already own manual deduplication; double ownership makes the method collide with its own `PROCESSING` record.

Spring AOP self-invocation is an enforced anti-pattern. A tracked method must not be called from the same class, including through a self-delegating overload. `FarmingModuleArchitectureTest` and `TrackedOperationPlacementTest` both guard this failure mode. New `@Transactional` self-calls are also rejected unless reviewed, documented, and added to the explicit allowlist.

Legacy manual dedup remains in production services such as `SaleService`. Because its state changes run inside the business transaction, a rollback can also roll back `markFailed`. The code documents this limitation. Do not present the legacy arrangement as the target for new operations.

Outbound submission has a stronger specialized lifecycle with `REQUIRES_NEW`, payload hashes, stored final responses, and recovery from ambiguous processing state. See `backend/rabbit-production/src/main/java/com/rabbit/app/modules/outbound/service/OutboundSubmitCoordinator.java` and `OutboundRequestLifecycleService.java`.

## Logging, tracing, and audit

`backend/rabbit-platform/src/main/java/com/rabbit/app/common/TraceIdFilter.java` accepts or generates `X-Trace-Id`, stores it in MDC and a request attribute, echoes it on the response, and clears MDC in `finally`. Log statements should rely on this request trace rather than inventing a second correlation identifier.

`backend/rabbit-reporting/src/main/java/com/rabbit/app/modules/audit/support/AuditLogInterceptor.java` records trace, actor, house and resource coordinates, method, path, query string, transport status, API envelope code/message, duration, client IP, and user agent. `backend/rabbit-platform/src/main/java/com/rabbit/app/common/ApiResponseAuditAdvice.java` exposes envelope fields as request attributes for that interceptor. The attribute names form a cross-module string contract and have regression coverage in `backend/rabbit-platform/src/test/java/com/rabbit/app/common/ApiResponseAuditAdviceTest.java` and reporting audit tests.

Operation tracking is split on purpose: access owns contexts, annotations, stamping, and aspects; production owns operation event persistence and read behavior; reporting owns legacy HTTP audit logs. `docs/backend/modules/operation-tracking.md` is the main explanation.

Known exception: `backend/rabbit-reporting/src/main/java/com/rabbit/app/modules/audit/service/AuditLogService.java` intentionally swallows persistence exceptions so audit failure cannot break the user request. It also emits no failure log, so audit loss can be silent. Record this as existing behavior, not a general rule to suppress exceptions.

## MyBatis and SQL

Mapper interfaces live under each owning domain's `mapper` package. XML lives at `src/main/resources/mapper/modules/<domain>/` in the same Maven module. XML uses explicit result maps, named parameters, and house filters for tenant-owned data. Reporting mappers may project directly into read DTOs.

Representative paths:

- `backend/rabbit-access/src/main/java/com/rabbit/app/modules/house/mapper/RabbitHouseMapper.java`
- `backend/rabbit-access/src/main/resources/mapper/modules/house/RabbitHouseMapper.xml`
- `backend/rabbit-reporting/src/main/resources/mapper/modules/admin/AdminFarmMapper.xml`

Do not put SQL in services or create a second data-access abstraction without a repository-wide decision. Review update/delete statements for `WHERE`, tenant predicates, and expected row counts even though the runtime guard catches part of that surface. Use `SELECT ... FOR UPDATE` only inside a transaction that owns the resulting decision and writes.

`backend/rabbit-access/src/main/java/com/rabbit/app/tracking/OperationStampInterceptor.java` fills supported `createBy`, `updateBy`, `houseId`, and operator snapshot fields from operation context while preserving explicit values. New service code should not hand-stamp fields already covered by that interceptor.

## Flyway and schema changes

All migrations belong to `backend/rabbit-boot/src/main/resources/db/migration/`, regardless of which business module owns the affected table. Files use `V<number>__snake_case_description.sql`; current history runs through `V54__restore_prepartum_lead_days.sql` and contains historical gaps. Never renumber or edit an applied migration to make the sequence look tidy. Add the next version.

Flyway replay is authoritative. `backend/rabbit-boot/src/main/resources/db/schema.sql` is a current full-schema reference, and `db/seed_demo.sql` is demo data. Neither replaces migration verification. The ownership and operational rules are documented in `docs/backend/modules/data-and-migrations.md`.

Migration style reflects production upgrade needs:

- Additive changes often probe `information_schema` and execute conditional DDL so partially upgraded databases can converge. See `V25__rabbit_stages_and_breeding_cage_guard.sql`.
- Risky contract changes can use staged additive, backfill, and compatibility-removal migrations. `V26__doe_breeding_v2_additive.sql`, `V27__doe_breeding_v2_backfill.sql`, and `V28__doe_breeding_v2_drop_compat.sql` are the clearest sequence.
- Database constraints and generated columns enforce invariants that must survive alternate writer paths. `V25` and `V44__batch_scoped_open_cycle_uniqueness.sql` are representative.

A schema change should include the migration, any required mapper/entity updates, and migration or API regression coverage. Do not treat a manual database edit or a `schema.sql` change as deployment work.

## Tests and enforced rules

Unit tests use JUnit 5, manual Mockito mocks, constructor injection, and direct assertions. They do not start Spring for ordinary service logic. Test method names usually state behavior in sentence form, while comments explain business consequences when the invariant is not obvious. `backend/rabbit-production/src/test/java/com/rabbit/app/modules/sale/service/SaleServiceTest.java`, `backend/rabbit-reporting/src/test/java/com/rabbit/app/modules/admin/service/AdminFarmServiceTest.java`, and `backend/rabbit-access/src/test/java/com/rabbit/app/security/PermissionModelTest.java` are representative.

Name fast tests `*Test.java` and keep them in the module that owns the tested class. `rabbit-boot` keeps only its own configuration tests, schema-resource checks, and all-module scans on the `BootTestPlacementTest` allowlist. Adding an exception requires an explanation in that allowlist.

Name full-context or external-resource tests `*IT.java`. The Maven `e2e` profile skips Surefire and uses Failsafe for `**/*IT.java` with the `e2e` Spring profile. Integration tests live in boot because they need the complete application. Representative paths are:

- `backend/rabbit-boot/src/test/java/com/rabbit/app/e2e/E2eTestSupport.java`
- `backend/rabbit-boot/src/test/java/com/rabbit/app/e2e/E2eDatabaseReset.java`
- `backend/rabbit-boot/src/test/java/com/rabbit/app/e2e/ReproLifecycleIT.java`

Shared SQL fixtures live under `backend/src/test/resources/fixtures/` and are added to boot's test resources by `backend/rabbit-boot/pom.xml`. The E2E harness uses dedicated MySQL schemas and a reset mechanism designed to avoid rerunning all migrations before every test. Migration-specific ITs use the migration datasource separately.

The build enforces these structural contracts:

- Module dependency direction and workspace public-package access.
- No `@TrackedOperation` self-invocation or self-delegating annotated overload.
- No new unreviewed `@Transactional` self-invocation.
- Permission metadata coverage across controllers.
- Boot unit-test placement.

The enforcing paths are `FarmingModuleArchitectureTest.java`, `TrackedOperationPlacementTest.java`, `PermissionAnnotationCoverageTest.java`, and `BootTestPlacementTest.java` under `backend/rabbit-boot/src/test/java/com/rabbit/app/`.

## Build and validation commands

The parent POM requires JDK 21 and Maven 3.9 or newer. Checkstyle includes test sources and runs in Maven's `validate` phase using `backend/config/checkstyle/checkstyle.xml`. Java uses four-space indentation, `PascalCase` classes, and `camelCase` methods as stated in `AGENTS.md`.

Use these commands from the repository root unless noted:

```bash
# Full fast suite, including architecture tests and Checkstyle during validate
mvn --file backend/pom.xml test

# Focused module plus required upstream reactor modules; keep -am
mvn --file backend/pom.xml -pl rabbit-production -am test
mvn --file backend/pom.xml -pl rabbit-reporting -am test

# Explicit lint check
mvn --file backend/pom.xml checkstyle:check

# Preferred local E2E entry point with managed schema variables
bash scripts/e2e-local.sh
bash scripts/e2e-local.sh -Dit.test=ReproLifecycleIT

# Run the composed application
cd backend
mvn -pl rabbit-boot -am spring-boot:run

# Package the only executable artifact
mvn --file backend/pom.xml -DskipTests package
java -jar backend/rabbit-boot/target/rabbit-backend.jar
```

Do not omit `-am` from a focused reactor test. Without it, Maven may resolve stale sibling artifacts from the local repository. Although `AGENTS.md` lists `mvn --file backend/pom.xml -Pe2e verify`, `docs/project/testing.md` and `scripts/e2e-local.sh` explain why the raw command is unsafe with default local database URLs. The script is the normal local entry point. E2E MySQL must use the documented `Asia/Shanghai` timezone because date assertions compare application timestamps with database `now()`.

## Recommended backend spec files

The backend spec layer should stay small enough to scan before a change. Recommended files:

- `index.md`: scope, evidence precedence, pre-development checklist, and quality checklist.
- `modules.md`: ownership of platform, access, production, reporting, boot; dependency direction; workspace SPI boundary.
- `http-and-services.md`: controller/service/mapper responsibilities, response envelopes, validation, transactions, and known exceptions.
- `security-and-tenancy.md`: JWT separation, permission annotations, `X-House-Id`, tenant predicates, SQL guard limits, and idempotency.
- `data-and-migrations.md`: MyBatis XML placement, stamping, Flyway ownership and naming, staged migrations, and schema references.
- `errors-observability.md`: `BizException`, global mapping, trace IDs, API audit, operation tracking, and failure-handling exceptions.
- `testing.md`: module-local unit tests, boot allowlist, architecture rules, E2E profile, fixtures, and commands.

Before publishing those specs, validate:

```bash
# Every cited repository path exists
rg -o '`(backend|docs|scripts|AGENTS\.md)[^`]*`' .trellis/spec/backend

# No generated placeholders remain
rg -n 'To fill|TODO|TBD|your project|placeholder' .trellis/spec/backend

# Backend rules still pass their own enforcement
mvn --file backend/pom.xml test
mvn --file backend/pom.xml checkstyle:check

# Trellis discovers the intended package layers and its own tests pass when present
python3 ./.trellis/scripts/get_context.py --mode packages
python3 -m pytest .trellis/tests -q
```

For documentation-only bootstrap work, the product test commands are verification recommendations rather than mandatory execution. The minimum research check is path existence, no placeholders, synchronized index links, and a manual comparison against the four architecture tests.
