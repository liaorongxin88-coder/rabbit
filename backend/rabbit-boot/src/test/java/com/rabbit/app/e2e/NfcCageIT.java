package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

public class NfcCageIT extends E2eTestSupport {
    @Test
    void signedCageQueueBindingResolutionAndReplacementWorkEndToEnd() {
        UserSession owner = register("nfc_cage");
        UserSession viewer = register("nfc_viewer");
        long houseId = createHouse(owner, "NFC兔舍", 1, 2, 1);
        List<Long> cages = cageIds(owner, houseId);
        api.postOk("/api/house-members", owner.token, houseId, obj(
                "userName", viewer.userName,
                "perms", "view",
                "isAdmin", false,
                "requestId", requestId("nfc_viewer")
        ));
        api.expectError(
                "/api/nfc/cages/write-queue",
                HttpMethod.GET,
                viewer.token,
                houseId,
                null,
                403,
                "权限不足"
        );

        JsonNode queue = api.getOk("/api/nfc/cages/write-queue", owner.token, houseId);
        Assertions.assertEquals(2, queue.size());
        Assertions.assertEquals(cages.get(0).longValue(), queue.get(0).get("cageId").asLong());
        Assertions.assertEquals("UNBOUND", queue.get(0).get("bindingStatus").asText());
        String firstPayload = queue.get(0).get("payload").asText();
        String secondPayload = queue.get(1).get("payload").asText();
        Assertions.assertTrue(firstPayload.startsWith("r1."));

        String uid = "04A1B2C3D4E5F6";
        String bindRequestId = requestId("nfc_cage_bind");
        JsonNode bound = api.postOk("/api/nfc/cages/bind", owner.token, houseId, obj(
                "cageId", cages.get(0),
                "tagUid", uid,
                "payload", firstPayload,
                "replaceExisting", false,
                "requestId", bindRequestId
        ));
        Assertions.assertEquals("BOUND", bound.get("bindingStatus").asText());
        api.expectError("/api/nfc/cages/bind", HttpMethod.POST, viewer.token, houseId, obj(
                "cageId", cages.get(0),
                "tagUid", uid,
                "payload", firstPayload,
                "replaceExisting", false,
                "requestId", requestId("nfc_viewer_bind")
        ), 403, "权限不足");
        JsonNode replayed = api.postOk("/api/nfc/cages/bind", owner.token, houseId, obj(
                "cageId", cages.get(0),
                "tagUid", uid,
                "payload", firstPayload,
                "replaceExisting", false,
                "requestId", bindRequestId
        ));
        Assertions.assertEquals(cages.get(0).longValue(), replayed.get("cageId").asLong());

        JsonNode resolved = api.postOk("/api/nfc/cages/resolve", owner.token, houseId, obj(
                "payload", firstPayload,
                "tagUid", uid
        ));
        Assertions.assertEquals(cages.get(0).longValue(), resolved.get("cageId").asLong());
        JsonNode viewerResolved = api.postOk("/api/nfc/cages/resolve", viewer.token, houseId, obj(
                "payload", firstPayload,
                "tagUid", uid
        ));
        Assertions.assertEquals(cages.get(0).longValue(), viewerResolved.get("cageId").asLong());

        api.expectError("/api/nfc/cages/bind", HttpMethod.POST, owner.token, houseId, obj(
                "cageId", cages.get(1),
                "tagUid", uid,
                "payload", secondPayload,
                "replaceExisting", false,
                "requestId", requestId("nfc_cage_conflict")
        ), 4606, "已绑定");

        JsonNode replaced = api.postOk("/api/nfc/cages/bind", owner.token, houseId, obj(
                "cageId", cages.get(1),
                "tagUid", uid,
                "payload", secondPayload,
                "replaceExisting", true,
                "requestId", requestId("nfc_cage_replace")
        ));
        Assertions.assertEquals(cages.get(1).longValue(), replaced.get("cageId").asLong());

        api.expectError("/api/nfc/cages/resolve", HttpMethod.POST, owner.token, houseId, obj(
                "payload", firstPayload,
                "tagUid", uid
        ), 4604, "不一致");

        char last = secondPayload.charAt(secondPayload.length() - 1);
        String tampered = secondPayload.substring(0, secondPayload.length() - 1) + (last == 'A' ? "B" : "A");
        api.expectError("/api/nfc/cages/resolve", HttpMethod.POST, owner.token, houseId, obj(
                "payload", tampered,
                "tagUid", uid
        ), 4602, "签名无效");
    }
}
