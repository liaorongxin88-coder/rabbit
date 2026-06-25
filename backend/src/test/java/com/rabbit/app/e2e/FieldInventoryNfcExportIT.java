package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;

public class FieldInventoryNfcExportIT extends E2eTestSupport {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void fieldRecordsInventorySalesNfcAndCsvExportsWorkEndToEnd() {
        UserSession owner = register("field");
        long houseId = createHouse(owner, "field_house", 1, 5, 1);
        List<Long> cages = cageIds(owner, houseId);
        long rabbitId = createRabbit(owner, houseId, cages.get(0), "0", "0", "field_female");
        long saleRabbitId = createRabbit(owner, houseId, cages.get(1), "2", "1", "sale_rabbit");

        api.postOk("/api/feed-logs", owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(rabbitId),
                "feedTime", now(),
                "feedType", "pellet",
                "unit", "kg",
                "amount", 1.5,
                "remark", "e2e feed",
                "requestId", requestId("feed")
        ));
        JsonNode feeds = api.getOk("/api/feed-logs", owner.token, houseId);
        Assertions.assertTrue(feeds.size() >= 1);

        api.postOk("/api/weight-logs", owner.token, houseId, obj(
                "rabbitId", rabbitId,
                "weighTime", now(),
                "weightKg", 3.45,
                "remark", "e2e weight",
                "requestId", requestId("weight")
        ));
        Assertions.assertTrue(api.getOk("/api/weight-logs?rabbitId=" + rabbitId, owner.token, houseId).size() >= 1);

        JsonNode treatment = api.postOk("/api/treatments", owner.token, houseId, obj(
                "rabbitId", rabbitId,
                "startDate", now(),
                "diagnosis", "e2e diagnosis",
                "drug", "safe drug",
                "dose", "1ml",
                "days", 1,
                "nextReviewDate", now() - 1000,
                "remark", "e2e treatment",
                "requestId", requestId("treatment")
        ));
        long treatmentId = treatment.get("id").asLong();
        Assertions.assertTrue(api.getOk("/api/treatments?rabbitId=" + rabbitId, owner.token, houseId).size() >= 1);
        api.postOk("/api/treatments/" + treatmentId + "/complete", owner.token, houseId, obj(
                "completeTime", now(),
                "remark", "done",
                "requestId", requestId("treatment_complete")
        ));

        jdbcTemplate.update("insert into rabbit_abnormal_conditions (rabbit_id, house_id, warning_status, warning_time, is_deal, remark, create_by, update_by) values (?, ?, ?, now(), 0, ?, ?, ?)",
                rabbitId, houseId, "体温异常", "e2e abnormal", String.valueOf(owner.userId), String.valueOf(owner.userId));
        JsonNode abnormal = api.getOk("/api/abnormal?isDeal=false", owner.token, houseId);
        Assertions.assertTrue(abnormal.size() >= 1);
        long abnormalId = abnormal.get(0).get("id").asLong();
        api.postOk("/api/abnormal/" + abnormalId + "/deal", owner.token, houseId, obj(
                "deal", true,
                "requestId", requestId("abnormal_deal")
        ));
        Assertions.assertTrue(api.getOk("/api/abnormal?isDeal=true", owner.token, houseId).size() >= 1);

        JsonNode item = api.postOk("/api/inventory/items", owner.token, houseId, obj(
                "name", "饲料-" + requestId("item").substring(0, 8),
                "unit", "kg",
                "initQty", 10,
                "lowStockQty", 2,
                "remark", "e2e inventory",
                "requestId", requestId("item")
        ));
        long itemId = item.get("id").asLong();
        api.postOk("/api/inventory/txs", owner.token, houseId, obj(
                "itemId", itemId,
                "txType", "OUT",
                "qtyDelta", -3,
                "txTime", now(),
                "remark", "consume",
                "requestId", requestId("stock_out")
        ));
        JsonNode txs = api.getOk("/api/inventory/txs?itemId=" + itemId, owner.token, houseId);
        Assertions.assertTrue(txs.size() >= 2, "initial and OUT transactions should be present");
        api.expectError("/api/inventory/txs", HttpMethod.POST, owner.token, houseId, obj(
                "itemId", itemId,
                "txType", "OUT",
                "qtyDelta", -999,
                "txTime", now(),
                "remark", "too much",
                "requestId", requestId("stock_negative")
        ), 400, "库存不足");

        JsonNode order = api.postOk("/api/sales", owner.token, houseId, obj(
                "rabbitIds", Arrays.asList(saleRabbitId),
                "saleTime", now(),
                "totalWeight", 2.5,
                "unitPrice", 20,
                "customer", "e2e customer",
                "remark", "e2e sale",
                "requestId", requestId("sale_order")
        ));
        long orderId = order.get("id").asLong();
        JsonNode detail = api.getOk("/api/sales/" + orderId, owner.token, houseId);
        Assertions.assertEquals("e2e customer", detail.get("order").get("customer").asText());
        Assertions.assertEquals(1, detail.get("items").size());

        String tagUid = "E2E" + requestId("tag").substring(0, 8).toUpperCase();
        api.postOk("/api/nfc/tags", owner.token, houseId, obj(
                "tagUid", tagUid,
                "targetType", "CAGE",
                "targetId", cages.get(0),
                "remark", "e2e nfc",
                "requestId", requestId("nfc")
        ));
        JsonNode resolved = api.getOk("/api/nfc/resolve?tagUid=" + tagUid, owner.token, houseId);
        Assertions.assertEquals("CAGE", resolved.get("targetType").asText());
        Assertions.assertEquals(cages.get(0).longValue(), resolved.get("targetId").asLong());

        E2eApiClient.Download feedCsv = api.download("/api/reports/feed-logs.csv?maxRows=10", owner.token, houseId);
        Assertions.assertEquals("text", feedCsv.contentType.getType());
        Assertions.assertTrue(feedCsv.utf8().contains("id,feed_time,feed_type,amount,feeding_rabbits,remark"));
        Assertions.assertTrue(feedCsv.utf8().contains("e2e feed"));

        E2eApiClient.Download itemsCsv = api.download("/api/inventory/items.csv", owner.token, houseId);
        Assertions.assertTrue(itemsCsv.utf8().contains("id,name,unit,current_qty"));
        Assertions.assertTrue(itemsCsv.utf8().contains("e2e inventory"));

        E2eApiClient.Download txCsv = api.download("/api/inventory/txs.csv?itemId=" + itemId + "&maxRows=10", owner.token, houseId);
        Assertions.assertTrue(txCsv.utf8().contains("item_id,item_name,id,tx_time,tx_type"));
        Assertions.assertTrue(txCsv.utf8().contains("consume"));

        E2eApiClient.Download auditCsv = api.download("/api/audit-logs.csv?maxRows=100", owner.token, houseId);
        Assertions.assertTrue(auditCsv.utf8().contains("id,create_time,trace_id,user_id,house_id"));
    }
}
