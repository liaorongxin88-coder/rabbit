package com.rabbit.app.modules.apprelease.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.apprelease.dto.AppReleaseItem;
import com.rabbit.app.modules.apprelease.dto.AppUpdateCheckResponse;
import com.rabbit.app.modules.apprelease.entity.AppRelease;
import com.rabbit.app.modules.apprelease.mapper.AppReleaseMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class AppReleaseServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void publishedNewerReleaseIsVisibleToTheClientCheck() {
        AppReleaseService service = service();
        AppReleaseItem created = service.create(
                "1",
                "prod",
                "1.0.3",
                4004,
                "修了登录",
                false,
                "req-1",
                apk("rabbit-1.0.3.apk")
        );

        AppUpdateCheckResponse beforePublish = service.check("prod", 4003);
        assertFalse(beforePublish.isHasUpdate());

        service.publish("1", created.getId());
        AppUpdateCheckResponse update = service.check("prod", 4003);
        assertTrue(update.isHasUpdate());
        assertFalse(update.isForceUpdate());
        assertEquals("1.0.3", update.getVersionName());
        assertEquals(4004, update.getVersionCode());
        assertEquals("/api/app/updates/" + created.getId() + "/apk", update.getDownloadPath());
        assertFalse(service.check("prod", 4004).isHasUpdate());
    }

    @Test
    void anyForcedPublishedReleaseBetweenClientAndLatestRequiresForceUpdate() {
        AppReleaseService service = service();
        AppReleaseItem forced = service.create(
                "1", "prod", "1.0.3", 4004, "必须升级", true, "req-force", apk("force.apk")
        );
        AppReleaseItem latest = service.create(
                "1", "prod", "1.0.4", 4005, "再修一刀", false, "req-latest", apk("latest.apk")
        );
        service.publish("1", forced.getId());
        service.publish("1", latest.getId());

        AppUpdateCheckResponse update = service.check("prod", 4003);
        assertTrue(update.isHasUpdate());
        assertTrue(update.isForceUpdate());
        assertEquals(4005, update.getVersionCode());
    }

    @Test
    void revokeHidesThePackageFromPublicCheckAndDownload() {
        AppReleaseService service = service();
        AppReleaseItem created = service.create(
                "1", "prod", "1.0.3", 4004, "先发后撤", false, "req-revoke", apk("revoke.apk")
        );
        service.publish("1", created.getId());
        service.revoke("1", created.getId());

        assertFalse(service.check("prod", 4003).isHasUpdate());
        BizException error = assertThrows(BizException.class, () -> service.openPublished(created.getId()));
        assertEquals(404, error.getCode());
    }

    @Test
    void publicDownloadOnlyServesTheLatestPublishedPackage() {
        AppReleaseService service = service();
        AppReleaseItem older = service.create(
                "1", "prod", "1.0.3", 4004, "旧包", false, "req-old", apk("old.apk")
        );
        AppReleaseItem newer = service.create(
                "1", "prod", "1.0.4", 4005, "新包", false, "req-new", apk("new.apk")
        );
        service.publish("1", older.getId());
        service.publish("1", newer.getId());

        assertEquals("/api/app/updates/" + newer.getId() + "/apk", service.check("prod", 4003).getDownloadPath());
        service.openPublished(newer.getId());
        BizException error = assertThrows(BizException.class, () -> service.openPublished(older.getId()));
        assertEquals(404, error.getCode());
    }

    @Test
    void sameRequestIdReplaysTheOriginalDraft() {
        AppReleaseService service = service();
        AppReleaseItem first = service.create(
                "1", "dev", "1.0.3-dev", 11, "草稿", false, "same-req", apk("first.apk")
        );
        AppReleaseItem replay = service.create(
                "1", "dev", "1.0.3-dev", 11, "草稿", false, "same-req", apk("first.apk")
        );
        assertEquals(first.getId(), replay.getId());
        assertEquals(AppReleaseService.STATUS_DRAFT, replay.getStatus());
    }

    @Test
    void sameRequestIdRejectsADifferentPackageEvenWhenTheSizeMatches() {
        AppReleaseService service = service();
        byte[] original = zipBytes();
        byte[] mutated = original.clone();
        mutated[10] ^= 1;
        service.create(
                "1",
                "dev",
                "1.0.3-dev",
                12,
                "草稿",
                false,
                "same-hash",
                new MockMultipartFile("file", "same-size.apk", AppReleaseService.APK_CONTENT_TYPE, original)
        );
        BizException error = assertThrows(
                BizException.class,
                () -> service.create(
                        "1",
                        "dev",
                        "1.0.3-dev",
                        12,
                        "草稿",
                        false,
                        "same-hash",
                        new MockMultipartFile("file", "same-size.apk", AppReleaseService.APK_CONTENT_TYPE, mutated)
                )
        );
        assertEquals(409, error.getCode());
    }

    @Test
    void insertRaceReplaysTheOriginalDraftWithoutRereadingTheUpload() {
        RaceOnInsertMapper mapper = new RaceOnInsertMapper();
        AppReleaseService service = new AppReleaseService(mapper, tempDir.toString(), 1024 * 1024);
        AppReleaseItem created = service.create(
                "1", "dev", "1.0.3-dev", 13, "草稿", false, "race-req", apk("race.apk")
        );
        assertEquals(AppReleaseService.STATUS_DRAFT, created.getStatus());
        assertEquals(1, mapper.insertAttempts);
    }

    @Test
    void rejectsNonApkPayloads() {
        AppReleaseService service = service();
        BizException typeError = assertThrows(
                BizException.class,
                () -> service.create("1", "prod", "1.0.3", 1, null, false, "req-txt", textFile())
        );
        assertEquals(400, typeError.getCode());

        BizException zipError = assertThrows(
                BizException.class,
                () -> service.create(
                        "1",
                        "prod",
                        "1.0.3",
                        1,
                        null,
                        false,
                        "req-fake",
                        new MockMultipartFile("file", "app.apk", "application/octet-stream", "not-a-zip".getBytes())
                )
        );
        assertEquals(400, zipError.getCode());
    }

    private AppReleaseService service() {
        return new AppReleaseService(new FakeAppReleaseMapper(), tempDir.toString(), 1024 * 1024);
    }

    private static MockMultipartFile apk(String fileName) {
        return new MockMultipartFile("file", fileName, AppReleaseService.APK_CONTENT_TYPE, zipBytes());
    }

    private static MockMultipartFile textFile() {
        return new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
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

    private static class FakeAppReleaseMapper implements AppReleaseMapper {
        private final List<AppRelease> items = new ArrayList<>();

        @Override
        public int insert(AppRelease release) {
            if (selectByOperatorAndRequestId(release.getCreateBy(), release.getRequestId()) != null
                    || selectByChannelAndVersionCode(release.getChannel(), release.getVersionCode()) != null) {
                throw new org.springframework.dao.DuplicateKeyException("duplicate");
            }
            AppRelease copy = copy(release);
            copy.setCreateTime(new Date());
            copy.setUpdateTime(copy.getCreateTime());
            items.add(copy);
            return 1;
        }

        @Override
        public AppRelease selectById(String id) {
            return items.stream().filter(item -> item.getId().equals(id)).findFirst().map(this::copy).orElse(null);
        }

        @Override
        public AppRelease selectByOperatorAndRequestId(String createBy, String requestId) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getCreateBy(), createBy)
                            && Objects.equals(item.getRequestId(), requestId))
                    .findFirst()
                    .map(this::copy)
                    .orElse(null);
        }

        @Override
        public AppRelease selectByChannelAndVersionCode(String channel, int versionCode) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getChannel(), channel)
                            && Objects.equals(item.getVersionCode(), versionCode))
                    .findFirst()
                    .map(this::copy)
                    .orElse(null);
        }

        @Override
        public AppRelease selectLatestPublished(String channel) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getChannel(), channel)
                            && AppReleaseService.STATUS_PUBLISHED.equals(item.getStatus()))
                    .max(Comparator.comparingInt(AppRelease::getVersionCode))
                    .map(this::copy)
                    .orElse(null);
        }

        @Override
        public AppRelease selectLatestPublishedNewerThan(String channel, int versionCode) {
            return items.stream()
                    .filter(item -> Objects.equals(item.getChannel(), channel)
                            && AppReleaseService.STATUS_PUBLISHED.equals(item.getStatus())
                            && item.getVersionCode() > versionCode)
                    .max(Comparator.comparingInt(AppRelease::getVersionCode))
                    .map(this::copy)
                    .orElse(null);
        }

        @Override
        public int countForcedPublishedNewerThan(String channel, int versionCode) {
            return (int) items.stream()
                    .filter(item -> Objects.equals(item.getChannel(), channel)
                            && AppReleaseService.STATUS_PUBLISHED.equals(item.getStatus())
                            && Boolean.TRUE.equals(item.getForceUpdate())
                            && item.getVersionCode() > versionCode)
                    .count();
        }

        @Override
        public long count(String channel, String status) {
            return items.stream().filter(item -> matches(item, channel, status)).count();
        }

        @Override
        public List<AppRelease> selectPage(String channel, String status, int offset, int limit) {
            return items.stream()
                    .filter(item -> matches(item, channel, status))
                    .sorted(Comparator.comparing(AppRelease::getVersionCode).reversed())
                    .skip(offset)
                    .limit(limit)
                    .map(this::copy)
                    .toList();
        }

        @Override
        public int updateStatus(String id, String status, Date publishedAt, String updateBy) {
            for (AppRelease item : items) {
                if (item.getId().equals(id)) {
                    item.setStatus(status);
                    item.setPublishedAt(publishedAt);
                    item.setUpdateBy(updateBy);
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public int updateMeta(String id, String releaseNotes, boolean forceUpdate, String updateBy) {
            for (AppRelease item : items) {
                if (item.getId().equals(id)) {
                    item.setReleaseNotes(releaseNotes);
                    item.setForceUpdate(forceUpdate);
                    item.setUpdateBy(updateBy);
                    return 1;
                }
            }
            return 0;
        }

        private boolean matches(AppRelease item, String channel, String status) {
            return (channel == null || channel.equals(item.getChannel()))
                    && (status == null || status.equals(item.getStatus()));
        }

        private AppRelease copy(AppRelease source) {
            AppRelease copy = new AppRelease();
            copy.setId(source.getId());
            copy.setChannel(source.getChannel());
            copy.setVersionName(source.getVersionName());
            copy.setVersionCode(source.getVersionCode());
            copy.setFileName(source.getFileName());
            copy.setContentType(source.getContentType());
            copy.setSizeBytes(source.getSizeBytes());
            copy.setSha256(source.getSha256());
            copy.setStorageKey(source.getStorageKey());
            copy.setReleaseNotes(source.getReleaseNotes());
            copy.setForceUpdate(source.getForceUpdate());
            copy.setStatus(source.getStatus());
            copy.setRequestId(source.getRequestId());
            copy.setPublishedAt(source.getPublishedAt());
            copy.setCreateBy(source.getCreateBy());
            copy.setCreateTime(source.getCreateTime());
            copy.setUpdateBy(source.getUpdateBy());
            copy.setUpdateTime(source.getUpdateTime());
            return copy;
        }
    }

    private static class RaceOnInsertMapper extends FakeAppReleaseMapper {
        int insertAttempts;

        @Override
        public int insert(AppRelease release) {
            insertAttempts++;
            super.insert(release);
            throw new org.springframework.dao.DuplicateKeyException("race");
        }
    }
}
