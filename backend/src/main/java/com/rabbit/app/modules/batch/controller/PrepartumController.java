package com.rabbit.app.modules.batch.controller;

import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.batch.entity.PrepartumRecord;
import com.rabbit.app.modules.batch.mapper.PrepartumRecordMapper;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.security.AuthContext;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PrepartumController {
    private final HouseService houseService;
    private final PrepartumRecordMapper prepartumRecordMapper;

    public PrepartumController(HouseService houseService, PrepartumRecordMapper prepartumRecordMapper) {
        this.houseService = houseService;
        this.prepartumRecordMapper = prepartumRecordMapper;
    }

    @GetMapping("/prepartum-records")
    @RequiresPermission(PermissionCode.RABBIT_RECORDS_LIST)
    public ApiResponse<List<PrepartumRecord>> list(@RequestHeader("X-House-Id") Long houseId,
                                                  @RequestParam(value = "batchId", required = false) Long batchId,
                                                  @RequestParam(value = "rabbitId", required = false) Long rabbitId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(prepartumRecordMapper.selectByHouse(houseId, batchId, rabbitId));
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
