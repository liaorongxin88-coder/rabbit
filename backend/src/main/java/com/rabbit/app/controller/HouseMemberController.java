package com.rabbit.app.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.dto.AddHouseMemberRequest;
import com.rabbit.app.dto.HouseMemberItem;
import com.rabbit.app.dto.UpdateHouseMemberRequest;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.HousePerm;
import com.rabbit.app.service.HouseMemberService;
import com.rabbit.app.service.HouseService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
@HousePerm("control")
public class HouseMemberController {
    private final HouseService houseService;
    private final HouseMemberService houseMemberService;

    public HouseMemberController(HouseService houseService, HouseMemberService houseMemberService) {
        this.houseService = houseService;
        this.houseMemberService = houseMemberService;
    }

    @GetMapping("/house-members")
    public ApiResponse<List<HouseMemberItem>> list(@RequestHeader("X-House-Id") Long houseId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "control");
        return ApiResponse.ok(houseMemberService.listMembers(houseId));
    }

    @PostMapping("/house-members")
    public ApiResponse<Void> add(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody AddHouseMemberRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "control");
        String operator = String.valueOf(userId);
        houseMemberService.addMember(houseId, userId, operator, req.getUserName(), req.getPerms(), req.getIsAdmin(), req.getRequestId());
        return ApiResponse.ok(null);
    }

    @PutMapping("/house-members/{memberUserId}")
    public ApiResponse<Void> update(@RequestHeader("X-House-Id") Long houseId,
                                    @PathVariable("memberUserId") Long memberUserId,
                                    @Valid @RequestBody UpdateHouseMemberRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "control");
        String operator = String.valueOf(userId);
        houseMemberService.updateMember(houseId, memberUserId, userId, operator, req.getPerms(), req.getIsAdmin(), req.getRequestId());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/house-members/{memberUserId}")
    public ApiResponse<Void> remove(@RequestHeader("X-House-Id") Long houseId,
                                    @PathVariable("memberUserId") Long memberUserId,
                                    @RequestParam("requestId") @NotBlank(message = "requestId不能为空") String requestId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "control");
        if (userId.equals(memberUserId)) {
            throw new BizException(400, "不支持移除自己，请使用退出接口");
        }
        houseMemberService.removeMember(houseId, memberUserId, userId, requestId);
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
