package com.rabbit.app.modules.apprelease.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.apprelease.dto.AppReleaseItem;
import com.rabbit.app.modules.apprelease.dto.AppReleasePage;
import com.rabbit.app.modules.apprelease.dto.AppUpdateCheckResponse;
import com.rabbit.app.modules.apprelease.entity.AppRelease;
import com.rabbit.app.modules.apprelease.mapper.AppReleaseMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AppReleaseService {
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String APK_CONTENT_TYPE = "application/vnd.android.package-archive";
    private static final Logger LOG = LoggerFactory.getLogger(AppReleaseService.class);
    private static final Set<String> CHANNELS = Set.of("dev", "test", "prod");
    private static final Set<String> STATUSES = Set.of(STATUS_DRAFT, STATUS_PUBLISHED, STATUS_REVOKED);

    private final AppReleaseMapper appReleaseMapper;
    private final Path storageRoot;
    private final long maxBytes;

    public AppReleaseService(
            AppReleaseMapper appReleaseMapper,
            @Value("${app.release.storage-path}") String storagePath,
            @Value("${app.release.max-bytes:157286400}") long maxBytes
    ) {
        this.appReleaseMapper = appReleaseMapper;
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        Path tmpRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        if (this.storageRoot.startsWith(tmpRoot)) {
            LOG.warn(
                    "APK 存储目录位于系统临时目录 {}，进程重启或临时目录清理后已发布的安装包会丢失",
                    this.storageRoot
            );
        }
    }

    public AppReleasePage list(String channel, String status, Integer pageNum, Integer pageSize) {
        String normalizedChannel = channel == null || channel.isBlank() ? null : normalizeChannel(channel);
        String normalizedStatus = status == null || status.isBlank() ? null : normalizeStatus(status);
        int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        long total = appReleaseMapper.count(normalizedChannel, normalizedStatus);
        List<AppReleaseItem> items = appReleaseMapper
                .selectPage(normalizedChannel, normalizedStatus, (page - 1) * size, size)
                .stream()
                .map(AppReleaseItem::from)
                .toList();
        return new AppReleasePage(items, total, page, size);
    }

    public AppReleaseItem create(
            String operator,
            String channel,
            String versionName,
            Integer versionCode,
            String releaseNotes,
            Boolean forceUpdate,
            String requestId,
            MultipartFile upload
    ) {
        String normalizedOperator = requireOperator(operator);
        String normalizedChannel = normalizeChannel(channel);
        String normalizedVersionName = normalizeVersionName(versionName);
        int normalizedVersionCode = normalizeVersionCode(versionCode);
        String normalizedNotes = normalizeNotes(releaseNotes);
        boolean normalizedForce = Boolean.TRUE.equals(forceUpdate);
        String normalizedRequestId = normalizeRequestId(requestId);

        AppRelease existing = appReleaseMapper.selectByOperatorAndRequestId(normalizedOperator, normalizedRequestId);
        if (existing != null) {
            long replaySize = existing.getSizeBytes() == null ? 0L : existing.getSizeBytes();
            String replaySha256 = existing.getSha256();
            if (upload != null && !upload.isEmpty()) {
                replaySize = upload.getSize();
                replaySha256 = hashUpload(upload);
            }
            return verifyIdempotentCreate(
                    existing,
                    normalizedChannel,
                    normalizedVersionName,
                    normalizedVersionCode,
                    normalizedNotes,
                    normalizedForce,
                    replaySize,
                    replaySha256
            );
        }

        String id = "apk_" + UUID.randomUUID().toString().replace("-", "");
        StoredApk stored = storeUpload(normalizedChannel, id, upload);
        try {
            AppRelease release = new AppRelease();
            release.setId(id);
            release.setChannel(normalizedChannel);
            release.setVersionName(normalizedVersionName);
            release.setVersionCode(normalizedVersionCode);
            release.setFileName(stored.fileName());
            release.setContentType(APK_CONTENT_TYPE);
            release.setSizeBytes(stored.sizeBytes());
            release.setSha256(stored.sha256());
            release.setStorageKey(stored.storageKey());
            release.setReleaseNotes(normalizedNotes);
            release.setForceUpdate(normalizedForce);
            release.setStatus(STATUS_DRAFT);
            release.setRequestId(normalizedRequestId);
            release.setCreateBy(normalizedOperator);
            release.setUpdateBy(normalizedOperator);
            appReleaseMapper.insert(release);
            return AppReleaseItem.from(require(id));
        } catch (DuplicateKeyException e) {
            deleteQuietly(resolveStorageKey(stored.storageKey()));
            AppRelease duplicate = appReleaseMapper.selectByOperatorAndRequestId(
                    normalizedOperator,
                    normalizedRequestId
            );
            if (duplicate != null) {
                return verifyIdempotentCreate(
                        duplicate,
                        normalizedChannel,
                        normalizedVersionName,
                        normalizedVersionCode,
                        normalizedNotes,
                        normalizedForce,
                        stored.sizeBytes(),
                        stored.sha256()
                );
            }
            throw new BizException(409, "该渠道已存在相同内部版本号");
        }
    }

    public AppReleaseItem publish(String operator, String id) {
        AppRelease release = require(id);
        if (STATUS_PUBLISHED.equals(release.getStatus())) {
            return AppReleaseItem.from(release);
        }
        if (!STATUS_DRAFT.equals(release.getStatus())) {
            throw new BizException(400, "只有草稿可以发布");
        }
        appReleaseMapper.updateStatus(id, STATUS_PUBLISHED, new Date(), requireOperator(operator));
        return AppReleaseItem.from(require(id));
    }

    public AppReleaseItem revoke(String operator, String id) {
        AppRelease release = require(id);
        if (STATUS_REVOKED.equals(release.getStatus())) {
            return AppReleaseItem.from(release);
        }
        appReleaseMapper.updateStatus(id, STATUS_REVOKED, release.getPublishedAt(), requireOperator(operator));
        return AppReleaseItem.from(require(id));
    }

    public AppReleaseItem updateMeta(String operator, String id, String releaseNotes, Boolean forceUpdate) {
        AppRelease release = require(id);
        if (STATUS_REVOKED.equals(release.getStatus())) {
            throw new BizException(400, "已撤回的版本不能再改");
        }
        String notes = releaseNotes == null ? release.getReleaseNotes() : normalizeNotes(releaseNotes);
        boolean force = forceUpdate == null ? Boolean.TRUE.equals(release.getForceUpdate()) : forceUpdate;
        appReleaseMapper.updateMeta(id, notes, force, requireOperator(operator));
        return AppReleaseItem.from(require(id));
    }

    public AppUpdateCheckResponse check(String channel, Integer versionCode) {
        String normalizedChannel = normalizeChannel(channel);
        int currentCode = normalizeVersionCode(versionCode);
        AppRelease latest = appReleaseMapper.selectLatestPublishedNewerThan(normalizedChannel, currentCode);
        if (latest == null) {
            return AppUpdateCheckResponse.none();
        }
        boolean force = appReleaseMapper.countForcedPublishedNewerThan(normalizedChannel, currentCode) > 0;
        return AppUpdateCheckResponse.of(AppReleaseItem.from(latest), force);
    }

    public StoredFile openPublished(String id) {
        AppRelease release = require(id);
        if (!STATUS_PUBLISHED.equals(release.getStatus())) {
            throw new BizException(404, "安装包不存在或尚未发布");
        }
        AppRelease latest = appReleaseMapper.selectLatestPublished(release.getChannel());
        if (latest == null || !Objects.equals(latest.getId(), id)) {
            throw new BizException(404, "安装包不存在或尚未发布");
        }
        return open(release);
    }

    public StoredFile openManaged(String id) {
        return open(require(id));
    }

    private StoredFile open(AppRelease release) {
        Path file = resolveStorageKey(release.getStorageKey());
        if (!Files.isRegularFile(file)) {
            throw new BizException(404, "安装包文件缺失");
        }
        return new StoredFile(
                release.getFileName(),
                release.getContentType(),
                release.getSizeBytes(),
                new FileSystemResource(file)
        );
    }

    private AppRelease require(String id) {
        if (id == null || id.isBlank()) {
            throw new BizException(400, "版本ID不能为空");
        }
        AppRelease release = appReleaseMapper.selectById(id.trim());
        if (release == null) {
            throw new BizException(404, "应用版本不存在");
        }
        return release;
    }

    private AppReleaseItem verifyIdempotentCreate(
            AppRelease existing,
            String channel,
            String versionName,
            int versionCode,
            String releaseNotes,
            boolean forceUpdate,
            long uploadSizeBytes,
            String uploadSha256
    ) {
        if (!Objects.equals(existing.getChannel(), channel)
                || !Objects.equals(existing.getVersionName(), versionName)
                || existing.getVersionCode() == null
                || existing.getVersionCode() != versionCode
                || !Objects.equals(existing.getReleaseNotes(), releaseNotes)
                || Boolean.TRUE.equals(existing.getForceUpdate()) != forceUpdate) {
            throw new BizException(409, "相同requestId不能用于不同的发布内容");
        }
        if (existing.getSizeBytes() == null
                || existing.getSizeBytes() != uploadSizeBytes
                || !Objects.equals(existing.getSha256(), uploadSha256)) {
            throw new BizException(409, "相同requestId不能用于不同的安装包");
        }
        return AppReleaseItem.from(existing);
    }

    private StoredApk storeUpload(String channel, String id, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new BizException(400, "请选择 APK 安装包");
        }
        if (upload.getSize() > maxBytes) {
            throw new BizException(400, "安装包不能超过 " + (maxBytes / (1024 * 1024)) + " MB");
        }
        String fileName = safeFileName(upload.getOriginalFilename());
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            throw new BizException(400, "只接受 .apk 安装包");
        }
        Path tempFile = null;
        try {
            Files.createDirectories(storageRoot);
            tempFile = Files.createTempFile(storageRoot, "upload-", ".tmp");
            MessageDigest digest = sha256Digest();
            long written;
            try (
                    InputStream in = upload.getInputStream();
                    DigestInputStream digestIn = new DigestInputStream(in, digest);
                    OutputStream out = Files.newOutputStream(tempFile)
            ) {
                written = digestIn.transferTo(out);
            }
            if (written <= 0 || written > maxBytes) {
                deleteQuietly(tempFile);
                throw new BizException(400, "安装包大小不合法");
            }
            if (!isZip(tempFile)) {
                deleteQuietly(tempFile);
                throw new BizException(400, "安装包格式不正确");
            }
            String sha256 = HexFormat.of().formatHex(digest.digest());
            String storageKey = channel + "/" + id + ".apk";
            Path destination = resolveStorageKey(storageKey);
            Files.createDirectories(destination.getParent());
            Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
            return new StoredApk(fileName, written, sha256, storageKey);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new BizException(400, "读取安装包失败");
        }
    }

    private static String hashUpload(MultipartFile upload) {
        try (InputStream in = upload.getInputStream()) {
            MessageDigest digest = sha256Digest();
            try (DigestInputStream digestIn = new DigestInputStream(in, digest)) {
                digestIn.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new BizException(400, "读取安装包失败");
        }
    }

    private Path resolveStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("..")) {
            throw new BizException(500, "安装包存储路径不合法");
        }
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new BizException(500, "安装包存储路径不合法");
        }
        return resolved;
    }

    private static boolean isZip(Path file) throws IOException {
        byte[] header = new byte[4];
        try (InputStream in = Files.newInputStream(file)) {
            if (in.read(header) < 4) {
                return false;
            }
        }
        return header[0] == 'P' && header[1] == 'K'
                && ((header[2] == 3 && header[3] == 4)
                || (header[2] == 5 && header[3] == 6)
                || (header[2] == 7 && header[3] == 8));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String safeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "app.apk";
        }
        String normalized = name.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return base.length() <= 255 ? base : base.substring(base.length() - 255);
    }

    private static String normalizeChannel(String channel) {
        String normalized = channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
        if (!CHANNELS.contains(normalized)) {
            throw new BizException(400, "渠道只能是 dev、test 或 prod");
        }
        return normalized;
    }

    private static String normalizeStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new BizException(400, "状态不合法");
        }
        return normalized;
    }

    private static String normalizeVersionName(String versionName) {
        String normalized = versionName == null ? "" : versionName.trim();
        if (normalized.isEmpty() || normalized.length() > 32 || !normalized.matches("[A-Za-z0-9._+-]+")) {
            throw new BizException(400, "版本号不合法");
        }
        return normalized;
    }

    private static int normalizeVersionCode(Integer versionCode) {
        if (versionCode == null || versionCode < 1) {
            throw new BizException(400, "内部版本号必须大于0");
        }
        return versionCode;
    }

    private static String normalizeNotes(String releaseNotes) {
        if (releaseNotes == null || releaseNotes.isBlank()) {
            return null;
        }
        String normalized = releaseNotes.trim();
        if (normalized.length() > 2000) {
            throw new BizException(400, "更新说明不能超过2000个字符");
        }
        return normalized;
    }

    private static String normalizeRequestId(String requestId) {
        String normalized = requestId == null ? "" : requestId.trim();
        if (normalized.isEmpty() || normalized.length() > 64 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new BizException(400, "requestId不合法");
        }
        return normalized;
    }

    private static String requireOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            throw new BizException(401, "后台未登录");
        }
        return operator;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 失败时留给磁盘清理，不阻断主流程。
        }
    }

    public record StoredFile(String fileName, String contentType, long sizeBytes, Resource resource) {
    }

    private record StoredApk(String fileName, long sizeBytes, String sha256, String storageKey) {
    }
}
