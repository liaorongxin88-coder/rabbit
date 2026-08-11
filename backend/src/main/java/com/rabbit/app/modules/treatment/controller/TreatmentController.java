package com.rabbit.app.modules.treatment.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.treatment.dto.CompleteTreatmentRequest;
import com.rabbit.app.modules.treatment.dto.CreateTreatmentRequest;
import com.rabbit.app.modules.treatment.entity.TreatmentRecord;
import com.rabbit.app.modules.treatment.service.TreatmentService;
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
@RequiresPermission(PermissionCode.RABBIT_TREATMENTS_LIST)
public class TreatmentController {
    private final HouseService houseService;
    private final TreatmentService treatmentService;

    public TreatmentController(HouseService houseService, TreatmentService treatmentService) {
        this.houseService = houseService;
        this.treatmentService = treatmentService;
    }

    @PostMapping("/treatments")
    @RequiresPermission(PermissionCode.RABBIT_TREATMENTS_EDIT)
    public ApiResponse<TreatmentRecord> create(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody CreateTreatmentRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        TreatmentRecord r = new TreatmentRecord();
        r.setRabbitId(req.getRabbitId());
        r.setStartDate(req.getStartDate());
        r.setDiagnosis(req.getDiagnosis());
        r.setDrug(req.getDrug());
        r.setDose(req.getDose());
        r.setDays(req.getDays());
        r.setNextReviewDate(req.getNextReviewDate());
        r.setRemark(req.getRemark());
        return ApiResponse.ok(treatmentService.create(userId, houseId, r, req.getRequestId()));
    }

    @GetMapping("/treatments")
    public ApiResponse<List<TreatmentRecord>> list(@RequestHeader("X-House-Id") Long houseId,
                                                   @RequestParam("rabbitId") Long rabbitId,
                                                   @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(treatmentService.listByRabbit(houseId, rabbitId, limit == null ? 50 : limit));
    }

    @PostMapping("/treatments/{id}/complete")
    @RequiresPermission(PermissionCode.RABBIT_TREATMENTS_EDIT)
    public ApiResponse<Void> complete(@RequestHeader("X-House-Id") Long houseId,
                                      @PathVariable("id") Long id,
                                      @Valid @RequestBody CompleteTreatmentRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        treatmentService.complete(userId, houseId, id, req.getCompleteTime(), req.getRemark(), req.getRequestId());
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
