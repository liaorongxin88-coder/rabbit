package com.rabbit.app.modules.merchant.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.merchant.dto.AddMerchantMemberRequest;
import com.rabbit.app.modules.merchant.dto.MerchantMemberItem;
import com.rabbit.app.modules.merchant.dto.MerchantMembershipView;
import com.rabbit.app.modules.merchant.dto.UpdateMerchantMemberRequest;
import com.rabbit.app.modules.merchant.service.MerchantMembershipService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/merchant-memberships")
public class MerchantMembershipController {
    private final MerchantMembershipService membershipService;

    public MerchantMembershipController(MerchantMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping
    @RequiresPermission(PermissionCode.MERCHANT_MEMBERSHIPS_LIST)
    public ApiResponse<List<MerchantMembershipView>> listMine() {
        return ApiResponse.ok(membershipService.listMyMemberships(requireLogin()));
    }

    @GetMapping("/{merchantId}/members")
    @RequiresPermission(PermissionCode.MERCHANT_MEMBERS_LIST)
    public ApiResponse<List<MerchantMemberItem>> listMembers(@PathVariable("merchantId") Long merchantId) {
        return ApiResponse.ok(membershipService.listMembers(requireLogin(), merchantId));
    }

    @PostMapping("/{merchantId}/members")
    @RequiresPermission(PermissionCode.MERCHANT_MEMBERS_ADD)
    public ApiResponse<Void> addMember(
            @PathVariable("merchantId") Long merchantId,
            @Valid @RequestBody AddMerchantMemberRequest request
    ) {
        membershipService.addMember(requireLogin(), merchantId, request.getUserName(), request.getRole());
        return ApiResponse.ok(null);
    }

    @PutMapping("/{merchantId}/members/{userId}")
    @RequiresPermission(PermissionCode.MERCHANT_MEMBERS_EDIT)
    public ApiResponse<Void> updateMember(
            @PathVariable("merchantId") Long merchantId,
            @PathVariable("userId") Long userId,
            @RequestBody UpdateMerchantMemberRequest request
    ) {
        membershipService.updateMember(requireLogin(), merchantId, userId, request.getRole(), request.getStatus());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{merchantId}/members/{userId}")
    @RequiresPermission(PermissionCode.MERCHANT_MEMBERS_REMOVE)
    public ApiResponse<Void> removeMember(
            @PathVariable("merchantId") Long merchantId,
            @PathVariable("userId") Long userId
    ) {
        membershipService.removeMember(requireLogin(), merchantId, userId);
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
