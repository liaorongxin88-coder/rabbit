# Backend guidelines

Rabbit's backend is a Java 21 Spring Boot modular monolith. Maven modules separate shared infrastructure, access control, farming operations, reporting, and application wiring. Within a business module, requests normally flow from controllers through services to MyBatis mapper interfaces and XML. Flyway migrations and full-context tests live in `rabbit-boot`.

The source of truth is current code and enforcement tests. Start with `AGENTS.md`, `docs/backend/README.md`, and the architecture tests under `backend/rabbit-boot/src/test/java/com/rabbit/app/architecture/`. When prose and executable behavior disagree, preserve the executable contract and record the mismatch.

## Pre-Development Checklist

- Read [Modules](./modules.md) before choosing a Maven module or adding a dependency.
- Read [HTTP and services](./http-and-services.md) before changing a controller, service, transaction, DTO, or mapper call.
- Read [Security and tenancy](./security-and-tenancy.md) before changing authentication, permissions, `X-House-Id`, scoped SQL, or idempotent writes.
- Read [Data and migrations](./data-and-migrations.md) before changing mapper XML, entities, schema, or Flyway history.
- Read [Errors and observability](./errors-observability.md) before adding an error code, log, trace field, audit behavior, or tracked operation.
- Read [Testing](./testing.md) before placing tests or choosing a Maven command.
- Read [Batch statistics](./batch-statistics.md) before changing the versioned 28-metric contract, immutable batch snapshots, outbound draft authority, or XLSX export.
- For shared API contracts, also read [Rabbit cross-application contracts](../guides/rabbit-cross-application-contracts.md).

## File index

| File | Scope |
| --- | --- |
| [Modules](./modules.md) | Maven ownership, dependency direction, and package boundaries |
| [HTTP and services](./http-and-services.md) | Controller, service, validation, transaction, and mapper responsibilities |
| [Security and tenancy](./security-and-tenancy.md) | JWT domains, permission metadata, house isolation, and request deduplication |
| [Data and migrations](./data-and-migrations.md) | MyBatis XML, operation stamping, Flyway, and schema changes |
| [Errors and observability](./errors-observability.md) | API errors, trace IDs, audit logs, and operation tracking |
| [Testing](./testing.md) | Unit, architecture, integration, and build checks |
| [Batch statistics](./batch-statistics.md) | Versioned metrics, immutable snapshots, compatibility events, outbound authority, and XLSX export |

## Quality Check

Run from the repository root for backend code changes:

```bash
mvn --file backend/pom.xml test
mvn --file backend/pom.xml checkstyle:check
```

Use `mvn --file backend/pom.xml -pl <module> -am test` for a focused reactor run. Keep `-am` so Maven tests current sibling sources instead of stale locally installed artifacts. Use `bash scripts/e2e-local.sh -Dit.test=<TestName>IT` for a focused MySQL-backed workflow. The script supplies the guarded local database settings described in `docs/project/testing.md`.

Review module direction, `@RequiresPermission` coverage, tenant predicates, transaction boundaries, API envelopes, and consumer compatibility before merging. Package changes with `mvn --file backend/pom.xml -DskipTests package`; `rabbit-boot` is the only executable artifact.
