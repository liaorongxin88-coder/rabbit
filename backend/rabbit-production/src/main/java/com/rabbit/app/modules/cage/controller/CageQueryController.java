package com.rabbit.app.modules.cage.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.cage.dto.CageSummary;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.cage.service.CageSummaryService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.nfc.dto.BindCageNfcTagRequest;
import com.rabbit.app.modules.nfc.service.CageNfcTagService;
import com.rabbit.app.modules.nfc.service.NfcTagService;
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
public class CageQueryController {
    private final HouseService houseService;
    private final CageMapper cageMapper;
    private final CageNfcTagService cageNfcTagService;
    private final NfcTagService nfcTagService;
    private final CageSummaryService cageSummaryService;

    public CageQueryController(
            HouseService houseService,
            CageMapper cageMapper,
            CageNfcTagService cageNfcTagService,
            NfcTagService nfcTagService,
            CageSummaryService cageSummaryService
    ) {
        this.houseService = houseService;
        this.cageMapper = cageMapper;
        this.cageNfcTagService = cageNfcTagService;
        this.nfcTagService = nfcTagService;
        this.cageSummaryService = cageSummaryService;
    }

    @GetMapping("/cages")
    @RequiresPermission(PermissionCode.RABBIT_CAGES_LIST)
    public ApiResponse<List<Cage>> listCages(@RequestHeader("X-House-Id") Long houseId) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(cageMapper.selectByHouseId(houseId));
    }

    @PostMapping("/cages/nfc-tags")
    @RequiresPermission(PermissionCode.RABBIT_NFC_CONTROL)
    public ApiResponse<Object> bindCageNfcTag(
            @RequestHeader("X-House-Id") Long houseId,
            @Valid @RequestBody BindCageNfcTagRequest req
    ) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "control");
        nfcTagService.bind(
                userId,
                houseId,
                req.getTagUid(),
                "CAGE",
                req.getCageId(),
                null,
                null,
                req.getRemark(),
                req.getRequestId()
        );
        return ApiResponse.ok(cageNfcTagService.bind(
                userId,
                houseId,
                req.getCageId(),
                req.getTagUid(),
                req.getRemark(),
                req.getRequestId()
        ));
    }

    @GetMapping("/cages/by-nfc")
    @RequiresPermission(PermissionCode.RABBIT_NFC_QUERY)
    public ApiResponse<Cage> getCageByNfc(
            @RequestHeader("X-House-Id") Long houseId,
            @RequestParam("tagUid") String tagUid
    ) {
        Long userId = requireLogin();
        return ApiResponse.ok(cageNfcTagService.resolveCage(userId, houseId, tagUid));
    }

    @GetMapping("/cages/{id}/summary")
    @RequiresPermission(PermissionCode.RABBIT_CAGES_QUERY)
    public ApiResponse<CageSummary> getCageSummary(
            @RequestHeader("X-House-Id") Long houseId,
            @org.springframework.web.bind.annotation.PathVariable("id") Long id
    ) {
        Long userId = requireLogin();
        return ApiResponse.ok(cageSummaryService.getSummary(userId, houseId, id));
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
