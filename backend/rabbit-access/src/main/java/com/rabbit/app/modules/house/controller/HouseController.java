package com.rabbit.app.modules.house.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.dto.CreateHouseRequest;
import com.rabbit.app.modules.house.dto.HousePermissionInfo;
import com.rabbit.app.modules.house.dto.UpdateHouseRequest;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class HouseController {
    private final HouseService houseService;

    public HouseController(HouseService houseService) {
        this.houseService = houseService;
    }

    @GetMapping("/houses")
    @RequiresPermission(PermissionCode.RABBIT_HOUSES_LIST)
    public ApiResponse<List<RabbitHouse>> listHouses() {
        Long userId = requireLogin();
        return ApiResponse.ok(houseService.listMyHouses(userId));
    }

    @GetMapping("/houses/permission")
    @RequiresPermission(PermissionCode.RABBIT_HOUSES_QUERY)
    public ApiResponse<HousePermissionInfo> getMyHousePermission(@RequestHeader("X-House-Id") Long houseId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(houseService.getMyHousePermission(userId, houseId));
    }

    @PostMapping("/houses")
    @RequiresPermission(PermissionCode.RABBIT_HOUSES_ADD)
    public ApiResponse<RabbitHouse> createHouse(@Valid @RequestBody CreateHouseRequest req) {
        Long userId = requireLogin();
        RabbitHouse house = houseService.createHouse(
                userId,
                req.getName(),
                req.getLayoutRows(),
                req.getLayoutCols(),
                req.getLayoutLayers(),
                req.getRemark(),
                req.getRequestId()
        );
        return ApiResponse.ok(house);
    }

    @PutMapping("/houses/{id}")
    @RequiresPermission(PermissionCode.RABBIT_HOUSES_EDIT)
    public ApiResponse<RabbitHouse> updateHouse(
            @RequestHeader("X-House-Id") Long houseId,
            @org.springframework.web.bind.annotation.PathVariable("id") Long id,
            @Valid @RequestBody UpdateHouseRequest req
    ) {
        Long userId = requireLogin();
        if (houseId == null || id == null || !houseId.equals(id)) {
            throw new BizException(400, "X-House-Id与路径id不一致");
        }
        return ApiResponse.ok(houseService.updateHouse(userId, id, req.getName(), req.getRemark()));
    }

    @DeleteMapping("/houses/{id}")
    @RequiresPermission(PermissionCode.RABBIT_HOUSES_REMOVE)
    public ApiResponse<Object> deleteHouse(
            @RequestHeader("X-House-Id") Long houseId,
            @org.springframework.web.bind.annotation.PathVariable("id") Long id
    ) {
        Long userId = requireLogin();
        if (houseId == null || id == null || !houseId.equals(id)) {
            throw new BizException(400, "X-House-Id与路径id不一致");
        }
        houseService.deleteHouse(userId, id);
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
