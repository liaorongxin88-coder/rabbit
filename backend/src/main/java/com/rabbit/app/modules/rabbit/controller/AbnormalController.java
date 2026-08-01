package com.rabbit.app.modules.rabbit.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.rabbit.dto.DealRequest;
import com.rabbit.app.modules.rabbit.entity.RabbitAbnormalCondition;
import com.rabbit.app.modules.rabbit.mapper.RabbitAbnormalConditionMapper;
import com.rabbit.app.modules.rabbit.mapper.RabbitMapper;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.HousePerm;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.annotation.Transactional;
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
@HousePerm("view")
public class AbnormalController {
    private final HouseService houseService;
    private final RabbitAbnormalConditionMapper rabbitAbnormalConditionMapper;
    private final RabbitMapper rabbitMapper;
    private final RequestDedupService requestDedupService;

    public AbnormalController(HouseService houseService, RabbitAbnormalConditionMapper rabbitAbnormalConditionMapper,
                              RabbitMapper rabbitMapper, RequestDedupService requestDedupService) {
        this.houseService = houseService;
        this.rabbitAbnormalConditionMapper = rabbitAbnormalConditionMapper;
        this.rabbitMapper = rabbitMapper;
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
    @Transactional
    public ApiResponse<Void> deal(@RequestHeader("X-House-Id") Long houseId, @PathVariable("id") Long id, @Valid @RequestBody DealRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        String api = "abnormal.deal";
        if (requestDedupService.shouldSkipAsDone(houseId, userId, api, req.getRequestId())) {
            return ApiResponse.ok(null);
        }
        requestDedupService.markProcessing(houseId, userId, api, req.getRequestId());
        try {
            RabbitAbnormalCondition condition = rabbitAbnormalConditionMapper.selectById(houseId, id);
            if (condition == null) {
                throw new BizException(404, "异常记录不存在");
            }
            String operator = String.valueOf(userId);
            int changed = rabbitAbnormalConditionMapper.markDeal(houseId, id, req.getDeal(), operator);
            if (changed > 0 && rabbitMapper.bumpStateVersion(houseId, condition.getRabbitId(), operator) == 0) {
                throw new BizException(409, "兔只状态已变化，请刷新后重试");
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
