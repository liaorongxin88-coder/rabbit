package com.rabbit.app.modules.vaccination.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.vaccination.dto.CreateVaccinationRequest;
import com.rabbit.app.modules.vaccination.dto.VaccinationBatchResult;
import com.rabbit.app.modules.vaccination.entity.VaccinationRecord;
import com.rabbit.app.modules.vaccination.service.VaccinationService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import jakarta.validation.Valid;
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
@RequiresPermission(PermissionCode.RABBIT_VACCINATIONS_LIST)
public class VaccinationController {
    private final HouseService houseService;
    private final VaccinationService vaccinationService;

    public VaccinationController(HouseService houseService, VaccinationService vaccinationService) {
        this.houseService = houseService;
        this.vaccinationService = vaccinationService;
    }

    @PostMapping("/vaccinations")
    @RequiresPermission(PermissionCode.RABBIT_VACCINATIONS_ADD)
    public ApiResponse<VaccinationBatchResult> create(@RequestHeader("X-House-Id") Long houseId,
                                                      @Valid @RequestBody CreateVaccinationRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        VaccinationRecord template = new VaccinationRecord();
        template.setVaccineName(req.getVaccineName());
        template.setVaccineBatchNo(req.getVaccineBatchNo());
        template.setDose(req.getDose());
        template.setRoute(req.getRoute());
        template.setVaccinatedAt(req.getVaccinatedAt());
        template.setNextDueDate(req.getNextDueDate());
        template.setRemark(req.getRemark());
        return ApiResponse.ok(vaccinationService.create(
            userId, houseId, req.getRabbitIds(), template, req.getRequestId()
        ));
    }

    @GetMapping("/vaccinations")
    public ApiResponse<List<VaccinationRecord>> list(@RequestHeader("X-House-Id") Long houseId,
                                                     @RequestParam("rabbitId") Long rabbitId,
                                                     @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(vaccinationService.listByRabbit(houseId, rabbitId, limit == null ? 50 : limit));
    }

    /** 待接种：已到 next_due_date 且仍未补种的记录。 */
    @GetMapping("/vaccinations/due")
    public ApiResponse<List<VaccinationRecord>> listDue(@RequestHeader("X-House-Id") Long houseId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(vaccinationService.listDue(houseId));
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
