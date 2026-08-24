package com.rabbit.app.modules.admin.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.admin.dto.PageResult;
import com.rabbit.app.modules.apprelease.dto.AppReleaseItem;
import com.rabbit.app.modules.apprelease.dto.AppReleasePage;
import com.rabbit.app.modules.apprelease.dto.UpdateAppReleaseRequest;
import com.rabbit.app.modules.apprelease.service.AppReleaseService;
import com.rabbit.app.security.PlatformAdminContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/admin/app-releases")
public class AdminAppReleaseController {
    private final AppReleaseService appReleaseService;

    public AdminAppReleaseController(AppReleaseService appReleaseService) {
        this.appReleaseService = appReleaseService;
    }

    @GetMapping
    @RequiresPermission(PermissionCode.PLATFORM_APP_RELEASES_LIST)
    public ApiResponse<PageResult<AppReleaseItem>> list(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "status", required = false) String status
    ) {
        AppReleasePage page = appReleaseService.list(channel, status, pageNum, pageSize);
        return ApiResponse.ok(new PageResult<>(page.getItems(), page.getTotal(), page.getPage(), page.getPageSize()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission(PermissionCode.PLATFORM_APP_RELEASES_ADD)
    public ApiResponse<AppReleaseItem> create(
            @RequestParam("channel") String channel,
            @RequestParam("versionName") String versionName,
            @RequestParam("versionCode") Integer versionCode,
            @RequestParam("requestId") String requestId,
            @RequestParam(value = "releaseNotes", required = false) String releaseNotes,
            @RequestParam(value = "forceUpdate", required = false) Boolean forceUpdate,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.ok(appReleaseService.create(
                operator(),
                channel,
                versionName,
                versionCode,
                releaseNotes,
                forceUpdate,
                requestId,
                file
        ));
    }

    @PutMapping("/{id}")
    @RequiresPermission(PermissionCode.PLATFORM_APP_RELEASES_EDIT)
    public ApiResponse<AppReleaseItem> update(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateAppReleaseRequest request
    ) {
        return ApiResponse.ok(appReleaseService.updateMeta(
                operator(),
                id,
                request.getReleaseNotes(),
                request.getForceUpdate()
        ));
    }

    @PostMapping("/{id}/publish")
    @RequiresPermission(PermissionCode.PLATFORM_APP_RELEASES_EDIT)
    public ApiResponse<AppReleaseItem> publish(@PathVariable("id") String id) {
        return ApiResponse.ok(appReleaseService.publish(operator(), id));
    }

    @PostMapping("/{id}/revoke")
    @RequiresPermission(PermissionCode.PLATFORM_APP_RELEASES_EDIT)
    public ApiResponse<AppReleaseItem> revoke(@PathVariable("id") String id) {
        return ApiResponse.ok(appReleaseService.revoke(operator(), id));
    }

    @GetMapping("/{id}/apk")
    @RequiresPermission(PermissionCode.PLATFORM_APP_RELEASES_QUERY)
    public ResponseEntity<Resource> download(@PathVariable("id") String id) {
        AppReleaseService.StoredFile file = appReleaseService.openManaged(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString()
                )
                .body(file.resource());
    }

    private static String operator() {
        Long adminId = PlatformAdminContext.getAdminId();
        if (adminId == null) {
            throw new BizException(401, "后台未登录");
        }
        return String.valueOf(adminId);
    }
}
