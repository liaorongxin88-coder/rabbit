package com.rabbit.app.db;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SchemaSqlV24Test {
    private static final String SCHEMA = loadSchema();

    @Test
    void schemaContainsBreedingCycleTerminalStructure() {
        String cycles = table("breeding_cycles");
        String batchRabbits = table("batch_rabbits");

        assertAll(
            () -> assertContains(
                cycles,
                "unique key uk_bc_batch_mother_cycle (house_id, batch_id, mother_rabbit_id, cycle_no)"
            ),
            () -> assertContains(
                cycles,
                "constraint fk_bc_house foreign key (house_id) references rabbit_houses (id)"
            ),
            () -> assertContains(
                cycles,
                "constraint fk_bc_batch foreign key (batch_id) references batches (id)"
            ),
            () -> assertContains(
                cycles,
                "constraint fk_bc_mother foreign key (mother_rabbit_id) references rabbits (id)"
            ),
            () -> assertContains(
                cycles,
                "constraint fk_bc_male foreign key (male_rabbit_id) references rabbits (id)"
            ),
            () -> assertFalse(cycles.contains("uk_bc_mother_cycle")),
            () -> assertFalse(cycles.contains("idx_bc_batch_mother")),
            () -> assertContains(batchRabbits, "latest_cycle_id bigint"),
            () -> assertContains(batchRabbits, "current_nursing_kits int not null default 0"),
            () -> assertContains(batchRabbits, "nursing_litter_count int not null default 0"),
            () -> assertContains(batchRabbits, "key idx_br_latest_cycle (latest_cycle_id)")
        );
    }

    @Test
    void schemaContainsCycleEventsAndRabbitLineage() {
        assertAll(
            () -> assertCycleReference("pregnancy_check_records", "idx_pcr_cycle"),
            () -> assertCycleReference("prepartum_records", "idx_ppr_cycle"),
            () -> assertCycleReference("parturition_records", "idx_pr_cycle"),
            () -> assertCycleReference("weaning_records", "idx_wr_cycle"),
            () -> assertContains(table("rabbits"), "father_id bigint"),
            () -> assertContains(table("rabbits"), "birth_batch_id bigint"),
            () -> assertContains(table("rabbits"), "birth_cycle_id bigint"),
            () -> assertContains(table("rabbits"), "key idx_rabbits_father (father_id)"),
            () -> assertContains(table("rabbits"), "key idx_rabbits_birth_cycle (birth_cycle_id)")
        );
    }

    @Test
    void weaningAllocationsRemainLogicalRelationsWithoutPhysicalForeignKeys() {
        String allocations = table("weaning_record_allocations");

        assertAll(
            () -> assertContains(
                allocations,
                "unique key uk_wra_record_cage (weaning_record_id, cage_id)"
            ),
            () -> assertContains(allocations, "key idx_wra_record (weaning_record_id)"),
            () -> assertContains(allocations, "key idx_wra_cage (cage_id)"),
            () -> assertFalse(allocations.contains("foreign key"))
        );
    }

    @Test
    void schemaContainsLargeFarmAndDedupTerminalStructure() {
        assertAll(
            () -> assertContains(table("outbound_requests"), "conflicts_json mediumtext"),
            () -> assertContains(
                table("rabbits"),
                "key idx_rabbits_house_birth_batch_id (house_id, birth_batch_id, id)"
            ),
            () -> assertContains(
                table("rabbit_abnormal_conditions"),
                "key idx_rac_house_rabbit_deal (house_id, rabbit_id, is_deal)"
            ),
            () -> assertContains(table("request_dedup"), "payload_hash varchar(64) null")
        );
    }

    @Test
    void schemaContainsRabbitStagesAndSingleBreedingCageGuard() {
        String rabbits = table("rabbits");

        assertAll(
            () -> assertContains(rabbits, "growth_stage varchar(20)"),
            () -> assertContains(rabbits, "reproductive_stage varchar(20)"),
            () -> assertContains(rabbits, "active_breeding_cage_id bigint generated always as"),
            () -> assertContains(
                rabbits,
                "unique key uk_rabbits_house_active_breeding_cage (house_id, active_breeding_cage_id)"
            ),
            () -> assertContains(
                rabbits,
                "key idx_rabbits_house_cage_active_type_id (house_id, cage_id, is_active, type, id)"
            )
        );
    }

    @Test
    void schemaContainsDoeBreedingV2AdditiveStructure() {
        String cycles = table("breeding_cycles");

        assertAll(
            // V28 tightened both: the legacy batch writer that inserted stage-less cycles is
            // gone, and V27 already backfilled every existing row.
            () -> assertContains(cycles, "stage varchar(20) not null"),
            () -> assertContains(cycles, "stage_entered_at datetime not null"),
            // Compat mirrors dropped by V28. Reminders live in work_tasks; the authoritative
            // state is stage + lifecycle + result, never the Chinese status string (one stage
            // could map to several legacy values, so it was never a reliable discriminator).
            () -> assertFalse(cycles.contains("status varchar(30) not null")),
            () -> assertFalse(cycles.contains("next_event_date datetime")),
            () -> assertFalse(cycles.contains("next_event_type varchar(30)")),
            () -> assertFalse(cycles.contains("overlap_days int")),
            () -> assertContains(cycles, "lifecycle varchar(10) not null default 'open'"),
            () -> assertContains(cycles, "result varchar(10)"),
            () -> assertContains(cycles, "mating_method varchar(10)"),
            () -> assertContains(cycles, "state_version bigint not null default 0"),
            () -> assertContains(cycles, "pipeline_guard bigint generated always as"),
            // batch_member_guard was dropped by V28 together with the key it was meant to feed.
            () -> assertFalse(cycles.contains("batch_member_guard varchar(64) generated")),
            // V27 enforces one in-flight pipeline cycle per doe.
            () -> assertContains(cycles, "unique key uk_bc_pipeline (house_id, pipeline_guard)"),
            // uk_bc_batch_member is deliberately NEVER created: batch_member_guard covers every
            // OPEN cycle including lactation, so enforcing it would block blood mating inside a
            // single batch -- exactly what pipeline_guard's AWAIT_WEANING exclusion exists to
            // allow. The generated column stays as a diagnostic handle only.
            // Matches the KEY, not the name: schema.sql documents in a comment why this key is
            // absent, and that explanation must not trip the guard it is explaining.
            () -> assertFalse(cycles.contains("unique key uk_bc_batch_member")),
            // Free-range does belong to no batch (business ruling 2026-08-16). Widening a
            // NOT NULL column is backward compatible, so it ships with the additive migration
            // rather than V27 -- the new write path cannot serve free-range does without it.
            () -> assertContains(cycles, "batch_id bigint,"),
            () -> assertFalse(cycles.contains("batch_id bigint not null")),
            () -> assertContains(table("batches"), "is_archived boolean not null default false"),
            () -> assertContains(table("global_setting"), "gestation_days int not null default 30"),
            // V29: a free-range doe has no batch, so her weaning record cannot carry one
            // either. The record belongs to the litter and cycle; batch_id is legacy
            // denormalisation. Leaving it NOT NULL made weaning crash for free-range does.
            () -> assertFalse(table("weaning_records").contains("batch_id bigint not null"))
        );
    }

    @Test
    void schemaContainsDoeBreedingV2Projections() {
        String rabbits = table("rabbits");

        assertAll(
            () -> assertContains(rabbits, "current_stage varchar(20)"),
            () -> assertContains(rabbits, "current_cycle_id bigint"),
            () -> assertContains(rabbits, "stage_entered_at datetime"),
            () -> assertContains(rabbits, "last_mating_date datetime"),
            () -> assertContains(
                rabbits,
                "key idx_rabbits_house_current_stage (house_id, current_stage, id)"
            )
        );
    }

    @Test
    void schemaContainsReproEventStoreAndTaskCenter() {
        assertAll(
            () -> assertContains(table("repro_events"), "unique key uk_re_request (house_id, request_id)"),
            () -> assertContains(table("repro_events"), "operator_id bigint"),
            () -> assertContains(table("repro_events"), "operator_name varchar(64) not null"),
            () -> assertContains(table("repro_events"), "payload json"),
            () -> assertContains(table("litters"), "unique key uk_lt_cycle (house_id, cycle_id)"),
            () -> assertContains(table("litters"), "current_nursing int not null default 0"),
            () -> assertContains(table("work_tasks"), "unique key uk_wt_dedup (house_id, dedup_key)"),
            () -> assertContains(
                table("work_tasks"),
                "key idx_wt_due (house_id, status, due_date, task_type)"
            ),
            () -> assertContains(table("work_tasks"), "key idx_wt_cage (house_id, cage_id, status)"),
            () -> assertContains(
                table("biz_attachments"),
                "unique key uk_ba_biz_file (house_id, biz_type, biz_id, file_id)"
            )
        );
    }

    private static void assertCycleReference(String tableName, String indexName) {
        String definition = table(tableName);
        assertContains(definition, "breeding_cycle_id bigint");
        assertContains(definition, "key " + indexName + " (breeding_cycle_id)");
    }

    private static void assertContains(String definition, String expected) {
        assertTrue(
            definition.contains(expected),
            () -> "Expected schema fragment: " + expected
        );
    }

    private static String table(String tableName) {
        Pattern pattern = Pattern.compile(
            "(?is)create table if not exists\\s+" + Pattern.quote(tableName)
                + "\\s*\\((.*?)\\)\\s*engine=innodb"
        );
        Matcher matcher = pattern.matcher(SCHEMA);
        assertTrue(matcher.find(), () -> "Missing table in schema.sql: " + tableName);
        return normalize(matcher.group(1));
    }

    private static String normalize(String sql) {
        return sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String loadSchema() {
        var classLoader = SchemaSqlV24Test.class.getClassLoader();
        try (var input = classLoader.getResourceAsStream("db/schema.sql")) {
            assertNotNull(input, "db/schema.sql must be available on the test classpath");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read db/schema.sql", exception);
        }
    }
}
