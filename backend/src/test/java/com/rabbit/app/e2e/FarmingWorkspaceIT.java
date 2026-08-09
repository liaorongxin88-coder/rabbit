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
        Assertions.assertFalse(workspace.has("ownerUserId"));
        Assertions.assertEquals("北区兔场", workspace.get("name").asText());
        Assertions.assertEquals("RABBIT", workspace.get("businessType").asText());
        Assertions.assertEquals("OWNER", workspace.get("role").asText());
        Assertions.assertFalse(workspace.has("merchantId"));
        Assertions.assertTrue(containsText(workspace.get("permissions"), "rabbit:rabbits:add"));
        Assertions.assertEquals("X-House-Id", workspace.get("scopeHeader").asText());
    }

    @Test
    void catalogOnlyExposesDirectlyAssociatedFarms() {
        UserSession firstOwner = register("workspace_first");
        UserSession secondOwner = register("workspace_second");
        UserSession outsider = register("workspace_outsider");
        long firstHouseId = createHouse(firstOwner, "第一兔场", 1, 1, 1);
        long secondHouseId = createHouse(secondOwner, "第二兔场", 1, 1, 1);
        long outsiderHouseId = createHouse(outsider, "外部兔场", 1, 1, 1);

        api.postOk("/api/house-members", firstOwner.token, firstHouseId, obj(
                "userName", secondOwner.userName,
                "role", "VIEWER",
                "requestId", requestId("workspace_viewer")
        ));

        JsonNode secondCatalog = api.getOk("/api/workspaces", secondOwner.token, null);

        Assertions.assertEquals(1, secondCatalog.get("modules").size());
        Assertions.assertEquals(2, secondCatalog.get("workspaces").size());
        Assertions.assertTrue(containsWorkspace(secondCatalog.get("workspaces"), firstHouseId));
        Assertions.assertTrue(containsWorkspace(secondCatalog.get("workspaces"), secondHouseId));
        Assertions.assertFalse(containsWorkspace(secondCatalog.get("workspaces"), outsiderHouseId));

        JsonNode firstCatalog = api.getOk("/api/workspaces", firstOwner.token, null);
        Assertions.assertEquals(1, firstCatalog.get("workspaces").size());
        Assertions.assertTrue(containsWorkspace(firstCatalog.get("workspaces"), firstHouseId));
        Assertions.assertFalse(containsWorkspace(firstCatalog.get("workspaces"), secondHouseId));
    }

    private boolean containsText(JsonNode values, String expected) {
        return StreamSupport.stream(values.spliterator(), false)
                .anyMatch(value -> expected.equals(value.asText()));
    }

    private boolean containsWorkspace(JsonNode workspaces, long resourceId) {
        return StreamSupport.stream(workspaces.spliterator(), false)
                .anyMatch(workspace -> resourceId == workspace.path("resourceId").asLong());
    }
}
