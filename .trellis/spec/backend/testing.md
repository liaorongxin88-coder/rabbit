# Backend testing

Fast tests use JUnit 5, constructor injection, manual Mockito mocks, and direct assertions. Keep `*Test.java` beside the Maven module that owns the tested class. `backend/rabbit-production/src/test/java/com/rabbit/app/modules/sale/service/SaleServiceTest.java` and `backend/rabbit-access/src/test/java/com/rabbit/app/security/PermissionModelTest.java` are representative.

Do not start Spring for ordinary service logic. Full-context and external-resource tests use `*IT.java` and live in `rabbit-boot`, where the complete application is available. `backend/rabbit-boot/src/test/java/com/rabbit/app/e2e/E2eTestSupport.java` and `ReproLifecycleIT.java` show the integration pattern.

## Enforced structure

The boot architecture suite checks contracts that ordinary compilation cannot:

- `FarmingModuleArchitectureTest.java` checks module package direction, workspace public packages, tracked self-invocation, and new transactional self-invocation outside its documented exception list.
- `TrackedOperationPlacementTest.java` rejects `@TrackedOperation` on a method that only delegates to an overload and can therefore leave the real entry point untracked.
- `backend/rabbit-boot/src/test/java/com/rabbit/app/security/PermissionAnnotationCoverageTest.java` scans controller permission metadata.
- `BootTestPlacementTest.java` keeps ordinary module tests out of boot and uses a documented allowlist for legitimate scans and boot tests.

Adding an allowlist entry requires an explanation. Do not move a test into boot solely to gain classpath access.

## Integration tests

The Maven `e2e` profile skips Surefire and runs `**/*IT.java` through Failsafe with the `e2e` Spring profile. Shared SQL fixtures live under `backend/src/test/resources/fixtures/` and are attached by `backend/rabbit-boot/pom.xml`. The harness uses dedicated MySQL schemas and a reset mechanism; migration-specific tests use their migration datasource separately.

Use `bash scripts/e2e-local.sh` instead of a raw local `-Pe2e verify`. The script protects against unsafe default database URLs and supplies the documented environment. E2E MySQL uses `Asia/Shanghai` because date assertions compare application time with database `now()`.

### Payload-sensitive replay tests

When a test expects the same `requestId` to replay successfully, construct the request body once or capture every hash-bound value before the first submission. Do not call `now()`, `new Date()`, or another clock separately for each attempt when `occurredAt`, `nextRemindAt`, or that field's epoch value participates in the payload hash. Fast local calls can land in one millisecond and pass while CI produces distinct hashes and correctly returns 409.

A changed-payload conflict test must keep all unrelated fields fixed and change only the field named by the test. This proves the conflict comes from the intended input rather than a regenerated timestamp.

```java
// Wrong: each retry can carry a different hash-bound timestamp.
for (int attempt = 0; attempt < 2; attempt++) {
    post(body("occurredAt", now(), "requestId", requestId));
}

// Correct: both attempts are the same logical request.
long occurredAt = now();
Map<String, Object> body = body("occurredAt", occurredAt, "requestId", requestId);
post(body);
post(body);
```

## Commands

```bash
# Full fast suite and architecture rules
mvn --file backend/pom.xml test

# Focused module with current upstream sources
mvn --file backend/pom.xml -pl rabbit-production -am test

# Explicit style check
mvn --file backend/pom.xml checkstyle:check

# Full or focused MySQL-backed workflow
bash scripts/e2e-local.sh
bash scripts/e2e-local.sh -Dit.test=ReproLifecycleIT
```

The parent POM requires JDK 21 and Maven 3.9 or newer. Checkstyle includes test sources and runs during `validate` using `backend/config/checkstyle/checkstyle.xml`. Java uses four-space indentation, `PascalCase` classes, and `camelCase` methods as stated in `AGENTS.md`.
