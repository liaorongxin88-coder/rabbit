package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

public class AppUpdateIT extends E2eTestSupport {
    @Test
    void publicCheckReturnsOnlyTheNewestPublishedAndroidBuild() {
        JsonNode current = api.getOk("/api/app-updates/check?buildNumber=4005", null, null);
        Assertions.assertFalse(current.get("updateAvailable").asBoolean());
        Assertions.assertEquals(4005, current.get("currentBuild").asLong());

        String adminToken = loginAdmin();
        JsonNode first = publish(adminToken, 4006, "1.0.5", false, "ota_first");
        JsonNode retry = publish(adminToken, 4006, "1.0.5", false, "ota_first");
        Assertions.assertEquals(first.get("id").asLong(), retry.get("id").asLong());
        JsonNode second = publish(adminToken, 4007, "1.0.6", true, "ota_second");

        JsonNode update = api.getOk("/api/app-updates/check?buildNumber=4005", null, null);
        Assertions.assertTrue(update.get("updateAvailable").asBoolean());
        Assertions.assertEquals(4007, update.get("buildNumber").asLong());
        Assertions.assertEquals("1.0.6", update.get("versionName").asText());
        Assertions.assertTrue(update.get("forceUpdate").asBoolean());
        Assertions.assertEquals(123456L, update.get("apkSizeBytes").asLong());
        Assertions.assertEquals("https://downloads.example.test/rabbit-4007.apk", update.get("downloadUrl").asText());
        Assertions.assertFalse(update.has("requestId"));

        api.putOk("/api/admin/app-updates/" + second.get("id").asLong() + "/status", adminToken, null, obj(
                "published", false,
                "requestId", requestId("ota_disable")
        ));
        JsonNode fallback = api.getOk("/api/app-updates/check?buildNumber=4005", null, null);
        Assertions.assertEquals(4006, fallback.get("buildNumber").asLong());

        JsonNode upToDate = api.getOk("/api/app-updates/check?buildNumber=4006", null, null);
        Assertions.assertFalse(upToDate.get("updateAvailable").asBoolean());
        Assertions.assertEquals(4006, upToDate.get("currentBuild").asLong());
    }

    @Test
    void catalogWritesRequirePlatformAdministrationButTheCheckDoesNotRequireLogin() {
        UserSession user = register("ota_business_user");
        api.expectError(
                "/api/admin/app-updates",
                HttpMethod.POST,
                user.token,
                null,
                releasePayload(4010, "1.1.0", false, requestId("ota_forbidden")),
                401,
                "后台未登录"
        );
        java.util.Map<String, Object> invalidUrl = releasePayload(
                4010,
                "1.1.0",
                false,
                requestId("ota_http")
        );
        invalidUrl.put("downloadUrl", "http://downloads.example.test/rabbit.apk");
        api.expectError(
                "/api/admin/app-updates",
                HttpMethod.POST,
                loginAdmin(),
                null,
                invalidUrl,
                400,
                "downloadUrl必须是HTTPS地址"
        );
    }

    private JsonNode publish(String adminToken, long buildNumber, String versionName, boolean forceUpdate, String requestId) {
        return api.postOk(
                "/api/admin/app-updates",
                adminToken,
                null,
                releasePayload(buildNumber, versionName, forceUpdate, requestId)
        );
    }

    private java.util.Map<String, Object> releasePayload(
            long buildNumber,
            String versionName,
            boolean forceUpdate,
            String requestId
    ) {
        return obj(
                "platform", "ANDROID",
                "buildNumber", buildNumber,
                "versionName", versionName,
                "downloadUrl", "https://downloads.example.test/rabbit-" + buildNumber + ".apk",
                "sha256", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "apkSizeBytes", 123456,
                "releaseNotes", "修复现场升级流程",
                "forceUpdate", forceUpdate,
                "requestId", requestId
        );
    }

    private String loginAdmin() {
        return api.postOk("/api/admin/auth/login", null, null, obj(
                "userName", "admin",
                "password", "admin123456"
        )).get("token").asText();
    }
}
