package com.rabbit.app.modules.rabbit.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.rabbit.dto.DealRequest;
import com.rabbit.app.modules.rabbit.entity.RabbitAbnormalCondition;
import com.rabbit.app.modules.rabbit.service.RabbitAbnormalService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
@RequiresPermission(PermissionCode.RABBIT_ABNORMAL_LIST)
public class AbnormalController {
    private final HouseService houseService;
    private final RabbitAbnormalService rabbitAbnormalService;

    public AbnormalController(HouseService houseService, RabbitAbnormalService rabbitAbnormalService) {
        this.houseService = houseService;
        this.rabbitAbnormalService = rabbitAbnormalService;
    }

    @GetMapping("/abnormal")
    public ApiResponse<List<RabbitAbnormalCondition>> list(
            @RequestHeader("X-House-Id") Long houseId,
            @RequestParam(value = "isDeal", required = false) Boolean isDeal
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(rabbitAbnormalService.list(houseId, isDeal));
    }

    @PostMapping("/abnormal/{id}/deal")
    @RequiresPermission(PermissionCode.RABBIT_ABNORMAL_EDIT)
    public ApiResponse<Void> deal(@RequestHeader("X-House-Id") Long houseId, @PathVariable("id") Long id, @Valid @RequestBody DealRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        rabbitAbnormalService.deal(userId, houseId, id, req.getDeal(), req.getRequestId());
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
