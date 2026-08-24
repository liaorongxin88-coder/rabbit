package com.rabbit.app.modules.apprelease.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.modules.apprelease.dto.AppUpdateCheckResponse;
import com.rabbit.app.modules.apprelease.service.AppReleaseService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/app/updates")
public class AppUpdateController {
    private final AppReleaseService appReleaseService;

    public AppUpdateController(AppReleaseService appReleaseService) {
        this.appReleaseService = appReleaseService;
    }

    @GetMapping("/check")
    public ApiResponse<AppUpdateCheckResponse> check(
            @RequestParam("channel") String channel,
            @RequestParam("versionCode") Integer versionCode
    ) {
        return ApiResponse.ok(appReleaseService.check(channel, versionCode));
    }

    @GetMapping("/{id}/apk")
    public ResponseEntity<Resource> download(@PathVariable("id") String id) {
        AppReleaseService.StoredFile file = appReleaseService.openPublished(id);
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
}
