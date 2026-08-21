package com.rabbit.app.modules.file.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.file.dto.BusinessFileUploadResponse;
import com.rabbit.app.modules.file.entity.BusinessFile;
import com.rabbit.app.modules.file.mapper.BusinessFileMapper;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BusinessFileService {
    public static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    public static final int MAX_FILES_PER_ACTION = 6;

    private final BusinessFileMapper businessFileMapper;

    public BusinessFileService(BusinessFileMapper businessFileMapper) {
        this.businessFileMapper = businessFileMapper;
    }

    public BusinessFileUploadResponse storeImage(Long houseId, Long userId, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new BizException(400, "请选择图片");
        }
        if (upload.getSize() > MAX_IMAGE_BYTES) {
            throw new BizException(400, "单张图片不能超过 5 MB");
        }
        byte[] content;
        try {
            content = upload.getBytes();
        } catch (IOException e) {
            throw new BizException(400, "读取图片失败");
        }
        String contentType = detectImageType(content);
        String sha256 = sha256(content);
        BusinessFile existing = businessFileMapper.selectByHouseAndSha(houseId, sha256);
        if (existing != null) {
            return response(existing);
        }

        BusinessFile file = new BusinessFile();
        file.setId("file_" + UUID.randomUUID().toString().replace("-", ""));
        file.setHouseId(houseId);
        file.setFileName(safeFileName(upload.getOriginalFilename()));
        file.setContentType(contentType);
        file.setSizeBytes((long) content.length);
        file.setSha256(sha256);
        file.setContent(content);
        file.setCreateBy(String.valueOf(userId));
        try {
            businessFileMapper.insert(file);
            return response(file);
        } catch (DuplicateKeyException e) {
            BusinessFile duplicate = businessFileMapper.selectByHouseAndSha(houseId, sha256);
            if (duplicate != null) {
                return response(duplicate);
            }
            throw e;
        }
    }

    public BusinessFile requireFile(Long houseId, String fileId) {
        BusinessFile file = businessFileMapper.selectById(houseId, fileId);
        if (file == null) {
            throw new BizException(404, "图片不存在");
        }
        return file;
    }

    public List<String> requireImages(Long houseId, List<String> fileIds, boolean required) {
        if (fileIds == null || fileIds.isEmpty()) {
            if (required) {
                throw new BizException(400, "请至少上传一张相关图片");
            }
            return List.of();
        }
        if (fileIds.size() > MAX_FILES_PER_ACTION) {
            throw new BizException(400, "单次最多上传 6 张图片");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String fileId : fileIds) {
            String normalized = fileId == null ? "" : fileId.trim();
            if (normalized.isEmpty() || !unique.add(normalized)) {
                throw new BizException(400, "图片引用包含空值或重复值");
            }
        }
        List<String> normalizedIds = new ArrayList<>(unique);
        if (businessFileMapper.countByIds(houseId, normalizedIds) != normalizedIds.size()) {
            throw new BizException(400, "图片不存在或不属于当前兔舍");
        }
        return List.copyOf(normalizedIds);
    }

    private static BusinessFileUploadResponse response(BusinessFile file) {
        return new BusinessFileUploadResponse(
            file.getId(), file.getFileName(), file.getContentType(), file.getSizeBytes()
        );
    }

    private static String safeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "image";
        }
        String normalized = name.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return base.length() <= 255 ? base : base.substring(base.length() - 255);
    }

    private static String detectImageType(byte[] content) {
        if (content.length >= 3
            && (content[0] & 0xff) == 0xff
            && (content[1] & 0xff) == 0xd8
            && (content[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (content.length >= 8
            && (content[0] & 0xff) == 0x89
            && content[1] == 0x50
            && content[2] == 0x4e
            && content[3] == 0x47
            && content[4] == 0x0d
            && content[5] == 0x0a
            && content[6] == 0x1a
            && content[7] == 0x0a) {
            return "image/png";
        }
        if (content.length >= 12
            && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
            && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return "image/webp";
        }
        if (content.length >= 12
            && content[4] == 'f' && content[5] == 't' && content[6] == 'y' && content[7] == 'p') {
            String brand = new String(content, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (brand.equals("heic") || brand.equals("heix") || brand.equals("hevc") || brand.equals("mif1")) {
                return "image/heic";
            }
        }
        throw new BizException(400, "仅支持 JPEG、PNG、WebP 或 HEIC 图片");
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
