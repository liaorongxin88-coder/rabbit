package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

public class ReproRequiredFieldsIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void abortionRequiresDetailAndAnImageFromTheSameHouse() {
        Fixture f = awaitingPalpation("required_abort");
        String imageId = uploadTestImage(f.owner, f.houseId, "abortion");

        api.expectError(
            actionPath(f.cycleId), HttpMethod.POST, f.owner.token, f.houseId,
            obj(
                "action", "ABORTION",
                "occurredAt", now(),
                "stillbirthCount", 1,
                "attachmentFileIds", List.of(imageId),
                "requestId", requestId("abort_missing_detail")
            ),
            400,
            "流产详情"
        );
        api.expectError(
            actionPath(f.cycleId), HttpMethod.POST, f.owner.token, f.houseId,
            obj(
                "action", "ABORTION",
                "occurredAt", now(),
                "stillbirthCount", 1,
                "remark", "疑似应激导致流产",
                "requestId", requestId("abort_missing_image")
            ),
            400,
            "上传"
        );

        UserSession other = register("required_abort_other");
        long otherHouse = createHouse(other, "other", 1, 1, 1);
        String otherImage = uploadTestImage(other, otherHouse, "other-abortion");
        api.expectError(
            actionPath(f.cycleId), HttpMethod.POST, f.owner.token, f.houseId,
            obj(
                "action", "ABORTION",
                "occurredAt", now(),
                "stillbirthCount", 1,
                "remark", "跨兔舍图片不应通过",
                "attachmentFileIds", List.of(otherImage),
                "requestId", requestId("abort_cross_house_image")
            ),
            400,
            "不属于当前兔舍"
        );

        JsonNode result = api.postOk(
            actionPath(f.cycleId), f.owner.token, f.houseId,
            obj(
                "action", "ABORTION",
                "occurredAt", now(),
                "stillbirthCount", 1,
                "remark", "疑似应激导致流产",
                "attachmentFileIds", List.of(imageId),
                "requestId", requestId("abort_ok")
            )
        );
        Assertions.assertTrue(result.get("eventId").asLong() > 0);
        Assertions.assertEquals(1, jdbc.queryForObject(
            "select count(*) from biz_attachments where house_id = ? and biz_id = ?",
            Integer.class, f.houseId, result.get("eventId").asLong()
        ));
        Assertions.assertEquals("ABORTION", jdbc.queryForObject(
            "select event_type from repro_events where id = ?",
            String.class, result.get("eventId").asLong()
        ));
    }

    @Test
    void keptKitAdjustmentRequiresTheSourceMotherWhenTheCountIncreases() {
        Fixture f = awaitingPalpation("required_kept");
        advanceToDelivery(f);
        JsonNode delivery = api.postOk(
            actionPath(f.cycleId), f.owner.token, f.houseId,
            obj(
                "action", "DELIVERY",
                "outcome", "BORN",
                "occurredAt", now(),
                "totalKits", 8,
                "liveKits", 7,
                "keptKits", 6,
                "requestId", requestId("delivery")
            )
        );
        Assertions.assertTrue(delivery.get("litterId").asLong() > 0);

        api.expectError(
            adjustmentPath(f.cycleId), HttpMethod.POST, f.owner.token, f.houseId,
            obj(
                "occurredAt", now(),
                "keptKits", 7,
                "requestId", requestId("increase_without_source")
            ),
            400,
            "来源母兔"
        );

        String requestId = requestId("increase_with_source");
        JsonNode adjusted = api.postOk(
            adjustmentPath(f.cycleId), f.owner.token, f.houseId,
            obj(
                "occurredAt", now(),
                "keptKits", 7,
                "sourceMotherRabbitId", f.sourceDoeId,
                "remark", "寄养转入 1 只",
                "requestId", requestId
            )
        );
        Assertions.assertEquals(6, adjusted.get("previousKeptKits").asInt());
        Assertions.assertEquals(7, adjusted.get("keptKits").asInt());
        Assertions.assertFalse(adjusted.get("replayed").asBoolean());
        Assertions.assertEquals(7, jdbc.queryForObject(
            "select kept_kits from litters where cycle_id = ?", Integer.class, f.cycleId
        ));
        Assertions.assertEquals(1, jdbc.queryForObject(
            "select foster_in from litters where cycle_id = ?", Integer.class, f.cycleId
        ));

        JsonNode replayed = api.postOk(
            adjustmentPath(f.cycleId), f.owner.token, f.houseId,
            obj(
                "occurredAt", now(),
                "keptKits", 7,
                "sourceMotherRabbitId", f.sourceDoeId,
                "remark", "寄养转入 1 只",
                "requestId", requestId
            )
        );
        Assertions.assertTrue(replayed.get("replayed").asBoolean());
        Assertions.assertEquals(1, jdbc.queryForObject(
            "select count(*) from repro_events where house_id = ? and request_id = ?",
            Integer.class, f.houseId, requestId
        ));
    }

    @Test
    void commodityToReplacementReturnsTheCreatedReplacementRecordIdOnReplay() {
        UserSession owner = register("required_replacement");
        long houseId = createHouse(owner, "replacement", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        long rabbitId = createRabbit(owner, houseId, cages.get(0), "2", "0", "commodity");
        String requestId = requestId("to_replacement");

        JsonNode first = api.postOk(
            "/api/rabbits/replacement", owner.token, houseId,
            obj(
                "rabbitIds", List.of(rabbitId),
                "targetCageId", cages.get(1),
                "forceExitBatch", true,
                "requestId", requestId
            )
        );
        JsonNode item = first.get("items").get(0);
        Assertions.assertEquals(rabbitId, item.get("rabbitId").asLong());
        Assertions.assertTrue(item.get("replacementRecordId").asLong() > 0);
        Assertions.assertEquals(cages.get(1).longValue(), item.get("targetCageId").asLong());

        JsonNode replay = api.postOk(
            "/api/rabbits/replacement", owner.token, houseId,
            obj(
                "rabbitIds", List.of(rabbitId),
                "targetCageId", cages.get(1),
                "forceExitBatch", true,
                "requestId", requestId
            )
        );
        Assertions.assertEquals(
            item.get("replacementRecordId").asLong(),
            replay.get("items").get(0).get("replacementRecordId").asLong()
        );
    }

    @Test
    void actionExecutionTimeIsRequiredAtTheHttpBoundary() {
        Fixture f = awaitingPalpation("required_time");
        api.expectError(
            actionPath(f.cycleId), HttpMethod.POST, f.owner.token, f.houseId,
            obj(
                "action", "PALPATION",
                "palpationResult", "PREGNANT",
                "requestId", requestId("missing_time")
            ),
            400,
            "执行时间"
        );
        api.expectError(
            actionPath(f.cycleId), HttpMethod.POST, f.owner.token, f.houseId,
            obj(
                "action", "PALPATION",
                "occurredAt", now() + 24L * 3600L * 1000L,
                "palpationResult", "PREGNANT",
                "requestId", requestId("future_time")
            ),
            400,
            "不能晚于当前时间"
        );
    }

    private Fixture awaitingPalpation(String prefix) {
        UserSession owner = register(prefix);
        long houseId = createHouse(owner, prefix, 1, 4, 1);
        List<Long> cages = cageIds(owner, houseId);
        long doeId = createRabbit(owner, houseId, cages.get(0), "0", "0", prefix + "_doe");
        long buckId = createRabbit(owner, houseId, cages.get(1), "0", "1", prefix + "_buck");
        long sourceDoeId = createRabbit(owner, houseId, cages.get(2), "0", "0", prefix + "_source");
        long batchId = api.postOk("/api/batches", owner.token, houseId, obj(
            "batchCode", "REQ-" + requestId(prefix).substring(0, 8),
            "femaleRabbitIds", List.of(doeId),
            "requestId", requestId(prefix + "_batch")
        )).get("id").asLong();
        long cycleId = api.postOk("/api/repro/cycles", owner.token, houseId, obj(
            "motherRabbitId", doeId,
            "batchId", batchId,
            "stage", "AWAIT_ESTRUS",
            "occurredAt", now(),
            "requestId", requestId(prefix + "_cycle")
        )).get("cycleId").asLong();
        api.postOk(actionPath(cycleId), owner.token, houseId, obj(
            "action", "ESTRUS", "occurredAt", now(), "requestId", requestId("estrus")
        ));
        api.postOk(actionPath(cycleId), owner.token, houseId, obj(
            "action", "MATING", "occurredAt", now(), "maleRabbitId", buckId,
            "matingMethod", "NATURAL", "requestId", requestId("mating")
        ));
        return new Fixture(owner, houseId, batchId, cycleId, doeId, sourceDoeId);
    }

    private void advanceToDelivery(Fixture f) {
        api.postOk(actionPath(f.cycleId), f.owner.token, f.houseId, obj(
            "action", "PALPATION", "occurredAt", now(), "palpationResult", "PREGNANT",
            "requestId", requestId("palpation")
        ));
        api.postOk(actionPath(f.cycleId), f.owner.token, f.houseId, obj(
            "action", "PREPARTUM", "occurredAt", now(), "requestId", requestId("prepartum")
        ));
    }

    private static String actionPath(long cycleId) {
        return "/api/repro/cycles/" + cycleId + "/actions";
    }

    private static String adjustmentPath(long cycleId) {
        return "/api/repro/cycles/" + cycleId + "/kept-kits-adjustments";
    }

    private record Fixture(
        UserSession owner,
        long houseId,
        long batchId,
        long cycleId,
        long doeId,
        long sourceDoeId
    ) {
    }
}
