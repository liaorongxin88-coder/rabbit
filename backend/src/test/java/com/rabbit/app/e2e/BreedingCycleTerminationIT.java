package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class BreedingCycleTerminationIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void forcedBatchCompletionClosesOpenCyclesAndStopsReminders() {
        UserSession owner = register("force_complete_cycle");
        long houseId = createHouse(owner, "force_complete_cycle_house", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        configureImmediateEvents(owner, houseId, "force_complete_settings");
        long motherId = createRabbit(
            owner,
            houseId,
            cages.get(0),
            "0",
            "0",
            "force_complete_mother"
        );
        long fatherId = createRabbit(
            owner,
            houseId,
            cages.get(1),
            "0",
            "1",
            "force_complete_father"
        );
        long batchId = createMatedBatch(
            owner,
            houseId,
            motherId,
            fatherId,
            "force_complete"
        );

        Assertions.assertEquals(1, openCycleCount(houseId, batchId));
        Assertions.assertEquals(1, dueCycleCount(houseId, batchId));

        api.postOk(
            "/api/batches/" + batchId + "/complete",
            owner.token,
            houseId,
            obj(
                "force",
                true,
                "endDate",
                oneMinuteAgo(),
                "remark",
                "terminate unfinished production",
                "requestId",
                requestId("force_complete_request")
            )
        );

        Assertions.assertEquals("已完成", api.getOk(
            "/api/batches/" + batchId,
            owner.token,
            houseId
        ).get("status").asText());
        Assertions.assertEquals(0, openCycleCount(houseId, batchId));
        Assertions.assertEquals(0, dueCycleCount(houseId, batchId));
        Assertions.assertEquals(1, terminatedCycleCount(
            houseId,
            batchId,
            "批次强制结束"
        ));
        Assertions.assertEquals(0, jdbc.queryForObject(
            "select count(*) from batch_rabbits where batch_id = ? and is_active = true",
            Integer.class,
            batchId
        ));
    }

    @Test
    void culledMotherClosesHerOpenCycleBeforeBatchAutoCompletion() {
        UserSession owner = register("cull_open_cycle");
        long houseId = createHouse(owner, "cull_open_cycle_house", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        configureImmediateEvents(owner, houseId, "cull_cycle_settings");
        long motherId = createRabbit(
            owner,
            houseId,
            cages.get(0),
            "0",
            "0",
            "cull_cycle_mother"
        );
        long fatherId = createRabbit(
            owner,
            houseId,
            cages.get(1),
            "0",
            "1",
            "cull_cycle_father"
        );
        long batchId = createMatedBatch(
            owner,
            houseId,
            motherId,
            fatherId,
            "cull_cycle"
        );

        api.postOk(
            "/api/rabbits/events",
            owner.token,
            houseId,
            obj(
                "rabbitId",
                motherId,
                "eventType",
                "cull",
                "actionDate",
                oneMinuteAgo(),
                "reason",
                "health cull during gestation",
                "forceExitBatch",
                true,
                "requestId",
                requestId("cull_cycle_request")
            )
        );

        Assertions.assertEquals("已完成", api.getOk(
            "/api/batches/" + batchId,
            owner.token,
            houseId
        ).get("status").asText());
        Assertions.assertEquals(0, openCycleCount(houseId, batchId));
        Assertions.assertEquals(0, dueCycleCount(houseId, batchId));
        Assertions.assertEquals(1, terminatedCycleCount(
            houseId,
            batchId,
            "兔离场:cull"
        ));
        Assertions.assertEquals(0, jdbc.queryForObject(
            "select count(*) from rabbits where house_id = ? and id = ? and is_active = true",
            Integer.class,
            houseId,
            motherId
        ));
    }

    private void configureImmediateEvents(
        UserSession owner,
        long houseId,
        String requestPrefix
    ) {
        api.putOk(
            "/api/settings",
            owner.token,
            null,
            obj(
                "aphrodisiacDays",
                0,
                "palpationDays",
                0,
                "prepartumDays",
                0,
                "weaningDays",
                0,
                "postpartumDays",
                0,
                "saleDays",
                0,
                "replacementDays",
                30,
                "requestId",
                requestId(requestPrefix)
            )
        );
    }

    private long createMatedBatch(
        UserSession owner,
        long houseId,
        long motherId,
        long fatherId,
        String requestPrefix
    ) {
        JsonNode batch = api.postOk(
            "/api/batches",
            owner.token,
            houseId,
            obj(
                "batchCode",
                "TERM-" + requestId(requestPrefix).substring(0, 10),
                "femaleRabbitIds",
                List.of(motherId),
                "requestId",
                requestId(requestPrefix + "_batch")
            )
        );
        long batchId = batch.get("id").asLong();
        api.postOk(
            "/api/batches/" + batchId + "/aphrodisiac/start",
            owner.token,
            houseId,
            obj(
                "rabbitIds",
                List.of(motherId),
                "requestId",
                requestId(requestPrefix + "_start")
            )
        );
        api.postOk(
            "/api/batches/" + batchId + "/aphrodisiac/finish",
            owner.token,
            houseId,
            obj(
                "rabbitIds",
                List.of(motherId),
                "requestId",
                requestId(requestPrefix + "_finish")
            )
        );
        api.postOk(
            "/api/batches/" + batchId + "/mating",
            owner.token,
            houseId,
            obj(
                "femaleRabbitId",
                motherId,
                "maleRabbitId",
                fatherId,
                "matingDate",
                oneMinuteAgo(),
                "requestId",
                requestId(requestPrefix + "_mating")
            )
        );
        return batchId;
    }

    private int openCycleCount(long houseId, long batchId) {
        return jdbc.queryForObject(
            "select count(*) from breeding_cycles where house_id = ? and batch_id = ? and closed_at is null",
            Integer.class,
            houseId,
            batchId
        );
    }

    private int dueCycleCount(long houseId, long batchId) {
        return jdbc.queryForObject(
            "select count(*) from breeding_cycles where house_id = ? and batch_id = ? " +
            "and closed_at is null and next_event_date <= now()",
            Integer.class,
            houseId,
            batchId
        );
    }

    private int terminatedCycleCount(
        long houseId,
        long batchId,
        String reason
    ) {
        return jdbc.queryForObject(
            "select count(*) from breeding_cycles where house_id = ? and batch_id = ? " +
            "and status = '已终止' and closed_at is not null and close_reason = ? " +
            "and next_event_date is null and next_event_type is null and current_nursing_kits = 0",
            Integer.class,
            houseId,
            batchId,
            reason
        );
    }
}
