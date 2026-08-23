package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

class IndividualRabbitSaleIT extends E2eTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void sellsBreedersAndReplacementWithLifecycleCleanupIdempotencyAndPermissions() {
        UserSession owner = register("individual_sale_owner");
        UserSession viewer = register("individual_sale_viewer");
        long houseId = createHouse(owner, "individual_sale_house", 1, 4, 1);
        List<Long> cages = cageIds(owner, houseId);
        long doeId = createRabbit(owner, houseId, cages.get(0), "0", "0", "sale_doe");
        long replacementId = createRabbit(owner, houseId, cages.get(1), "1", "0", "sale_replacement");
        long buckId = createRabbit(owner, houseId, cages.get(2), "0", "1", "sale_buck");

        JsonNode batch = api.postOk("/api/batches", owner.token, houseId, obj(
                "batchCode", "SALE-" + requestId("batch").substring(0, 8),
                "femaleRabbitIds", List.of(doeId, replacementId),
                "requestId", requestId("sale_batch")
        ));
        long batchId = batch.get("id").asLong();
        Assertions.assertEquals(2, jdbc.queryForObject(
                "select count(*) from batch_rabbits where batch_id = ? and is_active = true",
                Integer.class,
                batchId
        ));
        Assertions.assertEquals(2, jdbc.queryForObject(
                "select count(*) from breeding_cycles where house_id = ? and mother_rabbit_id in (?, ?) and lifecycle = 'OPEN'",
                Integer.class,
                houseId,
                doeId,
                replacementId
        ));

        api.postOk("/api/house-members", owner.token, houseId, obj(
                "userName", viewer.userName,
                "perms", "view",
                "isAdmin", false,
                "requestId", requestId("sale_viewer")
        ));
        api.expectError("/api/sales", HttpMethod.POST, viewer.token, houseId,
                saleRequest(buckId, now(), requestId("viewer_sale")), 403, "权限不足");
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from rabbits where id = ? and is_active = true",
                Integer.class,
                buckId
        ));

        long saleTime = now();
        String doeRequestId = requestId("sale_doe");
        JsonNode doeSale = api.postOk("/api/sales", owner.token, houseId,
                saleRequest(doeId, saleTime, doeRequestId));
        JsonNode replayedDoeSale = api.postOk("/api/sales", owner.token, houseId,
                saleRequest(doeId, saleTime, doeRequestId));
        Assertions.assertEquals(doeSale.get("id").asLong(), replayedDoeSale.get("id").asLong());
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from sale_orders where house_id = ? and request_id = ?",
                Integer.class,
                houseId,
                doeRequestId
        ));
        assertSold(houseId, doeId, cages.get(0));
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from batch_rabbits where batch_id = ? and is_active = true",
                Integer.class,
                batchId
        ));
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from breeding_cycles where house_id = ? and mother_rabbit_id = ? and lifecycle = 'OPEN'",
                Integer.class,
                houseId,
                doeId
        ));
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from work_tasks where house_id = ? and rabbit_id = ? and status = 'PENDING'",
                Integer.class,
                houseId,
                doeId
        ));

        api.postOk("/api/sales", owner.token, houseId,
                saleRequest(replacementId, now(), requestId("sale_replacement")));
        api.postOk("/api/sales", owner.token, houseId,
                saleRequest(buckId, now(), requestId("sale_buck")));

        assertSold(houseId, replacementId, cages.get(1));
        assertSold(houseId, buckId, cages.get(2));
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from batch_rabbits where batch_id = ? and is_active = true",
                Integer.class,
                batchId
        ));
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from breeding_cycles where house_id = ? and mother_rabbit_id in (?, ?) and lifecycle = 'OPEN'",
                Integer.class,
                houseId,
                doeId,
                replacementId
        ));
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from work_tasks where house_id = ? and rabbit_id in (?, ?) and status = 'PENDING'",
                Integer.class,
                houseId,
                doeId,
                replacementId
        ));
    }

    private Object saleRequest(long rabbitId, long saleTime, String requestId) {
        return obj(
                "rabbitIds", List.of(rabbitId),
                "saleTime", saleTime,
                "totalWeight", 3.2,
                "unitPrice", 20,
                "customer", "e2e customer",
                "remark", "individual sale",
                "requestId", requestId
        );
    }

    private void assertSold(long houseId, long rabbitId, long cageId) {
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from rabbits where id = ? and is_active = true",
                Integer.class,
                rabbitId
        ));
        Assertions.assertEquals("sale", jdbc.queryForObject(
                "select departure_reason from rabbits where id = ?",
                String.class,
                rabbitId
        ));
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from sale_order_items where rabbit_id = ?",
                Integer.class,
                rabbitId
        ));
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from rabbit_departure_records where house_id = ? and rabbit_id = ? and departure_type = 'sale'",
                Integer.class,
                houseId,
                rabbitId
        ));
        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from rabbit_status_history where house_id = ? and rabbit_id = ? and to_status = '出售出栏'",
                Integer.class,
                houseId,
                rabbitId
        ));
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select rabbit_count from cages where house_id = ? and id = ?",
                Integer.class,
                houseId,
                cageId
        ));
        Assertions.assertEquals("0", jdbc.queryForObject(
                "select status from cages where house_id = ? and id = ?",
                String.class,
                houseId,
                cageId
        ));
    }
}
