package com.rabbit.app.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

class BatchCarcassYieldIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void appendsHouseScopedVersionsWithPayloadSensitiveReplayAndAuditPermission() {
        UserSession owner = register("carcass_owner");
        UserSession staff = register("carcass_staff");
        UserSession viewer = register("carcass_viewer");
        long houseId = createHouse(owner, "出肉率来源兔舍", 1, 1, 1);
        long otherHouseId = createHouse(owner, "出肉率其他兔舍", 1, 1, 1);
        long batchId = createBatch(owner, houseId, "YIELD-A");
        addMember(owner, houseId, staff, "STAFF");
        addMember(owner, houseId, viewer, "VIEWER");
        String evidenceFileId = uploadTestImage(owner, houseId, "carcass-yield");
        String requestId = requestId("carcass_yield");
        Object body = request(requestId, "0.560000", evidenceFileId);
        String endpoint = endpoint(batchId);

        api.expectError(endpoint, HttpMethod.POST, viewer.token, houseId, body, 403, null);
        JsonNode created = api.postOk(endpoint, staff.token, houseId, body);
        JsonNode replayed = api.postOk(endpoint, staff.token, houseId, body);

        assertEquals(created.get("id").asLong(), replayed.get("id").asLong());
        assertEquals(houseId, created.get("houseId").asLong());
        assertEquals(batchId, created.get("batchId").asLong());
        assertEquals("0.56", created.get("yieldRate").decimalValue().stripTrailingZeros().toPlainString());
        assertEquals(evidenceFileId, created.get("evidenceFileId").asText());
        assertEquals(1, jdbc.queryForObject(
            "select count(*) from batch_carcass_yield_versions where house_id = ? and request_id = ?",
            Integer.class,
            houseId,
            requestId
        ));
        assertNotNull(jdbc.queryForObject(
            "select payload_hash from batch_carcass_yield_versions where house_id = ? and request_id = ?",
            String.class,
            houseId,
            requestId
        ));

        api.expectError(
            endpoint,
            HttpMethod.POST,
            staff.token,
            houseId,
            request(requestId, "0.570000", evidenceFileId),
            409,
            "requestId已用于不同的出肉率记录"
        );
        api.expectError(endpoint, HttpMethod.GET, staff.token, houseId, null, 403, null);

        JsonNode page = api.getOk(endpoint + "?page=1&pageSize=20", owner.token, houseId);
        assertEquals(1, page.get("total").asInt());
        assertEquals(1, page.get("items").size());
        assertEquals(created.get("id").asLong(), page.get("items").get(0).get("id").asLong());

        api.expectError(endpoint, HttpMethod.GET, owner.token, otherHouseId, null, 404, "批次不存在");
    }

    private long createBatch(UserSession owner, long houseId, String batchCode) {
        return api.postOk("/api/batches", owner.token, houseId, obj(
            "batchCode", batchCode,
            "femaleRabbitIds", List.of(),
            "requestId", requestId("carcass_batch")
        )).get("id").asLong();
    }

    private void addMember(
        UserSession owner,
        long houseId,
        UserSession member,
        String role
    ) {
        api.postOk("/api/house-members", owner.token, houseId, obj(
            "userName", member.userName,
            "role", role,
            "requestId", requestId("carcass_member")
        ));
    }

    private Object request(String requestId, String rate, String evidenceFileId) {
        return obj(
            "yieldRate", new BigDecimal(rate),
            "sourceUnit", "测试屠宰场",
            "measuredDate", LocalDate.of(2024, 8, 1).toString(),
            "reportNumber", "REPORT-001",
            "evidenceFileId", evidenceFileId,
            "changeReason", "首次录入",
            "requestId", requestId
        );
    }

    private static String endpoint(long batchId) {
        return "/api/batches/" + batchId + "/carcass-yields";
    }
}
