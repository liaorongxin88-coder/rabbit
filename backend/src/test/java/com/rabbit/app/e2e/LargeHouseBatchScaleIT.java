package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Capacity baseline for a production-sized rabbit house. The fixture preparation
 * is bulk-loaded so the measured path remains batch creation and breeding work.
 */
public class LargeHouseBatchScaleIT extends E2eTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(LargeHouseBatchScaleIT.class);
    private static final int MOTHER_COUNT = 1_000;
    private static final int MALE_COUNT = 20;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void oneHouseBatchAcceptsOneThousandMothersAtTheSameMatingTimeWithoutHouseLeakage(
            TestReporter reporter
    ) {
        UserSession owner = register("large_house_batch");
        long houseId = createHouse(owner, "large_house_batch_house", 20, 51, 1);
        BreedingStock stock = insertBreedingStock(owner, houseId, MOTHER_COUNT, MALE_COUNT);

        api.putOk("/api/settings", owner.token, null, obj(
                "aphrodisiacDays", 0,
                "palpationDays", 0,
                "prepartumDays", 3,
                "weaningDays", 35,
                "postpartumDays", 7,
                "saleDays", 70,
                "replacementDays", 150,
                "remark", "large house batch scale baseline",
                "requestId", requestId("large_house_settings")
        ));

        long batchStartedAt = System.nanoTime();
        JsonNode batch = api.postOk("/api/batches", owner.token, houseId, obj(
                "batchCode", "LARGE-HOUSE-" + requestId("batch").substring(0, 12),
                "femaleRabbitIds", stock.motherIds,
                "remark", "one batch containing one thousand breeding does",
                "requestId", requestId("large_house_batch")
        ));
        long batchCreatedAt = System.nanoTime();
        long batchId = batch.get("id").asLong();
        Assertions.assertEquals("计划中", batch.get("status").asText());

        api.postOk("/api/batches/" + batchId + "/aphrodisiac/start", owner.token, houseId, obj(
                "rabbitIds", stock.motherIds,
                "requestId", requestId("large_house_aph_start")
        ));
        api.postOk("/api/batches/" + batchId + "/aphrodisiac/finish", owner.token, houseId, obj(
                "rabbitIds", stock.motherIds,
                "requestId", requestId("large_house_aph_finish")
        ));
        long preparationFinishedAt = System.nanoTime();

        long commonMatingTime = oneMinuteAgo();
        for (int index = 0; index < stock.motherIds.size(); index++) {
            api.postOk("/api/batches/" + batchId + "/mating", owner.token, houseId, obj(
                    "femaleRabbitId", stock.motherIds.get(index),
                    "maleRabbitId", stock.maleIds.get(index % stock.maleIds.size()),
                    "matingDate", commonMatingTime,
                    "requestId", requestId("large_mating_" + index)
            ));
        }
        long matingFinishedAt = System.nanoTime();

        JsonNode batchRabbits = api.getOk(
                "/api/batches/" + batchId + "/batch-rabbits?role=breeding&active=true",
                owner.token,
                houseId
        );
        JsonNode cycles = api.getOk(
                "/api/batches/" + batchId + "/breeding-cycles?activeOnly=true",
                owner.token,
                houseId
        );
        JsonNode events = api.getOk("/api/events?onlyUnnotified=true", owner.token, houseId);

        Assertions.assertEquals(MOTHER_COUNT, batchRabbits.size());
        Assertions.assertEquals(MOTHER_COUNT, cycles.size());
        Assertions.assertEquals(MOTHER_COUNT, events.size());
        assertEventSet(events, batchId, stock.motherIds);

        Assertions.assertEquals(MOTHER_COUNT, count(
                "select count(*) from batch_rabbits where batch_id = ? and batch_role = 'breeding' and is_active = true",
                batchId
        ));
        Assertions.assertEquals(MOTHER_COUNT, count(
                "select count(distinct rabbit_id) from batch_rabbits where batch_id = ? and batch_role = 'breeding'",
                batchId
        ));
        Assertions.assertEquals(MOTHER_COUNT, count(
                "select count(*) from batch_rabbits where batch_id = ? and current_status = '已配种' "
                        + "and next_event_type = '摸胎' and latest_cycle_id is not null",
                batchId
        ));
        Assertions.assertEquals(MOTHER_COUNT, count(
                "select count(*) from breeding_cycles where house_id = ? and batch_id = ? "
                        + "and cycle_no = 1 and status = '已配种' and closed_at is null and next_event_type = '摸胎'",
                houseId,
                batchId
        ));
        Assertions.assertEquals(MOTHER_COUNT, count(
                "select count(distinct mother_rabbit_id) from breeding_cycles where house_id = ? and batch_id = ?",
                houseId,
                batchId
        ));
        Assertions.assertEquals(1, count(
                "select count(distinct mating_date) from breeding_cycles where house_id = ? and batch_id = ?",
                houseId,
                batchId
        ));
        Assertions.assertEquals(MOTHER_COUNT, count(
                "select count(*) from rabbit_status_history where house_id = ? and batch_id = ? "
                        + "and reason = '配种' and to_status = '已配种'",
                houseId,
                batchId
        ));
        Assertions.assertEquals(MOTHER_COUNT, count(
                "select count(*) from request_dedup where house_id = ? and user_id = ? "
                        + "and api = 'batch.mating' and status = 'DONE'",
                houseId,
                owner.userId
        ));
        Assertions.assertEquals(0, count(
                "select count(*) from breeding_cycles bc "
                        + "join batches b on b.id = bc.batch_id "
                        + "join rabbits mother on mother.id = bc.mother_rabbit_id "
                        + "left join rabbits father on father.id = bc.male_rabbit_id "
                        + "where bc.batch_id = ? and (bc.house_id <> ? or b.house_id <> ? "
                        + "or mother.house_id <> ? or father.house_id <> ?)",
                batchId,
                houseId,
                houseId,
                houseId,
                houseId
        ));

        long otherHouseId = createHouse(owner, "large_house_isolation_house", 1, 2, 1);
        List<Long> otherCages = cageIds(owner, otherHouseId);
        long otherMother = createRabbit(owner, otherHouseId, otherCages.get(0), "0", "0", "isolation_doe");
        long otherMale = createRabbit(owner, otherHouseId, otherCages.get(1), "0", "1", "isolation_buck");
        JsonNode otherBatch = api.postOk("/api/batches", owner.token, otherHouseId, obj(
                "batchCode", "ISOLATION-" + requestId("batch").substring(0, 12),
                "femaleRabbitIds", List.of(otherMother),
                "requestId", requestId("isolation_batch")
        ));
        long otherBatchId = otherBatch.get("id").asLong();
        api.postOk("/api/batches/" + otherBatchId + "/aphrodisiac/start", owner.token, otherHouseId, obj(
                "rabbitIds", List.of(otherMother),
                "requestId", requestId("isolation_aph_start")
        ));
        api.postOk("/api/batches/" + otherBatchId + "/aphrodisiac/finish", owner.token, otherHouseId, obj(
                "rabbitIds", List.of(otherMother),
                "requestId", requestId("isolation_aph_finish")
        ));
        api.postOk("/api/batches/" + otherBatchId + "/mating", owner.token, otherHouseId, obj(
                "femaleRabbitId", otherMother,
                "maleRabbitId", otherMale,
                "matingDate", commonMatingTime,
                "requestId", requestId("isolation_mating")
        ));

        JsonNode otherEvents = api.getOk("/api/events?onlyUnnotified=true", owner.token, otherHouseId);
        Assertions.assertEquals(1, otherEvents.size());
        Assertions.assertEquals(otherBatchId, otherEvents.get(0).get("batchId").asLong());
        Assertions.assertEquals(otherMother, otherEvents.get(0).get("rabbitId").asLong());
        Assertions.assertEquals(MOTHER_COUNT, api.getOk(
                "/api/events?onlyUnnotified=true",
                owner.token,
                houseId
        ).size());
        api.expectError(
                "/api/batches/" + batchId + "/breeding-cycles",
                HttpMethod.GET,
                owner.token,
                otherHouseId,
                null,
                400,
                "批次不存在"
        );

        long verifiedAt = System.nanoTime();
        Map<String, String> metrics = Map.of(
                "mothers", String.valueOf(MOTHER_COUNT),
                "males", String.valueOf(MALE_COUNT),
                "batchCreateMs", elapsedMillis(batchStartedAt, batchCreatedAt),
                "aphrodisiacMs", elapsedMillis(batchCreatedAt, preparationFinishedAt),
                "matingMs", elapsedMillis(preparationFinishedAt, matingFinishedAt),
                "verificationMs", elapsedMillis(matingFinishedAt, verifiedAt),
                "totalMs", elapsedMillis(batchStartedAt, verifiedAt)
        );
        reporter.publishEntry(metrics);
        LOGGER.info("Large house batch scale baseline: {}", metrics);
    }

    private BreedingStock insertBreedingStock(
            UserSession owner,
            long houseId,
            int motherCount,
            int maleCount
    ) {
        List<Long> cageIds = jdbc.queryForList(
                "select id from cages where house_id = ? order by id",
                Long.class,
                houseId
        );
        Assertions.assertEquals(motherCount + maleCount, cageIds.size());

        List<Object[]> rabbitRows = new ArrayList<>(cageIds.size());
        for (int index = 0; index < motherCount; index++) {
            rabbitRows.add(new Object[]{
                    houseId,
                    cageIds.get(index),
                    "0",
                    "scale_doe",
                    "large-house-mother-" + String.format("%04d", index),
                    String.valueOf(owner.userId),
                    String.valueOf(owner.userId)
            });
        }
        for (int index = 0; index < maleCount; index++) {
            rabbitRows.add(new Object[]{
                    houseId,
                    cageIds.get(motherCount + index),
                    "1",
                    "scale_buck",
                    "large-house-male-" + String.format("%02d", index),
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
        Assertions.assertEquals(motherCount, motherIds.size());
        Assertions.assertEquals(maleCount, maleIds.size());
        return new BreedingStock(motherIds, maleIds);
    }

    private void assertEventSet(JsonNode events, long batchId, List<Long> expectedMotherIds) {
        Set<Long> rabbitIds = new HashSet<>();
        Set<Long> recordIds = new HashSet<>();
        for (JsonNode event : events) {
            Assertions.assertEquals("生产周期", event.get("category").asText());
            Assertions.assertEquals("摸胎", event.get("eventType").asText());
            Assertions.assertEquals("已配种", event.get("status").asText());
            Assertions.assertEquals(batchId, event.get("batchId").asLong());
            rabbitIds.add(event.get("rabbitId").asLong());
            recordIds.add(event.get("recordId").asLong());
        }
        Assertions.assertEquals(new HashSet<>(expectedMotherIds), rabbitIds);
        Assertions.assertEquals(MOTHER_COUNT, recordIds.size());
    }

    private int count(String sql, Object... parameters) {
        Integer value = jdbc.queryForObject(sql, Integer.class, parameters);
        return value == null ? 0 : value;
    }

    private String elapsedMillis(long startedAt, long finishedAt) {
        return String.valueOf(TimeUnit.NANOSECONDS.toMillis(finishedAt - startedAt));
    }

    private static final class BreedingStock {
        private final List<Long> motherIds;
        private final List<Long> maleIds;

        private BreedingStock(List<Long> motherIds, List<Long> maleIds) {
            this.motherIds = motherIds;
            this.maleIds = maleIds;
        }
    }
}
