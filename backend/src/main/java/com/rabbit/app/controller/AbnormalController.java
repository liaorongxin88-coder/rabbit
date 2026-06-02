package com.rabbit.app.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.dto.DealRequest;
import com.rabbit.app.mapper.RabbitAbnormalConditionMapper;
import com.rabbit.app.model.RabbitAbnormalCondition;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.HousePerm;
import com.rabbit.app.service.HouseService;
import com.rabbit.app.service.RequestDedupService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
@HousePerm("view")
public class AbnormalController {
    private final HouseService houseService;
    private final RabbitAbnormalConditionMapper rabbitAbnormalConditionMapper;
    private final RequestDedupService requestDedupService;

    public AbnormalController(HouseService houseService, RabbitAbnormalConditionMapper rabbitAbnormalConditionMapper, RequestDedupService requestDedupService) {
        this.houseService = houseService;
        this.rabbitAbnormalConditionMapper = rabbitAbnormalConditionMapper;
        this.requestDedupService = requestDedupService;
    }

    @GetMapping("/abnormal")
    public ApiResponse<List<RabbitAbnormalCondition>> list(
            @RequestHeader("X-House-Id") Long houseId,
            @RequestParam(value = "isDeal", required = false) Boolean isDeal
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(rabbitAbnormalConditionMapper.selectByHouse(houseId, isDeal));
    }

    @PostMapping("/abnormal/{id}/deal")
    @HousePerm("edit")
    public ApiResponse<Void> deal(@RequestHeader("X-House-Id") Long houseId, @PathVariable("id") Long id, @Valid @RequestBody DealRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        String api = "abnormal.deal";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, req.getRequestId())) {
            return ApiResponse.ok(null);
        }
        requestDedupService.markProcessing(houseId, userId, api, req.getRequestId());
        try {
            rabbitAbnormalConditionMapper.markDeal(houseId, id, req.getDeal(), String.valueOf(userId));
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
