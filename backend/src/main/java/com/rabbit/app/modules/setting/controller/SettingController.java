package com.rabbit.app.modules.setting.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.setting.dto.UpdateSettingRequest;
import com.rabbit.app.modules.setting.entity.GlobalSetting;
import com.rabbit.app.modules.setting.mapper.GlobalSettingMapper;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.HousePerm;
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
@HousePerm("view")
public class SettingController {
    private final HouseService houseService;
    private final GlobalSettingMapper globalSettingMapper;
    private final RequestDedupService requestDedupService;

    public SettingController(HouseService houseService, GlobalSettingMapper globalSettingMapper, RequestDedupService requestDedupService) {
        this.houseService = houseService;
        this.globalSettingMapper = globalSettingMapper;
        this.requestDedupService = requestDedupService;
    }

    @GetMapping("/settings")
    public ApiResponse<GlobalSetting> get(@RequestHeader("X-House-Id") Long houseId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(globalSettingMapper.selectByHouseId(houseId));
    }

    @PutMapping("/settings")
    @HousePerm("edit")
    public ApiResponse<Void> update(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody UpdateSettingRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        String api = "settings.update";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, req.getRequestId())) {
            return ApiResponse.ok(null);
        }
        requestDedupService.markProcessing(houseId, userId, api, req.getRequestId());
        try {
            GlobalSetting gs = new GlobalSetting();
            gs.setHouseId(houseId);
            gs.setAphrodisiacDays(req.getAphrodisiacDays());
            gs.setPalpationDays(req.getPalpationDays());
            gs.setPrepartumDays(req.getPrepartumDays());
            gs.setWeaningDays(req.getWeaningDays());
            gs.setPostpartumDays(req.getPostpartumDays());
            gs.setSaleDays(req.getSaleDays());
            gs.setReplacementDays(req.getReplacementDays());
            gs.setRemark(req.getRemark());
            gs.setUpdateBy(String.valueOf(userId));
            int n = globalSettingMapper.updateByHouse(gs);
            if (n == 0) {
                throw new BizException(400, "兔舍未初始化周期配置");
            }
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
}
