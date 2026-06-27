package com.rabbit.app.modules.setting.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.setting.dto.HouseSettingResponse;
import com.rabbit.app.modules.setting.dto.UpdateSettingRequest;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.service.SettingService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.HouseContext;
import com.rabbit.app.security.HousePerm;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class SettingController {
    private static final Long USER_SETTING_DEDUP_HOUSE_ID = 0L;

    private final SettingService settingService;
    private final RequestDedupService requestDedupService;

    public SettingController(SettingService settingService, RequestDedupService requestDedupService) {
        this.settingService = settingService;
        this.requestDedupService = requestDedupService;
    }

    @GetMapping("/settings")
    public ApiResponse<GlobalSetting> get() {
        Long userId = requireLogin();
        return ApiResponse.ok(settingService.getOrCreateUserSetting(userId));
    }

    @PutMapping("/settings")
    public ApiResponse<Void> update(@Valid @RequestBody UpdateSettingRequest req) {
        Long userId = requireLogin();
        String api = "settings.update";
        if (requestDedupService.shouldSkipAsDone(USER_SETTING_DEDUP_HOUSE_ID, userId, api, req.getRequestId())) {
            return ApiResponse.ok(null);
        }
        requestDedupService.markProcessing(USER_SETTING_DEDUP_HOUSE_ID, userId, api, req.getRequestId());
        try {
            settingService.updateUserSetting(userId, req);
            requestDedupService.markDone(USER_SETTING_DEDUP_HOUSE_ID, userId, api, req.getRequestId());
            return ApiResponse.ok(null);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(USER_SETTING_DEDUP_HOUSE_ID, userId, api, req.getRequestId(), e.getMessage());
            throw e;
        }
    }

    @GetMapping("/house-settings")
    @HousePerm("view")
    public ApiResponse<HouseSettingResponse> getHouseSetting() {
        Long userId = requireLogin();
        Long houseId = requireHouse();
        boolean customized = settingService.hasHouseSetting(houseId);
        return ApiResponse.ok(HouseSettingResponse.of(
                settingService.getHouseSettingOrDefault(userId, houseId),
                customized));
    }

    @PutMapping("/house-settings")
    @HousePerm("control")
    public ApiResponse<Void> updateHouseSetting(@Valid @RequestBody UpdateSettingRequest req) {
        Long userId = requireLogin();
        Long houseId = requireHouse();
        String api = "house-settings.update";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, req.getRequestId())) {
            return ApiResponse.ok(null);
        }
        requestDedupService.markProcessing(houseId, userId, api, req.getRequestId());
        try {
            settingService.updateHouseSetting(userId, houseId, req);
            requestDedupService.markDone(houseId, userId, api, req.getRequestId());
            return ApiResponse.ok(null);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, req.getRequestId(), e.getMessage());
            throw e;
        }
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }

    private Long requireHouse() {
        HouseContext context = HouseContext.get();
        Long houseId = context == null ? null : context.getHouseId();
        if (houseId == null || houseId <= 0) {
            throw new BizException(400, "缺少X-House-Id");
        }
        return houseId;
    }
}
