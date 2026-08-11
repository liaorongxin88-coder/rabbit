package com.rabbit.app.modules.house.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.dto.HouseInvitationRequest;
import com.rabbit.app.modules.house.dto.HouseInvitationResponse;
import com.rabbit.app.modules.house.service.HouseInvitationService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/house-invitations")
public class HouseInvitationController {
    private final HouseInvitationService invitationService;

    public HouseInvitationController(HouseInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping
    @RequiresPermission(PermissionCode.RABBIT_HOUSE_MEMBERS_ADD)
    public ApiResponse<HouseInvitationResponse> invite(
            @RequestHeader("X-House-Id") Long houseId,
            @Valid @RequestBody HouseInvitationRequest request
    ) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return ApiResponse.ok(invitationService.invite(
                houseId,
                userId,
                request.getPhone(),
                request.getRole(),
                request.getRequestId()
        ));
    }
}
