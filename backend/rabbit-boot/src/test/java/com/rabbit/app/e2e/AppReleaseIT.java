package com.rabbit.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public class AppReleaseIT extends E2eTestSupport {
    @Test
    void adminPublishesAnApkAndTheClientCanCheckAndDownloadWithoutLogin() {
        api.expectError(
                "/api/admin/app-releases?pageNum=1&pageSize=20",
                HttpMethod.GET,
                null,
                null,
                null,
                401,
                "后台未登录"
        );

        UserSession businessUser = register("ota_user");
        api.expectError(
                "/api/admin/app-releases?pageNum=1&pageSize=20",
                HttpMethod.GET,
                businessUser.token,
                null,
                null,
                401,
                "后台未登录"
        );

        JsonNode emptyCheck = api.getOk("/api/app/updates/check?channel=prod&versionCode=4003", null, null);
        Assertions.assertFalse(emptyCheck.get("hasUpdate").asBoolean());

        String adminToken = loginAdmin();
        byte[] apk = zipBytes();
        JsonNode created = uploadRelease(adminToken, "prod", "1.0.3", 4004, "修了登录", false, "ota_create", apk);
        Assertions.assertEquals("DRAFT", created.get("status").asText());
        Assertions.assertEquals("prod", created.get("channel").asText());
        Assertions.assertEquals(4004, created.get("versionCode").asInt());

        JsonNode stillDraft = api.getOk("/api/app/updates/check?channel=prod&versionCode=4003", null, null);
        Assertions.assertFalse(stillDraft.get("hasUpdate").asBoolean());
        api.expectError(
                "/api/app/updates/" + created.get("id").asText() + "/apk",
                HttpMethod.GET,
                null,
                null,
                null,
                404,
                "尚未发布"
        );

        JsonNode published = api.postOk(
                "/api/admin/app-releases/" + created.get("id").asText() + "/publish",
                adminToken,
                null,
                obj()
        );
        Assertions.assertEquals("PUBLISHED", published.get("status").asText());

        JsonNode update = api.getOk("/api/app/updates/check?channel=prod&versionCode=4003", null, null);
        Assertions.assertTrue(update.get("hasUpdate").asBoolean());
        Assertions.assertFalse(update.get("forceUpdate").asBoolean());
        Assertions.assertEquals("1.0.3", update.get("versionName").asText());
        Assertions.assertEquals("/api/app/updates/" + created.get("id").asText() + "/apk", update.get("downloadPath").asText());

        E2eApiClient.Download download = api.download(update.get("downloadPath").asText(), null, null);
        Assertions.assertArrayEquals(apk, download.bytes);
        Assertions.assertTrue(
                download.cacheControl != null && download.cacheControl.contains("no-store"),
                "public APK download must not be cached"
        );

        api.postOk(
                "/api/admin/app-releases/" + created.get("id").asText() + "/revoke",
                adminToken,
                null,
                obj()
        );
        JsonNode afterRevoke = api.getOk("/api/app/updates/check?channel=prod&versionCode=4003", null, null);
        Assertions.assertFalse(afterRevoke.get("hasUpdate").asBoolean());
        api.expectError(
                "/api/app/updates/" + created.get("id").asText() + "/apk",
                HttpMethod.GET,
                null,
                null,
                null,
                404,
                "尚未发布"
        );
    }

    @Test
    void sameAdminRequestIdDoesNotCreateASecondRelease() {
        String adminToken = loginAdmin();
        byte[] apk = zipBytes();
        String requestId = requestId("ota_idem");
        JsonNode first = uploadRelease(adminToken, "test", "1.0.3-test", 21, "测试包", true, requestId, apk);
        JsonNode replay = uploadRelease(adminToken, "test", "1.0.3-test", 21, "测试包", true, requestId, apk);
        Assertions.assertEquals(first.get("id").asText(), replay.get("id").asText());

        JsonNode listed = api.getOk("/api/admin/app-releases?pageNum=1&pageSize=20&channel=test", adminToken, null);
        Assertions.assertEquals(1, listed.get("total").asLong());
    }

    private JsonNode uploadRelease(
            String adminToken,
            String channel,
            String versionName,
            int versionCode,
            String releaseNotes,
            boolean forceUpdate,
            String requestId,
            byte[] apk
    ) {
        MultiValueMap<String, Object> fields = new LinkedMultiValueMap<>();
        fields.add("channel", channel);
        fields.add("versionName", versionName);
        fields.add("versionCode", String.valueOf(versionCode));
        fields.add("releaseNotes", releaseNotes);
        fields.add("forceUpdate", String.valueOf(forceUpdate));
        fields.add("requestId", requestId);
        fields.add("file", new ByteArrayResource(apk) {
            @Override
            public String getFilename() {
                return "rabbit-" + versionName + ".apk";
            }
        });
        return api.uploadMultipart("/api/admin/app-releases", adminToken, null, fields);
    }

    private String loginAdmin() {
        JsonNode auth = api.postOk("/api/admin/auth/login", null, null, obj(
                "userName", "admin",
                "password", "admin123456"
        ));
        return auth.get("token").asText();
    }

    private static byte[] zipBytes() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zip.write("dummy".getBytes());
            zip.closeEntry();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return buffer.toByteArray();
    }
}
