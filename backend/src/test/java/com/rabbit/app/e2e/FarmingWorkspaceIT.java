package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FarmingWorkspaceIT extends E2eTestSupport {
    @Test
    void exposesRabbitHousesThroughTheGenericWorkspaceContract() {
        UserSession owner = register("workspace_owner");
        long houseId = createHouse(owner, "北区兔场", 1, 1, 1);

        JsonNode catalog = api.getOk("/api/workspaces", owner.token, null);

        Assertions.assertEquals(1, catalog.get("modules").size());
        JsonNode module = catalog.get("modules").get(0);
        Assertions.assertEquals("RABBIT", module.get("code").asText());
        Assertions.assertEquals("兔养殖", module.get("displayName").asText());
        Assertions.assertTrue(containsText(module.get("capabilities"), "animal-records"));
        Assertions.assertFalse(containsText(module.get("capabilities"), "hardware"));
        Assertions.assertFalse(containsText(module.get("capabilities"), "nfc"));

        Assertions.assertEquals(1, catalog.get("workspaces").size());
        JsonNode workspace = catalog.get("workspaces").get(0);
        Assertions.assertEquals("RABBIT:" + houseId, workspace.get("workspaceKey").asText());
        Assertions.assertEquals(houseId, workspace.get("resourceId").asLong());
        Assertions.assertEquals(owner.userId, workspace.get("ownerUserId").asLong());
        Assertions.assertEquals("北区兔场", workspace.get("name").asText());
        Assertions.assertEquals("RABBIT", workspace.get("businessType").asText());
        Assertions.assertEquals("MERCHANT_OWNER", workspace.get("role").asText());
        Assertions.assertTrue(containsText(workspace.get("permissions"), "rabbit:rabbits:add"));
        Assertions.assertEquals("X-House-Id", workspace.get("scopeHeader").asText());
    }

    @Test
    void merchantFilterCannotExposeAnotherTenantsWorkspace() {
        UserSession firstOwner = register("workspace_first");
        createHouse(firstOwner, "第一商户兔场", 1, 1, 1);
        JsonNode firstCatalog = api.getOk("/api/workspaces", firstOwner.token, null);
        long firstMerchantId = firstCatalog.get("workspaces").get(0).get("merchantId").asLong();

        UserSession secondOwner = register("workspace_second");
        createHouse(secondOwner, "第二商户兔场", 1, 1, 1);

        JsonNode filtered = api.getOk(
                "/api/workspaces?merchantId=" + firstMerchantId,
                secondOwner.token,
                null
        );

        Assertions.assertEquals(1, filtered.get("modules").size());
        Assertions.assertTrue(filtered.get("workspaces").isEmpty());
    }

    private boolean containsText(JsonNode values, String expected) {
        return StreamSupport.stream(values.spliterator(), false)
                .anyMatch(value -> expected.equals(value.asText()));
    }
}
