package com.rabbit.app.modules.setting.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.setting.dto.HouseSettingResponse;
import com.rabbit.app.modules.setting.dto.ReminderPreferenceRequest;
import com.rabbit.app.modules.setting.dto.ReminderPreferenceResponse;
import com.rabbit.app.modules.setting.dto.UpdateSettingRequest;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.service.ReminderPreferenceService;
import com.rabbit.app.modules.setting.service.SettingService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.HouseContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class SettingController {
    private static final Long USER_SETTING_DEDUP_HOUSE_ID = 0L;

    private final SettingService settingService;
    private final ReminderPreferenceService reminderPreferenceService;
    private final RequestDedupService requestDedupService;
    private final HouseService houseService;

    public SettingController(
        SettingService settingService,
        ReminderPreferenceService reminderPreferenceService,
        RequestDedupService requestDedupService,
        HouseService houseService
    ) {
        this.settingService = settingService;
        this.reminderPreferenceService = reminderPreferenceService;
        this.requestDedupService = requestDedupService;
        this.houseService = houseService;
    }

    @GetMapping("/settings")
    @RequiresPermission(PermissionCode.USER_SETTINGS_QUERY)
    public ApiResponse<GlobalSetting> get() {
        Long userId = requireLogin();
        return ApiResponse.ok(settingService.getOrCreateUserSetting(userId));
    }

    @PutMapping("/settings")
    @RequiresPermission(PermissionCode.USER_SETTINGS_EDIT)
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

    @GetMapping("/reminder-settings")
    @RequiresPermission(PermissionCode.USER_SETTINGS_QUERY)
    public ApiResponse<ReminderPreferenceResponse> getReminderSettings(
        @RequestHeader("X-House-Id") Long houseId
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(ReminderPreferenceResponse.from(
            reminderPreferenceService.getOrCreate(userId, houseId)
        ));
    }

    @PutMapping("/reminder-settings")
    @RequiresPermission(PermissionCode.USER_SETTINGS_EDIT)
    public ApiResponse<Void> updateReminderSettings(
        @RequestHeader("X-House-Id") Long houseId,
        @Valid @RequestBody ReminderPreferenceRequest req
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        String api = "reminder-settings.update";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, req.getRequestId())) {
            return ApiResponse.ok(null);
        }
        requestDedupService.markProcessing(houseId, userId, api, req.getRequestId());
        try {
            reminderPreferenceService.update(userId, houseId, req);
            requestDedupService.markDone(houseId, userId, api, req.getRequestId());
            return ApiResponse.ok(null);
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, api, req.getRequestId(), e.getMessage());
            throw e;
        }
    }

    @GetMapping("/house-settings")
    @RequiresPermission(PermissionCode.RABBIT_SETTINGS_QUERY)
    public ApiResponse<HouseSettingResponse> getHouseSetting() {
        Long userId = requireLogin();
        Long houseId = requireHouse();
        GlobalSetting setting = settingService.getHouseSettingOrDefault(userId, houseId);
        return ApiResponse.ok(HouseSettingResponse.of(setting, true));
    }

    @PutMapping("/house-settings")
    @RequiresPermission(PermissionCode.RABBIT_SETTINGS_EDIT)
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
