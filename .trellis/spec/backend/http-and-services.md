# HTTP and services

The usual request path is:

```text
controller -> service -> mapper interface -> mapper XML -> MySQL
```

Rabbit does not add a repository abstraction over MyBatis. `HouseController` and `HouseService` under `backend/rabbit-access/src/main/java/com/rabbit/app/modules/house/` show the access pattern. `SaleController`, `SaleService`, and `SaleOrderMapper.xml` under `backend/rabbit-production/src/main/` show the production pattern.

## Controllers

Controllers unwrap HTTP input, declare permission requirements, read authenticated or house context, and return the wire contract. Request DTOs use Jakarta Bean Validation for shape constraints. Services still validate cross-field meaning, current state, and domain invariants.

JSON endpoints normally return `ApiResponse<T>` from `backend/rabbit-platform/src/main/java/com/rabbit/app/common/ApiResponse.java`. Raw downloads and CSV streams use `ResponseEntity`, as in `backend/rabbit-reporting/src/main/java/com/rabbit/app/modules/report/controller/ReportController.java`.

Do not put SQL or multi-aggregate business decisions in a controller. `ReportController` currently injects mappers for export queries and owns CSV paging and serialization. This is an existing exception, not an example for new endpoints.

## Services and transactions

Services own semantic validation, orchestration, state transitions, transaction boundaries, and idempotency. Constructor injection is the common style and allows unit tests to supply manual Mockito mocks.

Put `@Transactional` on service methods that coordinate multiple writes. A single mapper statement may remain nontransactional. A lock such as `SELECT ... FOR UPDATE` must run inside the transaction that uses the locked state to decide and write. `backend/rabbit-access/src/main/java/com/rabbit/app/modules/house/service/HouseService.java` and `backend/rabbit-reporting/src/main/java/com/rabbit/app/modules/admin/service/AdminFarmService.java` show contended workflows.

Spring proxy annotations do not apply on self-invocation. Do not call a new `@Transactional` or `@TrackedOperation` method from the same class. `FarmingModuleArchitectureTest.java` rejects tracked self-calls and allows transactional self-calls only through its documented exception list. `backend/rabbit-boot/src/test/java/com/rabbit/app/architecture/TrackedOperationPlacementTest.java` separately rejects `@TrackedOperation` on a method that only delegates to an overload.

## DTOs and mapper calls

Use projection DTOs where a report or administrative query has its own result shape. DTO/entity separation is not universal: current operational controllers sometimes return persistence entities directly, including `HouseController` and `SaleController`. Preserve those contracts during focused changes instead of performing an unrelated response migration.

Call another domain through its service when that domain owns validation or state changes. Mapper interfaces define persistence operations; their XML owns SQL, result maps, joins, tenant predicates, and pagination. Review [Data and migrations](./data-and-migrations.md) for mapper placement.

## Review checklist

- The controller declares permission metadata and does HTTP adaptation only.
- The service owns business meaning, idempotency, and coordinated writes.
- Transactions cover every write that must commit or roll back together.
- Mapper calls retain house scope and expected row-count checks.
- Response shapes remain compatible with admin and Flutter consumers.
