package com.rabbit.app.modules.rabbit.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.rabbit.dto.CreateRabbitRequest;
import com.rabbit.app.modules.rabbit.dto.ReplacementRequest;
import com.rabbit.app.modules.rabbit.dto.UpdateRabbitRequest;
import com.rabbit.app.modules.rabbit.entity.Rabbit;
import com.rabbit.app.modules.rabbit.service.RabbitService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.HousePerm;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
@HousePerm("view")
public class RabbitController {
    private final HouseService houseService;
    private final RabbitService rabbitService;

    public RabbitController(HouseService houseService, RabbitService rabbitService) {
        this.houseService = houseService;
        this.rabbitService = rabbitService;
    }

    @PostMapping("/rabbits")
    @HousePerm("edit")
    public ApiResponse<Rabbit> createRabbit(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody CreateRabbitRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");

        Rabbit r = new Rabbit();
        r.setCageId(req.getCageId());
        r.setMotherId(req.getMotherId());
        r.setType(req.getType());
        r.setGender(req.getGender());
        r.setBreed(req.getBreed());
        r.setArrivalMethod(req.getArrivalMethod());
        r.setArrivalDate(req.getArrivalDate());
        r.setWeight(req.getWeight());
        return ApiResponse.ok(rabbitService.createRabbit(userId, houseId, r, req.getRequestId()));
    }

    @GetMapping("/rabbits")
    public ApiResponse<List<Rabbit>> listRabbits(
            @RequestHeader("X-House-Id") Long houseId,
            @RequestParam(value = "cageId", required = false) Long cageId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        if (page == null && pageSize == null) {
            return ApiResponse.ok(rabbitService.listRabbits(houseId, cageId, type, active));
        }
        return ApiResponse.ok(rabbitService.listRabbitsPage(houseId, cageId, type, active, page == null ? 1 : page, pageSize == null ? 50 : pageSize));
    }

    @GetMapping("/rabbits/{id}")
    public ApiResponse<Rabbit> getRabbit(@RequestHeader("X-House-Id") Long houseId, @PathVariable("id") Long id) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        Rabbit r = rabbitService.getRabbit(houseId, id);
        return ApiResponse.ok(r);
    }

    @PostMapping("/rabbits/replacement")
    @HousePerm("control")
    public ApiResponse<Void> replacement(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody ReplacementRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "control");
        boolean force = req.getForceExitBatch() != null && req.getForceExitBatch();
        rabbitService.convertToReplacement(userId, houseId, req.getRabbitIds(), force, req.getTargetCageId(), req.getRequestId());
        return ApiResponse.ok(null);
    }

    @PutMapping("/rabbits/{id}")
    @HousePerm("edit")
    public ApiResponse<Rabbit> updateRabbit(@RequestHeader("X-House-Id") Long houseId, @PathVariable("id") Long id, @Valid @RequestBody UpdateRabbitRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        return ApiResponse.ok(rabbitService.updateBaseInfo(userId, houseId, id, req.getCageId(), req.getMotherId(), req.getBreed(), req.getArrivalMethod(), req.getArrivalDate(), req.getWeight(), req.getRequestId()));
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
