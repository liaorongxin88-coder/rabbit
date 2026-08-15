package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * One production-sized house and one batch, from breeding stock through
 * production-origin commodity outbound and final batch closure.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=${E2E_LARGE_LOOP_DATASOURCE_URL:jdbc:mysql://localhost:3306/rabbit_app_e2e_large_loop?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true}"
})
public class LargeWholeHouseBatchLifecycleIT extends E2eTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(LargeWholeHouseBatchLifecycleIT.class);

    private static final int MOTHER_COUNT = 1_000;
    private static final int MALE_COUNT = 20;
    private static final int EMPTY_COUNT = 100;
    private static final int FAILED_PARTURITION_COUNT = 20;
    private static final int SUCCESSFUL_MOTHER_COUNT = MOTHER_COUNT - EMPTY_COUNT - FAILED_PARTURITION_COUNT;
    private static final int OVERLAP_REMATING_COUNT = 100;
    private static final int KITS_PER_SUCCESSFUL_LITTER = 7;
    private static final int COMMODITY_RABBIT_COUNT = SUCCESSFUL_MOTHER_COUNT * KITS_PER_SUCCESSFUL_LITTER;
    private static final int TOTAL_RECORDED_KITS = COMMODITY_RABBIT_COUNT;
    private static final int ACTIVE_SEED_COUNT = MOTHER_COUNT - FAILED_PARTURITION_COUNT + MALE_COUNT;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void oneThousandMotherSingleHouseBatchClosesFromMatingThroughWholeHouseOutbound(
            TestReporter reporter
    ) {
        Map<String, Long> timings = new LinkedHashMap<>();
        long testStartedAt = System.nanoTime();

        UserSession owner = register("large_whole_loop");
        long fixtureStartedAt = System.nanoTime();
        long houseId = createHouse(owner, "large_whole_loop_house", 20, 95, 1);
        LargeBreedingStock stock = insertBreedingStock(owner, houseId);
        List<Long> commodityCages = jdbc.queryForList(
                "select id from cages where house_id = ? and rabbit_count = 0 order by id",
                Long.class,
                houseId
        );
        Assertions.assertEquals(SUCCESSFUL_MOTHER_COUNT, commodityCages.size());
        recordTiming(timings, "fixtureMs", fixtureStartedAt);

        api.putOk("/api/settings", owner.token, null, obj(
                "aphrodisiacDays", 0,
                "palpationDays", 0,
                "prepartumDays", 0,
                "weaningDays", 0,
                "postpartumDays", 0,
                "saleDays", 0,
                "replacementDays", 150,
                "remark", "one-thousand-mother whole batch closed loop",
                "requestId", requestId("large_loop_settings")
        ));

        long preparationStartedAt = System.nanoTime();
        JsonNode batch = api.postOk("/api/batches", owner.token, houseId, obj(
                "batchCode", "LARGE-LOOP-" + requestId("batch").substring(0, 12),
                "femaleRabbitIds", stock.motherIds,
                "remark", "all breeding does in one production house",
                "requestId", requestId("large_loop_batch")
        ));
        long batchId = batch.get("id").asLong();
        Assertions.assertEquals("计划中", batch.get("status").asText());
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/start", owner.token, houseId, obj(
                "rabbitIds", stock.motherIds,
                "requestId", requestId("large_loop_aphrodisiac_start")
        ));
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/finish", owner.token, houseId, obj(
                "rabbitIds", stock.motherIds,
                "requestId", requestId("large_loop_aphrodisiac_finish")
        ));
        recordTiming(timings, "batchPreparationMs", preparationStartedAt);

        long commonActionTime = oneMinuteAgo();
        long matingStartedAt = System.nanoTime();
        JsonNode bulkMating = api.postOk(
                "/api/batches/" + batchId + "/mating/bulk",
                owner.token,
                houseId,
                obj(
                        "femaleRabbitIds", stock.motherIds,
                        "maleRabbitId", stock.maleIds.get(0),
                        "matingDate", commonActionTime,
                        "requestId", requestId("large_loop_first_mating_bulk")
                )
        );
        Assertions.assertEquals(MOTHER_COUNT, bulkMating.get("count").asInt());
        recordTiming(timings, "firstMatingMs", matingStartedAt);

        long pregnancyStartedAt = System.nanoTime();
        for (int index = 0; index < MOTHER_COUNT; index++) {
            pregnancyCheck(
                    owner,
                    houseId,
                    batchId,
                    stock.motherIds.get(index),
                    index < EMPTY_COUNT ? "空怀" : "怀孕",
                    commonActionTime,
                    "large_loop_first_pregnancy_" + index
            );
        }
        recordTiming(timings, "pregnancyCheckMs", pregnancyStartedAt);

        List<Long> failedMothers = stock.motherIds.subList(
                EMPTY_COUNT,
                EMPTY_COUNT + FAILED_PARTURITION_COUNT
        );
        List<Long> successfulMothers = stock.motherIds.subList(
                EMPTY_COUNT + FAILED_PARTURITION_COUNT,
                MOTHER_COUNT
        );

        long prepartumStartedAt = System.nanoTime();
        for (int index = EMPTY_COUNT; index < MOTHER_COUNT; index++) {
            prepartum(
                    owner,
                    houseId,
                    batchId,
                    stock.motherIds.get(index),
                    commonActionTime,
                    "large_loop_prepartum_" + index
            );
        }
        recordTiming(timings, "prepartumMs", prepartumStartedAt);

        long parturitionStartedAt = System.nanoTime();
        for (int index = 0; index < failedMothers.size(); index++) {
            parturition(
                    owner,
                    houseId,
                    batchId,
                    failedMothers.get(index),
                    0,
                    0,
                    true,
                    commonActionTime,
                    "large_loop_failed_parturition_" + index
            );
        }
        for (int index = 0; index < successfulMothers.size(); index++) {
            parturition(
                    owner,
                    houseId,
                    batchId,
                    successfulMothers.get(index),
                    KITS_PER_SUCCESSFUL_LITTER,
                    KITS_PER_SUCCESSFUL_LITTER,
                    false,
                    commonActionTime,
                    "large_loop_successful_parturition_" + index
            );
        }
        recordTiming(timings, "parturitionMs", parturitionStartedAt);

        Map<Long, Long> firstCycleIds = jdbc.query(
                "select mother_rabbit_id, id from breeding_cycles where house_id = ? and batch_id = ? and cycle_no = 1",
                resultSet -> {
                    Map<Long, Long> result = new LinkedHashMap<>();
                    while (resultSet.next()) {
                        result.put(resultSet.getLong("mother_rabbit_id"), resultSet.getLong("id"));
                    }
                    return result;
                },
                houseId,
                batchId
        );
        Assertions.assertEquals(MOTHER_COUNT, firstCycleIds.size());

        List<Long> overlapMothers = successfulMothers.subList(0, OVERLAP_REMATING_COUNT);
        long overlapStartedAt = System.nanoTime();
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/start", owner.token, houseId, obj(
                "rabbitIds", overlapMothers,
                "requestId", requestId("large_loop_overlap_aphrodisiac_start")
        ));
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/finish", owner.token, houseId, obj(
                "rabbitIds", overlapMothers,
                "requestId", requestId("large_loop_overlap_aphrodisiac_finish")
        ));
        for (int index = 0; index < overlapMothers.size(); index++) {
            long motherId = overlapMothers.get(index);
            mate(
                    owner,
                    houseId,
                    batchId,
                    motherId,
                    stock.maleIds.get((index + 1) % MALE_COUNT),
                    commonActionTime,
                    "large_loop_overlap_mating_" + index
            );
            pregnancyCheck(
                    owner,
                    houseId,
                    batchId,
                    motherId,
                    "怀孕",
                    commonActionTime,
                    "large_loop_overlap_pregnancy_" + index
            );
        }
        recordTiming(timings, "overlapRematingMs", overlapStartedAt);

        Assertions.assertEquals(OVERLAP_REMATING_COUNT, count(
                "select count(*) from ("
                        + "select mother_rabbit_id from breeding_cycles where house_id = ? and batch_id = ? "
                        + "and closed_at is null group by mother_rabbit_id having count(*) = 2"
                        + ") overlapping_mothers",
                houseId,
                batchId
        ));
        Assertions.assertEquals(OVERLAP_REMATING_COUNT, count(
                "select count(*) from breeding_cycles where house_id = ? and batch_id = ? and cycle_no = 2 "
                        + "and status = '怀孕确认' and overlap_litter_cycle_no = 1 and postpartum_remating_days is not null",
                houseId,
                batchId
        ));

        long weaningStartedAt = System.nanoTime();
        for (int index = 0; index < successfulMothers.size(); index++) {
            long motherId = successfulMothers.get(index);
            wean(
                    owner,
                    houseId,
                    batchId,
                    motherId,
                    firstCycleIds.get(motherId),
                    commodityCages.get(index),
                    commonActionTime,
                    "large_loop_weaning_" + index
            );
        }
        recordTiming(timings, "weaningMs", weaningStartedAt);

        assertProductionFactsBeforeOutbound(owner, houseId, batchId, stock, successfulMothers);

        long outboundDraftStartedAt = System.nanoTime();
        JsonNode task = api.postOk("/api/outbound/tasks", owner.token, houseId, obj(
                "entryType", "HOUSE",
                "resumeExisting", true
        ));
        Assertions.assertEquals(COMMODITY_RABBIT_COUNT, task.get("summary").get("normal").asInt());
        Assertions.assertEquals(ACTIVE_SEED_COUNT, task.get("summary").get("blocked").asInt());
        Assertions.assertEquals(0, task.get("summary").get("earlySale").asInt());
        Assertions.assertEquals(0, task.get("summary").get("needsAction").asInt());
        Assertions.assertEquals(COMMODITY_RABBIT_COUNT, task.get("selectedItems").size());

        List<Map<String, Object>> selectedItems = new ArrayList<>(COMMODITY_RABBIT_COUNT);
        List<Long> outboundRabbitIds = new ArrayList<>(COMMODITY_RABBIT_COUNT);
        Map<String, Long> stateVersions = new LinkedHashMap<>(COMMODITY_RABBIT_COUNT);
        for (JsonNode item : task.get("selectedItems")) {
            long rabbitId = item.get("rabbitId").asLong();
            long stateVersion = item.get("stateVersion").asLong();
            selectedItems.add(obj(
                    "rabbitId", rabbitId,
                    "stateVersion", stateVersion,
                    "selectionType", "NORMAL"
            ));
            outboundRabbitIds.add(rabbitId);
            stateVersions.put(String.valueOf(rabbitId), stateVersion);
        }
        double totalWeight = COMMODITY_RABBIT_COUNT * 2.2D;
        JsonNode frozen = api.putOk(
                "/api/outbound/tasks/" + task.get("taskId").asText(),
                owner.token,
                houseId,
                obj(
                        "revision", task.get("revision").asLong(),
                        "status", "WAITING_CONFIRMATION",
                        "items", selectedItems,
                        "saleTime", commonActionTime,
                        "totalWeight", totalWeight,
                        "unitPrice", 18.5,
                        "customer", "large whole-house buyer",
                        "remark", "all production-origin commodity rabbits"
                )
        );
        Assertions.assertEquals("WAITING_CONFIRMATION", frozen.get("status").asText());
        Assertions.assertEquals(COMMODITY_RABBIT_COUNT, frozen.get("selectedItems").size());
        recordTiming(timings, "outboundDraftMs", outboundDraftStartedAt);

        long outboundSubmitStartedAt = System.nanoTime();
        JsonNode outbound = api.postOk(
                "/api/outbound/tasks/" + task.get("taskId").asText() + "/submit",
                owner.token,
                houseId,
                obj(
                        "rabbitIds", outboundRabbitIds,
                        "stateVersions", stateVersions,
                        "saleTime", commonActionTime,
                        "totalWeight", totalWeight,
                        "unitPrice", 18.5,
                        "customer", "large whole-house buyer",
                        "remark", "single-house single-batch final outbound",
                        "requestId", UUID.randomUUID().toString()
                )
        );
        recordTiming(timings, "outboundSubmitMs", outboundSubmitStartedAt);
        Assertions.assertEquals("COMPLETED", outbound.get("status").asText());
        Assertions.assertEquals(COMMODITY_RABBIT_COUNT, outbound.get("rabbitCount").asInt());
        long saleOrderId = outbound.get("saleOrderId").asLong();

        Assertions.assertEquals(COMMODITY_RABBIT_COUNT, count(
                "select count(*) from sale_order_items where sale_order_id = ?",
                saleOrderId
        ));
        Assertions.assertEquals(COMMODITY_RABBIT_COUNT, count(
                "select count(*) from sale_order_items where sale_order_id = ? and batch_id_snapshot = ? "
                        + "and state_version_snapshot = 0",
                saleOrderId,
                batchId
        ));
        Assertions.assertEquals(0, count(
                "select count(*) from batch_rabbits where batch_id = ? and batch_role = 'fattening' and is_active = true",
                batchId
        ));
        Assertions.assertEquals("进行中", jdbc.queryForObject(
                "select status from batches where id = ? and house_id = ?",
                String.class,
                batchId,
                houseId
        ));

        long motherExitStartedAt = System.nanoTime();
        for (int index = 0; index < stock.motherIds.size(); index++) {
            // A failed parturition already records and performs the doe's departure.
            if (index >= EMPTY_COUNT && index < EMPTY_COUNT + FAILED_PARTURITION_COUNT) {
                continue;
            }
            cull(
                    owner,
                    houseId,
                    stock.motherIds.get(index),
                    commonActionTime,
                    "large_loop_mother_exit_" + index
            );
        }
        recordTiming(timings, "motherExitMs", motherExitStartedAt);

        long reconciliationStartedAt = System.nanoTime();
        assertClosedLoopAccounting(owner, houseId, batchId, stock, saleOrderId);
        recordTiming(timings, "reconciliationMs", reconciliationStartedAt);
        timings.put("totalMs", elapsedMillis(testStartedAt));

        assertStageWithin(timings, "fixtureMs", 30_000);
        assertStageWithin(timings, "batchPreparationMs", 60_000);
        assertStageWithin(timings, "firstMatingMs", 180_000);
        assertStageWithin(timings, "pregnancyCheckMs", 180_000);
        assertStageWithin(timings, "prepartumMs", 180_000);
        assertStageWithin(timings, "parturitionMs", 240_000);
        assertStageWithin(timings, "overlapRematingMs", 120_000);
        assertStageWithin(timings, "weaningMs", 600_000);
        assertStageWithin(timings, "outboundDraftMs", 300_000);
        assertStageWithin(timings, "outboundSubmitMs", 600_000);
        assertStageWithin(timings, "motherExitMs", 300_000);
        assertStageWithin(timings, "reconciliationMs", 120_000);
        assertStageWithin(timings, "totalMs", 1_800_000);

        Map<String, String> reportEntries = new LinkedHashMap<>();
        reportEntries.put("mothers", String.valueOf(MOTHER_COUNT));
        reportEntries.put("males", String.valueOf(MALE_COUNT));
        reportEntries.put("emptyMothers", String.valueOf(EMPTY_COUNT));
        reportEntries.put("failedParturitions", String.valueOf(FAILED_PARTURITION_COUNT));
        reportEntries.put("overlapRematings", String.valueOf(OVERLAP_REMATING_COUNT));
        reportEntries.put("commodityRabbits", String.valueOf(COMMODITY_RABBIT_COUNT));
        timings.forEach((key, value) -> reportEntries.put(key, String.valueOf(value)));
        reporter.publishEntry(reportEntries);
        LOGGER.info("Large whole-house batch closed-loop metrics: {}", reportEntries);
    }

    private LargeBreedingStock insertBreedingStock(UserSession owner, long houseId) {
        List<Long> allCageIds = jdbc.queryForList(
                "select id from cages where house_id = ? order by id",
                Long.class,
                houseId
        );
        Assertions.assertEquals(MOTHER_COUNT + MALE_COUNT + SUCCESSFUL_MOTHER_COUNT, allCageIds.size());

        List<Object[]> rabbitRows = new ArrayList<>(MOTHER_COUNT + MALE_COUNT);
        for (int index = 0; index < MOTHER_COUNT; index++) {
            rabbitRows.add(new Object[]{
                    houseId,
                    allCageIds.get(index),
                    "0",
                    "large-loop-doe",
                    "large-loop-mother-" + String.format("%04d", index),
                    String.valueOf(owner.userId),
                    String.valueOf(owner.userId)
            });
        }
        for (int index = 0; index < MALE_COUNT; index++) {
            rabbitRows.add(new Object[]{
                    houseId,
                    allCageIds.get(MOTHER_COUNT + index),
                    "1",
                    "large-loop-buck",
                    "large-loop-male-" + String.format("%02d", index),
                    String.valueOf(owner.userId),
                    String.valueOf(owner.userId)
            });
        }
        jdbc.batchUpdate(
                "insert into rabbits (house_id, cage_id, type, gender, breed, arrival_method, arrival_date, "
                        + "weight, state_version, is_active, is_quarantined, request_id, create_by, update_by) "
                        + "values (?, ?, '0', ?, ?, '1', now(), 3.2, 0, true, false, ?, ?, ?)",
                rabbitRows
        );
        jdbc.update(
                "update cages c join rabbits r on r.cage_id = c.id and r.house_id = c.house_id "
                        + "set c.status = '1', c.rabbit_count = 1 where c.house_id = ?",
                houseId
        );

        List<Long> motherIds = jdbc.queryForList(
                "select id from rabbits where house_id = ? and gender = '0' order by request_id",
                Long.class,
                houseId
        );
        List<Long> maleIds = jdbc.queryForList(
                "select id from rabbits where house_id = ? and gender = '1' order by request_id",
                Long.class,
                houseId
        );
        Assertions.assertEquals(MOTHER_COUNT, motherIds.size());
        Assertions.assertEquals(MALE_COUNT, maleIds.size());
        return new LargeBreedingStock(motherIds, maleIds);
    }

    private void assertProductionFactsBeforeOutbound(
            UserSession owner,
            long houseId,
            long batchId,
            LargeBreedingStock stock,
            List<Long> successfulMothers
    ) {
        Assertions.assertEquals(1_100, count(
                "select count(*) from breeding_cycles where house_id = ? and batch_id = ?",
                houseId,
                batchId
        ));
        Assertions.assertEquals(EMPTY_COUNT, count(
                "select count(*) from breeding_cycles where house_id = ? and batch_id = ? and status = '空怀' and closed_at is not null",
                houseId,
                batchId
        ));
        Assertions.assertEquals(FAILED_PARTURITION_COUNT, count(
                "select count(*) from breeding_cycles where house_id = ? and batch_id = ? and status = '分娩失败' and closed_at is not null",
                houseId,
                batchId
        ));
        Assertions.assertEquals(SUCCESSFUL_MOTHER_COUNT, count(
                "select count(*) from breeding_cycles where house_id = ? and batch_id = ? and status = '已断奶' and closed_at is not null",
                houseId,
                batchId
        ));
        Assertions.assertEquals(OVERLAP_REMATING_COUNT, count(
                "select count(*) from breeding_cycles where house_id = ? and batch_id = ? and cycle_no = 2 "
                        + "and status = '怀孕确认' and closed_at is null",
                houseId,
                batchId
        ));
        Assertions.assertEquals(900, count(
                "select count(*) from parturition_records where house_id = ? and batch_id = ?",
                houseId,
                batchId
        ));
        Assertions.assertEquals(SUCCESSFUL_MOTHER_COUNT, count(
                "select count(*) from weaning_records where house_id = ? and batch_id = ?",
                houseId,
                batchId
        ));
        Assertions.assertEquals(FAILED_PARTURITION_COUNT, count(
                "select count(*) from rabbit_departure_records where house_id = ? and departure_type = 'parturition_fail'",
                houseId
        ));
        Assertions.assertEquals(COMMODITY_RABBIT_COUNT, count(
                "select count(*) from rabbits where house_id = ? and birth_batch_id = ? and type = '2' and is_active = true",
                houseId,
                batchId
        ));
        Assertions.assertEquals(SUCCESSFUL_MOTHER_COUNT, count(
                "select count(distinct mother_id) from rabbits where house_id = ? and birth_batch_id = ?",
                houseId,
                batchId
        ));
        Assertions.assertEquals(SUCCESSFUL_MOTHER_COUNT, count(
                "select count(distinct birth_cycle_id) from rabbits where house_id = ? and birth_batch_id = ?",
                houseId,
                batchId
        ));
        Assertions.assertEquals(0, count(
                "select count(*) from ("
                        + "select mother_id, count(*) kit_count from rabbits where house_id = ? and birth_batch_id = ? "
                        + "group by mother_id having count(*) <> ?"
                        + ") invalid_litters",
                houseId,
                batchId,
                KITS_PER_SUCCESSFUL_LITTER
        ));
        Assertions.assertEquals(0, count(
                "select count(*) from rabbits child left join rabbits father on father.id = child.father_id "
                        + "where child.house_id = ? and child.birth_batch_id = ? "
                        + "and (child.mother_id is null or child.birth_cycle_id is null or father.id is null or father.house_id <> ?)",
                houseId,
                batchId,
                houseId
        ));

        Assertions.assertEquals(900, count(
                "select coalesce(sum(total_litters), 0) from breeding_performance where house_id = ?",
                houseId
        ));
        Assertions.assertEquals(TOTAL_RECORDED_KITS, count(
                "select coalesce(sum(total_kits), 0) from breeding_performance where house_id = ?",
                houseId
        ));
        Assertions.assertEquals(COMMODITY_RABBIT_COUNT, count(
                "select coalesce(sum(total_live_kits), 0) from breeding_performance where house_id = ?",
                houseId
        ));
        Assertions.assertEquals(COMMODITY_RABBIT_COUNT, count(
                "select coalesce(sum(total_weaned), 0) from breeding_performance where house_id = ?",
                houseId
        ));
        Assertions.assertEquals(1_000, count(
                "select coalesce(sum(success_breeding_count), 0) from breeding_performance where house_id = ?",
                houseId
        ));
        Assertions.assertEquals(EMPTY_COUNT, count(
                "select coalesce(sum(failed_breeding_count), 0) from breeding_performance where house_id = ?",
                houseId
        ));

        JsonNode report = api.getOk(
                "/api/reports/dashboard?houseId=" + houseId,
                owner.token,
                null
        );
        Assertions.assertEquals(ACTIVE_SEED_COUNT + COMMODITY_RABBIT_COUNT,
                report.get("totalRabbits").asInt());
        Assertions.assertEquals(ACTIVE_SEED_COUNT, report.get("seedRabbits").asInt());
        Assertions.assertEquals(COMMODITY_RABBIT_COUNT, report.get("commodityRabbits").asInt());
        Assertions.assertEquals(OVERLAP_REMATING_COUNT, report.get("bredRabbits").asInt());
        Assertions.assertEquals(
                MOTHER_COUNT - FAILED_PARTURITION_COUNT - OVERLAP_REMATING_COUNT,
                report.get("readyForBreeding").asInt()
        );
        Assertions.assertEquals(900, report.get("litters").asInt());
        Assertions.assertEquals(0, report.get("nursingKits").asInt());
        Assertions.assertEquals((double) COMMODITY_RABBIT_COUNT / TOTAL_RECORDED_KITS,
                report.get("liveRate").asDouble(), 0.000_001D);
        Assertions.assertEquals(COMMODITY_RABBIT_COUNT, sumArray(report.get("monthlyBirths")));
        Assertions.assertEquals(COMMODITY_RABBIT_COUNT, sumArray(report.get("monthlyWeaned")));

        Assertions.assertEquals(SUCCESSFUL_MOTHER_COUNT, successfulMothers.size());
        Assertions.assertEquals(MOTHER_COUNT, stock.motherIds.size());
        Assertions.assertEquals(MALE_COUNT, stock.maleIds.size());
    }

    private void assertClosedLoopAccounting(
            UserSession owner,
            long houseId,
            long batchId,
            LargeBreedingStock stock,
            long saleOrderId
    ) {
        JsonNode completed = api.getOk("/api/batches/" + batchId, owner.token, houseId);
        Assertions.assertEquals("已完成", completed.get("status").asText());
        Assertions.assertNotNull(completed.get("endDate"));

        Assertions.assertEquals(MOTHER_COUNT + COMMODITY_RABBIT_COUNT, count(
                "select count(*) from batch_rabbits where batch_id = ?",
                batchId
        ));
        Assertions.assertEquals(0, count(
                "select count(*) from batch_rabbits where batch_id = ? and is_active = true",
                batchId
        ));
        Assertions.assertEquals(OVERLAP_REMATING_COUNT, count(
                "select count(*) from breeding_cycles where house_id = ? and batch_id = ? and cycle_no = 2 "
                        + "and status = '已终止' and closed_at is not null and next_event_type is null",
                houseId,
                batchId
        ));
        Assertions.assertEquals(0, count(
                "select count(*) from breeding_cycles where house_id = ? and batch_id = ? and closed_at is null",
                houseId,
                batchId
        ));
        Assertions.assertEquals(COMMODITY_RABBIT_COUNT, count(
                "select count(*) from rabbit_departure_records where house_id = ? and departure_type = 'sale'",
                houseId
        ));
        Assertions.assertEquals(MOTHER_COUNT - FAILED_PARTURITION_COUNT, count(
                "select count(*) from rabbit_departure_records where house_id = ? and departure_type = 'cull'",
                houseId
        ));
        Assertions.assertEquals(FAILED_PARTURITION_COUNT, count(
                "select count(*) from rabbit_departure_records where house_id = ? and departure_type = 'parturition_fail'",
                houseId
        ));
        Assertions.assertEquals(COMMODITY_RABBIT_COUNT, count(
                "select count(*) from rabbit_status_history where house_id = ? and batch_id = ? and to_status = '出售出栏'",
                houseId,
                batchId
        ));
        Assertions.assertEquals(COMMODITY_RABBIT_COUNT, count(
                "select count(*) from sale_order_items where sale_order_id = ? and batch_id_snapshot = ?",
                saleOrderId,
                batchId
        ));
        Assertions.assertEquals(MALE_COUNT, count(
                "select count(*) from rabbits where house_id = ? and is_active = true",
                houseId
        ));
        Assertions.assertEquals(MALE_COUNT, count(
                "select coalesce(sum(rabbit_count), 0) from cages where house_id = ?",
                houseId
        ));
        Assertions.assertEquals(0, count(
                "select count(*) from cages c where c.house_id = ? and c.rabbit_count <> ("
                        + "select count(*) from rabbits r where r.house_id = c.house_id and r.cage_id = c.id and r.is_active = true"
                        + ")",
                houseId
        ));

        JsonNode report = api.getOk("/api/reports/dashboard?houseId=" + houseId, owner.token, null);
        Assertions.assertEquals(MALE_COUNT, report.get("totalRabbits").asInt());
        Assertions.assertEquals(MALE_COUNT, report.get("seedRabbits").asInt());
        Assertions.assertEquals(0, report.get("femaleRabbits").asInt());
        Assertions.assertEquals(0, report.get("commodityRabbits").asInt());
        Assertions.assertEquals(0, report.get("bredRabbits").asInt());
        Assertions.assertEquals(0, report.get("readyForBreeding").asInt());
        Assertions.assertEquals(0, report.get("nursingKits").asInt());

        Assertions.assertEquals(MOTHER_COUNT, stock.motherIds.size());
        Assertions.assertEquals(MALE_COUNT, stock.maleIds.size());
    }

    private void mate(
            UserSession owner,
            long houseId,
            long batchId,
            long motherId,
            long fatherId,
            long actionTime,
            String requestPrefix
    ) {
        api.postOk("/api/batches/" + batchId + "/mating", owner.token, houseId, obj(
                "femaleRabbitId", motherId,
                "maleRabbitId", fatherId,
                "matingDate", actionTime,
                "requestId", requestId(requestPrefix)
        ));
    }

    private void pregnancyCheck(
            UserSession owner,
            long houseId,
            long batchId,
            long motherId,
            String result,
            long actionTime,
            String requestPrefix
    ) {
        api.postOk("/api/batches/" + batchId + "/pregnancy-check", owner.token, houseId, obj(
                "rabbitId", motherId,
                "checkDate", actionTime,
                "result", result,
                "requestId", requestId(requestPrefix)
        ));
    }

    private void prepartum(
            UserSession owner,
            long houseId,
            long batchId,
            long motherId,
            long actionTime,
            String requestPrefix
    ) {
        api.postOk("/api/batches/" + batchId + "/prepartum/finish", owner.token, houseId, obj(
                "rabbitId", motherId,
                "actionDate", actionTime,
                "requestId", requestId(requestPrefix)
        ));
    }

    private void parturition(
            UserSession owner,
            long houseId,
            long batchId,
            long motherId,
            int totalKits,
            int liveKits,
            boolean failed,
            long actionTime,
            String requestPrefix
    ) {
        api.postOk("/api/batches/" + batchId + "/parturition", owner.token, houseId, obj(
                "rabbitId", motherId,
                "birthDate", actionTime,
                "totalKits", totalKits,
                "liveKits", liveKits,
                "failed", failed,
                "requestId", requestId(requestPrefix)
        ));
    }

    private void wean(
            UserSession owner,
            long houseId,
            long batchId,
            long motherId,
            long breedingCycleId,
            long targetCageId,
            long actionTime,
            String requestPrefix
    ) {
        api.postOk("/api/batches/" + batchId + "/weaning", owner.token, houseId, obj(
                "rabbitId", motherId,
                "breedingCycleId", breedingCycleId,
                "weaningDate", actionTime,
                "weaningCount", KITS_PER_SUCCESSFUL_LITTER,
                "maleCount", 4,
                "femaleCount", 3,
                "targetCageId", targetCageId,
                "avgWeight", 1.1,
                "requestId", requestId(requestPrefix)
        ));
    }

    private void cull(
            UserSession owner,
            long houseId,
            long rabbitId,
            long actionTime,
            String requestPrefix
    ) {
        api.postOk("/api/rabbits/events", owner.token, houseId, obj(
                "rabbitId", rabbitId,
                "eventType", "cull",
                "actionDate", actionTime,
                "reason", "large batch breeding doe exit",
                "forceExitBatch", true,
                "requestId", requestId(requestPrefix)
        ));
    }

    private int count(String sql, Object... parameters) {
        Integer value = jdbc.queryForObject(sql, Integer.class, parameters);
        return value == null ? 0 : value;
    }

    private int sumArray(JsonNode array) {
        int total = 0;
        for (JsonNode value : array) {
            total += value.asInt();
        }
        return total;
    }

    private void recordTiming(Map<String, Long> timings, String stage, long startedAt) {
        long elapsed = elapsedMillis(startedAt);
        timings.put(stage, elapsed);
        LOGGER.info("Large whole-house batch stage {} completed in {} ms", stage, elapsed);
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private void assertStageWithin(Map<String, Long> timings, String stage, long maxMillis) {
        long actual = timings.get(stage);
        Assertions.assertTrue(
                actual <= maxMillis,
                () -> stage + " exceeded budget: " + actual + "ms > " + maxMillis + "ms; all timings=" + timings
        );
    }

    private static final class LargeBreedingStock {
        private final List<Long> motherIds;
        private final List<Long> maleIds;

        private LargeBreedingStock(List<Long> motherIds, List<Long> maleIds) {
            this.motherIds = motherIds;
            this.maleIds = maleIds;
        }
    }
}
