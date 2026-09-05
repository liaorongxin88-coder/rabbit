package com.rabbit.app.modules.feed.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.feed.dto.FeedAllocationPreview;
import com.rabbit.app.modules.feed.dto.FeedAllocationPreviewRequest;
import com.rabbit.app.modules.feed.dto.FeedLogRequest;
import com.rabbit.app.modules.feed.entity.FeedLog;
import com.rabbit.app.modules.feed.service.FeedService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
import java.util.Date;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
@RequiresPermission(PermissionCode.RABBIT_FEED_LIST)
public class FeedController {
    private final HouseService houseService;
    private final FeedService feedService;

    public FeedController(HouseService houseService, FeedService feedService) {
        this.houseService = houseService;
        this.feedService = feedService;
    }

    @PostMapping("/feed-logs")
    @RequiresPermission(PermissionCode.RABBIT_FEED_ADD)
    public ApiResponse<Void> add(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody FeedLogRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        feedService.assertRequestAllowed(
            userId,
            houseId,
            req.getRequestId(),
            req.getRabbitIds(),
            req.getFeedTime(),
            req.getUnit(),
            req.getAllocations()
        );

        FeedLog log = new FeedLog();
        log.setFeedTime(req.getFeedTime());
        log.setFeedType(req.getFeedType());
        log.setItemId(req.getItemId());
        log.setUnit(req.getUnit());
        log.setRequestId(req.getRequestId());
        log.setAmount(req.getAmount());
        log.setRemark(req.getRemark());
        feedService.addFeedLog(userId, houseId, log, req.getRabbitIds(), req.getAllocations());
        return ApiResponse.ok(null);
    }

    @PostMapping("/feed-logs/allocation-preview")
    @RequiresPermission(PermissionCode.RABBIT_FEED_ADD)
    public ApiResponse<FeedAllocationPreview> previewAllocations(
        @RequestHeader("X-House-Id") Long houseId,
        @Valid @RequestBody FeedAllocationPreviewRequest request
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        return ApiResponse.ok(feedService.previewAllocations(
            houseId, request.rabbitIds(), request.feedTime()
        ));
    }

    @GetMapping("/feed-logs")
    public ApiResponse<List<FeedLog>> list(@RequestHeader("X-House-Id") Long houseId,
                                           @RequestParam(value = "page", required = false) Integer page,
                                           @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                           @RequestParam(value = "from", required = false) Date from,
                                           @RequestParam(value = "to", required = false) Date to) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        if (page == null && pageSize == null && from == null && to == null) {
            return ApiResponse.ok(feedService.list(houseId));
        }
        return ApiResponse.ok(feedService.listPage(houseId, from, to, page == null ? 1 : page, pageSize == null ? 20 : pageSize));
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
