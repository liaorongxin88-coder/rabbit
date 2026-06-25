package com.rabbit.app.modules.hardware.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.hardware.dto.HardwareAphrodisiacRequest;
import com.rabbit.app.modules.hardware.dto.HardwareStatus;
import com.rabbit.app.modules.hardware.service.HardwareLinkService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.HousePerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hardware")
@HousePerm("control")
public class HardwareController {
    private final HouseService houseService;
    private final HardwareLinkService hardwareLinkService;

    public HardwareController(HouseService houseService, HardwareLinkService hardwareLinkService) {
        this.houseService = houseService;
        this.hardwareLinkService = hardwareLinkService;
    }

    @GetMapping("/status")
    public ApiResponse<HardwareStatus> status(@RequestHeader("X-House-Id") Long houseId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "control");
        HardwareStatus s = new HardwareStatus();
        s.setEnabled(hardwareLinkService.isEnabled());
        s.setType(hardwareLinkService.getType());
        return ApiResponse.ok(s);
    }

    @PostMapping("/aphrodisiac/start")
    public ApiResponse<Void> aphrodisiacStart(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody HardwareAphrodisiacRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "control");
        hardwareLinkService.aphrodisiacStart(houseId, req.getBatchId(), req.getRabbitIds());
        return ApiResponse.ok(null);
    }

    @PostMapping("/aphrodisiac/finish")
    public ApiResponse<Void> aphrodisiacFinish(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody HardwareAphrodisiacRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "control");
        hardwareLinkService.aphrodisiacFinish(houseId, req.getBatchId(), req.getRabbitIds());
        return ApiResponse.ok(null);
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
