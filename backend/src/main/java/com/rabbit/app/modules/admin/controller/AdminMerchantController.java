package com.rabbit.app.modules.admin.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.modules.admin.dto.CreateMerchantAccountRequest;
import com.rabbit.app.modules.admin.dto.CreateMerchantRequest;
import com.rabbit.app.modules.admin.dto.MerchantOverview;
import com.rabbit.app.modules.admin.dto.MerchantAccountSummary;
import com.rabbit.app.modules.admin.dto.PageResult;
import com.rabbit.app.modules.admin.dto.UpdateMerchantRequest;
import com.rabbit.app.modules.admin.dto.UpdateMerchantStatusRequest;
import com.rabbit.app.modules.admin.dto.UpdateMerchantHousePolicyRequest;
import com.rabbit.app.modules.merchant.dto.UpdateMerchantMemberRequest;
import com.rabbit.app.modules.merchant.entity.MerchantHousePolicy;
import com.rabbit.app.modules.admin.entity.Merchant;
import com.rabbit.app.modules.admin.service.MerchantAdminService;
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
@RequestMapping("/api/admin/merchants")
public class AdminMerchantController {
    private final MerchantAdminService merchantAdminService;

    public AdminMerchantController(MerchantAdminService merchantAdminService) {
        this.merchantAdminService = merchantAdminService;
    }

    @GetMapping
    @RequiresPermission(PermissionCode.PLATFORM_MERCHANTS_LIST)
    public ApiResponse<PageResult<Merchant>> list(@RequestParam(value = "page", required = false) Integer page,
                                                  @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                  @RequestParam(value = "keyword", required = false) String keyword,
                                                  @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.ok(merchantAdminService.list(keyword, status, page, pageSize));
    }

    @GetMapping("/{merchantId}")
    @RequiresPermission(PermissionCode.PLATFORM_MERCHANTS_QUERY)
    public ApiResponse<Merchant> get(@PathVariable("merchantId") Long merchantId) {
        return ApiResponse.ok(merchantAdminService.get(merchantId));
    }

    @PostMapping
    @RequiresPermission(PermissionCode.PLATFORM_MERCHANTS_ADD)
    public ApiResponse<Merchant> create(@Valid @RequestBody CreateMerchantRequest req) {
        return ApiResponse.ok(merchantAdminService.create(
                req.getName(),
                req.getContactName(),
                req.getContactPhone(),
                req.getRemark(),
                req.getUserName(),
                req.getPassword(),
                req.getConfirmPassword()
        ));
    }

    @PutMapping("/{merchantId}")
    @RequiresPermission(PermissionCode.PLATFORM_MERCHANTS_EDIT)
    public ApiResponse<Merchant> update(@PathVariable("merchantId") Long merchantId, @Valid @RequestBody UpdateMerchantRequest req) {
        return ApiResponse.ok(merchantAdminService.update(merchantId, req.getName(), req.getContactName(), req.getContactPhone(), req.getRemark()));
    }

    @PutMapping("/{merchantId}/status")
    @RequiresPermission(PermissionCode.PLATFORM_MERCHANTS_EDIT)
    public ApiResponse<Merchant> updateStatus(@PathVariable("merchantId") Long merchantId, @Valid @RequestBody UpdateMerchantStatusRequest req) {
        return ApiResponse.ok(merchantAdminService.updateStatus(merchantId, req.getStatus()));
    }

    @GetMapping("/{merchantId}/accounts")
    @RequiresPermission(PermissionCode.PLATFORM_MERCHANT_ACCOUNTS_LIST)
    public ApiResponse<List<MerchantAccountSummary>> accounts(@PathVariable("merchantId") Long merchantId) {
        return ApiResponse.ok(merchantAdminService.listAccounts(merchantId));
    }

    @PostMapping("/{merchantId}/accounts")
    @RequiresPermission(PermissionCode.PLATFORM_MERCHANT_ACCOUNTS_ADD)
    public ApiResponse<Void> createAccount(
            @PathVariable("merchantId") Long merchantId,
            @Valid @RequestBody CreateMerchantAccountRequest req
    ) {
        merchantAdminService.createAccount(
                merchantId,
                req.getUserName(),
                req.getPassword(),
                req.getConfirmPassword(),
                req.getRole()
        );
        return ApiResponse.ok(null);
    }

    @GetMapping("/{merchantId}/overview")
    @RequiresPermission(PermissionCode.PLATFORM_MERCHANT_OVERVIEW_QUERY)
    public ApiResponse<MerchantOverview> overview(@PathVariable("merchantId") Long merchantId) {
        return ApiResponse.ok(merchantAdminService.overview(merchantId));
    }

    @GetMapping("/{merchantId}/house-policy")
    @RequiresPermission(PermissionCode.PLATFORM_MERCHANT_POLICY_QUERY)
    public ApiResponse<MerchantHousePolicy> getHousePolicy(@PathVariable("merchantId") Long merchantId) {
        return ApiResponse.ok(merchantAdminService.getHousePolicy(merchantId));
    }

    @PutMapping("/{merchantId}/house-policy")
    @RequiresPermission(PermissionCode.PLATFORM_MERCHANT_POLICY_EDIT)
    public ApiResponse<MerchantHousePolicy> updateHousePolicy(
            @PathVariable("merchantId") Long merchantId,
            @Valid @RequestBody UpdateMerchantHousePolicyRequest req
    ) {
        return ApiResponse.ok(merchantAdminService.updateHousePolicy(
                merchantId,
                req.getHouseCreationEnabled(),
                req.getHouseMemberManagementEnabled(),
                req.getMaxHouseCount(),
                req.getMaxMembersPerHouse()
        ));
    }

    @PutMapping("/{merchantId}/accounts/{userId}/membership")
    @RequiresPermission(PermissionCode.PLATFORM_MERCHANT_MEMBERSHIP_EDIT)
    public ApiResponse<Void> updateMembership(
            @PathVariable("merchantId") Long merchantId,
            @PathVariable("userId") Long userId,
            @RequestBody UpdateMerchantMemberRequest req
    ) {
        merchantAdminService.updateMembership(merchantId, userId, req.getRole(), req.getStatus());
        return ApiResponse.ok(null);
    }
}
