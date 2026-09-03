# Data and migrations

Rabbit uses MyBatis mapper interfaces and XML. Keep the interface under the owning domain's `mapper` package and its XML in the same Maven module under `src/main/resources/mapper/modules/<domain>/`. `backend/rabbit-access/src/main/java/com/rabbit/app/modules/house/mapper/RabbitHouseMapper.java` and the matching XML are a representative pair.

## SQL rules

- Use named parameters and `house_id` predicates for tenant-owned data. XML commonly uses explicit `resultMap` definitions for entities and complex projections; scalar and some DTO queries use `resultType`. `backend/rabbit-reporting/src/main/resources/mapper/modules/admin/AdminFarmMapper.xml` demonstrates both forms.
- Keep SQL out of services. Do not introduce a second persistence abstraction unless a repository-wide design owns the change.
- Review selects as well as updates and deletes for house scope. `HouseSelectGuardInterceptor` and `HouseSqlGuardInterceptor` check recognized `houseId` parameter maps, but neither proves semantic isolation. Also review every update and delete for `WHERE` and expected affected rows.
- Use `SELECT ... FOR UPDATE` only inside the transaction that consumes the locked decision.
- Let `backend/rabbit-access/src/main/java/com/rabbit/app/tracking/OperationStampInterceptor.java` fill supported creator, updater, house, and operator snapshot fields. It preserves explicit values, so service code should not duplicate covered stamping.

Avoid an unscoped statement such as:

```sql
UPDATE rabbit SET status = #{status}
```

A tenant-owned write needs an identifying `WHERE` clause and its house predicate or a join through a house-owned parent.

## Flyway ownership

All deployed schema history lives in `backend/rabbit-boot/src/main/resources/db/migration/`, regardless of the table's business module. Files use `V<number>__snake_case_description.sql`. History contains gaps, and applied migrations are immutable. Add the next version; do not renumber or edit old files to tidy the sequence.

`backend/rabbit-boot/src/main/resources/db/schema.sql` is a full-schema reference and `backend/rabbit-boot/src/main/resources/db/seed_demo.sql` is demo data. Neither replaces a Flyway migration.

Production-safe migrations are often conditional or staged:

- `V25__rabbit_stages_and_breeding_cage_guard.sql` probes metadata and adds invariants that must survive alternate writers.
- `V26__doe_breeding_v2_additive.sql`, `V27__doe_breeding_v2_backfill.sql`, and `V28__doe_breeding_v2_drop_compat.sql` separate additive change, data backfill, and compatibility removal.
- `V44__batch_scoped_open_cycle_uniqueness.sql` uses database constraints for a scoped uniqueness rule.

A schema task includes the migration, affected mapper/entity changes, and regression coverage. Manual database edits or a standalone `schema.sql` update are not deployment work. Follow `docs/backend/modules/data-and-migrations.md` for replay and operational checks.
