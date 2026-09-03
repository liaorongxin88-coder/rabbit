# Backend modules

The module POM dependencies and `backend/rabbit-boot/src/test/java/com/rabbit/app/architecture/FarmingModuleArchitectureTest.java` establish the allowed dependency direction. The parent reactor lists the modules in the same order:

```text
rabbit-platform <- rabbit-access <- rabbit-production <- rabbit-reporting <- rabbit-boot
```

This is a direction, not a requirement that each module depend only on its immediate predecessor. `backend/rabbit-boot/src/test/java/com/rabbit/app/architecture/FarmingModuleArchitectureTest.java` rejects imports from lower modules into higher modules and imports from library modules into boot configuration.

## Ownership

- `rabbit-platform` owns shared API envelopes, exceptions, tracing, cache primitives, utilities, and persistent request deduplication. See `backend/rabbit-platform/src/main/java/com/rabbit/app/common/ApiResponse.java` and `backend/rabbit-platform/src/main/java/com/rabbit/app/modules/dedup/service/RequestDedupService.java`. It may use persistence and Redis; its name does not imply a persistence-free module.
- `rabbit-access` owns authentication, users, houses, membership, workspace contracts, authorization, security contexts, and operation-tracking aspects. Higher modules implement stable extension contracts such as `backend/rabbit-access/src/main/java/com/rabbit/app/modules/house/spi/HouseInitializer.java`.
- `rabbit-production` owns farming operations: rabbits, cages, batches, reproduction, events and reminders, outbound workflows, feed, treatment, vaccination, weight, inventory, sales, settings, files, NFC, hardware adapters, and app updates. `backend/rabbit-production/src/main/java/com/rabbit/app/modules/outbound/service/OutboundSubmitCoordinator.java` is a representative cross-domain workflow.
- `rabbit-reporting` owns reports, HTTP audit persistence, platform administration entities, endpoints, services, the `backend/rabbit-reporting/src/main/java/com/rabbit/app/modules/admin/security/PlatformAdminGuardInterceptor.java` MVC guard, and administrative orchestration. Access still owns platform JWT parsing, `PlatformAdminContext`, roles, and general permission evaluation; boot wires both sides. Reporting may read and coordinate access and production services because it sits above both modules. See `backend/rabbit-reporting/src/main/java/com/rabbit/app/modules/admin/service/AdminFarmService.java`.
- `rabbit-boot` is the composition root and only executable JAR. It owns startup, cross-module configuration, runtime YAML, Flyway migrations, schema references, architecture tests, and full-context integration tests. See `backend/rabbit-boot/src/main/java/com/rabbit/app/RabbitBackendApplication.java`.

Keep shared code with a real cross-module contract. Do not turn `rabbit-platform` into a miscellaneous helper directory. Keep ordinary domain logic and module-local unit tests out of `rabbit-boot`; `backend/rabbit-boot/src/test/java/com/rabbit/app/architecture/BootTestPlacementTest.java` permits only documented all-module scans, boot configuration tests, and schema checks.

## Domain packages

Within a Maven module, group controllers, services, models, mappers, and DTOs under the owning business domain. Mapper XML stays in the same Maven module at `src/main/resources/mapper/modules/<domain>/`.

Concrete domains may import only `com.rabbit.app.modules.workspace.model` and `com.rabbit.app.modules.workspace.spi` from the workspace package. They must not depend on workspace implementation packages. `FarmingModuleArchitectureTest` enforces this narrower boundary.

Cross-domain production workflows may call another domain's service. A direct import of another domain's mapper is not the default integration mechanism because it bypasses semantic validation and transaction ownership.

## Review examples

Preferred ownership:

```text
access owns HouseInitializer
production owns CageHouseInitializer
boot wires the implementation
```

Avoid placing a production mapper or business rule in boot merely because boot can see every module. Also avoid moving a one-domain utility into platform before a second module shares the same contract.
