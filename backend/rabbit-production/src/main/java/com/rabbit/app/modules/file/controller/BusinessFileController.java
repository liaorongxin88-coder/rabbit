package com.rabbit.app.modules.file.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.file.dto.BusinessFileUploadResponse;
import com.rabbit.app.modules.file.entity.BusinessFile;
import com.rabbit.app.modules.file.service.BusinessFileService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/business-files")
public class BusinessFileController {
    private final HouseService houseService;
    private final BusinessFileService businessFileService;

    public BusinessFileController(HouseService houseService, BusinessFileService businessFileService) {
        this.houseService = houseService;
        this.businessFileService = businessFileService;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_EDIT)
    public ApiResponse<BusinessFileUploadResponse> uploadImage(
        @RequestHeader("X-House-Id") Long houseId,
        @RequestParam("file") MultipartFile file
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        return ApiResponse.ok(businessFileService.storeImage(houseId, userId, file));
    }

    @GetMapping("/{fileId}")
    @RequiresPermission(PermissionCode.RABBIT_BATCHES_QUERY)
    public ResponseEntity<byte[]> readImage(
        @RequestHeader("X-House-Id") Long houseId,
        @PathVariable String fileId
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        BusinessFile file = businessFileService.requireFile(houseId, fileId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.getContentType()))
            .contentLength(file.getSizeBytes())
            .cacheControl(CacheControl.noStore())
            .body(file.getContent());
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
