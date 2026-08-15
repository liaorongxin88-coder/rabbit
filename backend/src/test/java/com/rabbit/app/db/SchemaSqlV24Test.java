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
