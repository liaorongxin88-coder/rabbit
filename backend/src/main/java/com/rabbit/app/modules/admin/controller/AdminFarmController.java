package com.rabbit.app.modules.admin.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.modules.admin.dto.AddFarmMemberRequest;
import com.rabbit.app.modules.admin.dto.AdminFarmItem;
import com.rabbit.app.modules.admin.dto.AdminFarmOverview;
import com.rabbit.app.modules.admin.dto.CreateAdminFarmRequest;
import com.rabbit.app.modules.admin.dto.PageResult;
import com.rabbit.app.modules.admin.dto.UpdateAdminFarmRequest;
import com.rabbit.app.modules.admin.dto.UpdateResourceStatusRequest;
import com.rabbit.app.modules.admin.service.AdminFarmService;
import com.rabbit.app.modules.house.dto.HouseMemberItem;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/farms")
public class AdminFarmController {
    private final AdminFarmService farmService;

    public AdminFarmController(AdminFarmService farmService) {
        this.farmService = farmService;
    }

    @GetMapping
    @RequiresPermission(PermissionCode.PLATFORM_FARMS_LIST)
    public ApiResponse<PageResult<AdminFarmItem>> list(
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status
    ) {
        return ApiResponse.ok(farmService.list(keyword, status, pageNum, pageSize));
    }

    @PostMapping
    @RequiresPermission(PermissionCode.PLATFORM_FARMS_ADD)
    public ApiResponse<AdminFarmItem> create(@Valid @RequestBody CreateAdminFarmRequest request) {
        return ApiResponse.ok(farmService.create(request));
    }

    @GetMapping("/{farmId}/overview")
    @RequiresPermission(PermissionCode.PLATFORM_FARMS_QUERY)
    public ApiResponse<AdminFarmOverview> overview(@PathVariable("farmId") Long farmId) {
        return ApiResponse.ok(farmService.overview(farmId));
    }

    @GetMapping("/{farmId}/members")
    @RequiresPermission(PermissionCode.PLATFORM_FARMS_QUERY)
    public ApiResponse<List<HouseMemberItem>> members(@PathVariable("farmId") Long farmId) {
        return ApiResponse.ok(farmService.members(farmId));
    }

    @PostMapping("/{farmId}/members")
    @RequiresPermission(PermissionCode.PLATFORM_FARMS_EDIT)
    public ApiResponse<List<HouseMemberItem>> addMember(
            @PathVariable("farmId") Long farmId,
            @Valid @RequestBody AddFarmMemberRequest request
    ) {
        return ApiResponse.ok(farmService.addMember(farmId, request.getUserId(), request.getRole()));
    }

    @PutMapping("/{farmId}")
    @RequiresPermission(PermissionCode.PLATFORM_FARMS_EDIT)
    public ApiResponse<AdminFarmItem> update(
            @PathVariable("farmId") Long farmId,
            @Valid @RequestBody UpdateAdminFarmRequest request
    ) {
        return ApiResponse.ok(farmService.update(farmId, request.getName(), request.getRemark()));
    }

    @PutMapping("/{farmId}/status")
    @RequiresPermission(PermissionCode.PLATFORM_FARMS_EDIT)
    public ApiResponse<AdminFarmItem> updateStatus(
            @PathVariable("farmId") Long farmId,
            @Valid @RequestBody UpdateResourceStatusRequest request
    ) {
        return ApiResponse.ok(farmService.updateStatus(farmId, request.getStatus()));
    }
}
