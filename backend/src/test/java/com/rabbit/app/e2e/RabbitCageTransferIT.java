package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 换笼位（recvqh5TC8wd3y）的三条分支。
 *
 * <p>用真库跑是必须的：对调那条路会撞 uk_rabbits_house_active_breeding_cage 这个建在生成列上的
 * 唯一键，任何“看起来对”的两条 UPDATE 在真实 InnoDB 上都会 1062。只有真库能证明它没撞。
 */
class RabbitCageTransferIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void movesIntoEmptyCageAndFlipsBothCageStatuses() {
        UserSession owner = register("cage_move");
        long houseId = createHouse(owner, "换笼兔舍", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        long source = cages.get(0);
        long target = cages.get(1);
        long doeId = createRabbit(owner, houseId, source, "0", "0", "move_doe");

        JsonNode result = transferOk(owner, houseId, doeId, target, "move_empty");

        Assertions.assertEquals("MOVE", result.get("mode").asText());
        Assertions.assertEquals(source, result.get("fromCageId").asLong());
        Assertions.assertEquals(target, result.get("toCageId").asLong());
        Assertions.assertTrue(result.get("swappedRabbitId").isNull());
        assertCage(houseId, source, 0, "0");
        assertCage(houseId, target, 1, "1");
        Assertions.assertEquals(target, cageOf(houseId, doeId));
    }

    @Test
    void appendsCommodityRabbitIntoCommodityCage() {
        UserSession owner = register("cage_append");
        long houseId = createHouse(owner, "合笼兔舍", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        long source = cages.get(0);
        long target = cages.get(1);
        long movingId = createRabbit(owner, houseId, source, "2", "0", "append_moving");
        createRabbit(owner, houseId, target, "2", "1", "append_resident");

        JsonNode result = transferOk(owner, houseId, movingId, target, "append_ok");

        Assertions.assertEquals("APPEND", result.get("mode").asText());
        assertCage(houseId, source, 0, "0");
        assertCage(houseId, target, 2, "3");
        Assertions.assertEquals(target, cageOf(houseId, movingId));
    }

    @Test
    void rejectsCommodityRabbitJoiningBreedingCage() {
        UserSession owner = register("cage_reject_commodity");
        long houseId = createHouse(owner, "拒绝合笼兔舍", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        long source = cages.get(0);
        long target = cages.get(1);
        long movingId = createRabbit(owner, houseId, source, "2", "0", "reject_moving");
        createRabbit(owner, houseId, target, "0", "0", "reject_resident");

        JsonNode response = transferResponse(owner, houseId, movingId, target, "reject_commodity");

        Assertions.assertEquals(400, response.get("code").asInt());
        Assertions.assertTrue(response.get("message").asText().contains("不是商品兔笼"));
        Assertions.assertEquals(source, cageOf(houseId, movingId));
        assertCage(houseId, source, 1, "3");
        assertCage(houseId, target, 1, "1");
    }

    @Test
    void swapsBreedingRabbitWithReserveRabbit() {
        UserSession owner = register("cage_swap");
        long houseId = createHouse(owner, "对调兔舍", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        long source = cages.get(0);
        long target = cages.get(1);
        long doeId = createRabbit(owner, houseId, source, "0", "0", "swap_doe");
        long reserveId = createRabbit(owner, houseId, target, "1", "1", "swap_reserve");

        JsonNode result = transferOk(owner, houseId, doeId, target, "swap_ok");

        Assertions.assertEquals("SWAP", result.get("mode").asText());
        Assertions.assertEquals(reserveId, result.get("swappedRabbitId").asLong());
        Assertions.assertEquals(target, cageOf(houseId, doeId));
        Assertions.assertEquals(source, cageOf(houseId, reserveId));
        // 用途跟着换过来的兔子走：原笼变后备兔笼，目标笼变种兔笼。
        assertCage(houseId, source, 1, "2");
        assertCage(houseId, target, 1, "1");
        // 中途借用过 is_active 让唯一键放行，收尾必须复位，否则会多出一只查不到的活兔。
        Assertions.assertEquals(
                2,
                jdbc.queryForObject(
                        "select count(*) from rabbits where house_id = ? and is_active = true",
                        Integer.class,
                        houseId
                )
        );
        Assertions.assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from rabbits where house_id = ? and departure_date is not null",
                        Integer.class,
                        houseId
                )
        );
    }

    @Test
    void rejectsBreedingRabbitSwappingWithCommodityCage() {
        UserSession owner = register("cage_reject_swap");
        long houseId = createHouse(owner, "拒绝对调兔舍", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        long source = cages.get(0);
        long target = cages.get(1);
        long doeId = createRabbit(owner, houseId, source, "0", "0", "reject_swap_doe");
        createRabbit(owner, houseId, target, "2", "0", "reject_swap_commodity");

        JsonNode response = transferResponse(owner, houseId, doeId, target, "reject_swap");

        Assertions.assertEquals(400, response.get("code").asInt());
        Assertions.assertTrue(response.get("message").asText().contains("不能与种兔"));
        Assertions.assertEquals(source, cageOf(houseId, doeId));
    }

    @Test
    void replayingTheSameRequestDoesNotMoveTwice() {
        UserSession owner = register("cage_replay");
        long houseId = createHouse(owner, "幂等换笼兔舍", 1, 3, 1);
        List<Long> cages = cageIds(owner, houseId);
        long source = cages.get(0);
        long target = cages.get(1);
        long doeId = createRabbit(owner, houseId, source, "0", "0", "replay_doe");
        String requestId = requestId("replay_transfer");

        JsonNode first = api.postOk(
                "/api/rabbits/" + doeId + "/cage-transfer",
                owner.token,
                houseId,
                obj("targetCageId", target, "requestId", requestId)
        );
        JsonNode second = api.postOk(
                "/api/rabbits/" + doeId + "/cage-transfer",
                owner.token,
                houseId,
                obj("targetCageId", target, "requestId", requestId)
        );

        Assertions.assertEquals("MOVE", first.get("mode").asText());
        Assertions.assertEquals("REPLAY", second.get("mode").asText());
        assertCage(houseId, source, 0, "0");
        assertCage(houseId, target, 1, "1");
    }

    private JsonNode transferOk(UserSession owner, long houseId, long rabbitId, long targetCageId, String suffix) {
        return api.postOk(
                "/api/rabbits/" + rabbitId + "/cage-transfer",
                owner.token,
                houseId,
                obj("targetCageId", targetCageId, "requestId", requestId(suffix))
        );
    }

    private JsonNode transferResponse(UserSession owner, long houseId, long rabbitId, long targetCageId, String suffix) {
        return api.postResponse(
                "/api/rabbits/" + rabbitId + "/cage-transfer",
                owner.token,
                houseId,
                obj("targetCageId", targetCageId, "requestId", requestId(suffix))
        );
    }

    private long cageOf(long houseId, long rabbitId) {
        return jdbc.queryForObject(
                "select cage_id from rabbits where house_id = ? and id = ?",
                Long.class,
                houseId,
                rabbitId
        );
    }

    private void assertCage(long houseId, long cageId, int expectedCount, String expectedStatus) {
        Assertions.assertEquals(
                expectedCount,
                jdbc.queryForObject(
                        "select rabbit_count from cages where house_id = ? and id = ?",
                        Integer.class,
                        houseId,
                        cageId
                )
        );
        Assertions.assertEquals(
                expectedStatus,
                jdbc.queryForObject(
                        "select status from cages where house_id = ? and id = ?",
                        String.class,
                        houseId,
                        cageId
                )
        );
        Assertions.assertEquals(
                expectedCount,
                jdbc.queryForObject(
                        "select count(*) from rabbits where house_id = ? and cage_id = ? and is_active = true",
                        Integer.class,
                        houseId,
                        cageId
                )
        );
    }
}
