package com.rabbit.app.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.dto.CreateCageRequest;
import com.rabbit.app.dto.SetCageRabbitCountRequest;
import com.rabbit.app.dto.UpdateCageRequest;
import com.rabbit.app.model.Cage;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.HousePerm;
import com.rabbit.app.service.CageAdminService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Validated
@RestController
@RequestMapping("/api")
@HousePerm("control")
public class CageAdminController {
    private final CageAdminService cageAdminService;

    public CageAdminController(CageAdminService cageAdminService) {
        this.cageAdminService = cageAdminService;
    }

    @PostMapping("/cages")
    public ApiResponse<Cage> create(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody CreateCageRequest req) {
        Long userId = requireLogin();
        return ApiResponse.ok(cageAdminService.create(userId, houseId, req.getCageNumber(), req.getRemark(), req.getIsEnabled()));
    }

    @PutMapping("/cages/{id}")
    public ApiResponse<Cage> update(@RequestHeader("X-House-Id") Long houseId, @org.springframework.web.bind.annotation.PathVariable("id") Long id, @Valid @RequestBody UpdateCageRequest req) {
        Long userId = requireLogin();
        return ApiResponse.ok(cageAdminService.update(userId, houseId, id, req.getCageNumber(), req.getRemark(), req.getIsEnabled()));
    }

    @DeleteMapping("/cages/{id}")
    public ApiResponse<Object> delete(@RequestHeader("X-House-Id") Long houseId, @org.springframework.web.bind.annotation.PathVariable("id") Long id) {
        Long userId = requireLogin();
        cageAdminService.delete(userId, houseId, id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/cages/{id}/rabbit-count")
    public ApiResponse<Cage> setRabbitCount(@RequestHeader("X-House-Id") Long houseId, @org.springframework.web.bind.annotation.PathVariable("id") Long id, @Valid @RequestBody SetCageRabbitCountRequest req) {
        Long userId = requireLogin();
        return ApiResponse.ok(cageAdminService.setRabbitCount(userId, houseId, id, req.getRabbitCount()));
    }

    @PostMapping("/cages/recount-rabbit-count")
    public ApiResponse<Integer> recountRabbitCount(@RequestHeader("X-House-Id") Long houseId) {
        Long userId = requireLogin();
        return ApiResponse.ok(cageAdminService.recountRabbitCount(userId, houseId));
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
