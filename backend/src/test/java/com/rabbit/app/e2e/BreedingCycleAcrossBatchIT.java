package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class BreedingCycleAcrossBatchIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void motherCanStartCycleOneAgainAfterMovingToANewBatch() {
        UserSession owner = register("cycle_across_batch");
        long houseId = createHouse(owner, "cycle_across_batch_house", 1, 2, 1);
        List<Long> cages = cageIds(owner, houseId);
        long motherId = createRabbit(
            owner,
            houseId,
            cages.get(0),
            "0",
            "0",
            "cycle_across_batch_mother"
        );
        long fatherId = createRabbit(
            owner,
            houseId,
            cages.get(1),
            "0",
            "1",
            "cycle_across_batch_father"
        );

        long firstBatchId = createBatch(owner, houseId, motherId, "FIRST");
        startMating(owner, houseId, firstBatchId, motherId, fatherId, "first");
        JsonNode firstCycles = api.getOk(
            "/api/batches/" + firstBatchId + "/breeding-cycles",
            owner.token,
            houseId
        );
        long firstCycleId = firstCycles.get(0).get("id").asLong();
        Assertions.assertEquals(1, firstCycles.get(0).get("cycleNo").asInt());

        api.postOk(
            "/api/batches/" + firstBatchId + "/pregnancy-check",
            owner.token,
            houseId,
            obj(
                "rabbitId", motherId,
                "breedingCycleId", firstCycleId,
                "checkDate", oneMinuteAgo(),
                "result", "空怀",
                "remark", "close first batch cycle",
                "requestId", requestId("first_empty")
            )
        );
        api.postOk(
            "/api/batches/" + firstBatchId + "/complete",
            owner.token,
            houseId,
            obj(
                "endDate", oneMinuteAgo(),
                "force", true,
                "remark", "move mother to next production batch",
                "requestId", requestId("first_complete")
            )
        );

        long secondBatchId = createBatch(owner, houseId, motherId, "SECOND");
        startMating(owner, houseId, secondBatchId, motherId, fatherId, "second");
        JsonNode secondCycles = api.getOk(
            "/api/batches/" + secondBatchId + "/breeding-cycles",
            owner.token,
            houseId
        );

        Assertions.assertEquals(1, secondCycles.size());
        Assertions.assertEquals(1, secondCycles.get(0).get("cycleNo").asInt());
        Assertions.assertEquals(secondBatchId, secondCycles.get(0).get("batchId").asLong());
        Assertions.assertEquals(
            2,
            jdbc.queryForObject(
                "select count(*) from breeding_cycles where house_id = ? and mother_rabbit_id = ?",
                Integer.class,
                houseId,
                motherId
            )
        );
    }

    private long createBatch(
        UserSession owner,
        long houseId,
        long motherId,
        String suffix
    ) {
        JsonNode batch = api.postOk(
            "/api/batches",
            owner.token,
            houseId,
            obj(
                "batchCode", suffix + "-" + requestId("code").substring(0, 12),
                "femaleRabbitIds", List.of(motherId),
                "requestId", requestId(suffix + "_batch")
            )
        );
        return batch.get("id").asLong();
    }

    private void startMating(
        UserSession owner,
        long houseId,
        long batchId,
        long motherId,
        long fatherId,
        String suffix
    ) {
        api.postOk(
            "/api/batches/" + batchId + "/aphrodisiac/start",
            owner.token,
            houseId,
            obj(
                "rabbitIds", List.of(motherId),
                "requestId", requestId(suffix + "_aph_start")
            )
        );
        api.postOk(
            "/api/batches/" + batchId + "/aphrodisiac/finish",
            owner.token,
            houseId,
            obj(
                "rabbitIds", List.of(motherId),
                "requestId", requestId(suffix + "_aph_finish")
            )
        );
        api.postOk(
            "/api/batches/" + batchId + "/mating",
            owner.token,
            houseId,
            obj(
                "femaleRabbitId", motherId,
                "maleRabbitId", fatherId,
                "matingDate", oneMinuteAgo(),
                "requestId", requestId(suffix + "_mating")
            )
        );
    }
}
