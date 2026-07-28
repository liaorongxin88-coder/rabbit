package com.rabbit.app.modules.nfc.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.nfc.dto.BindNfcTagRequest;
import com.rabbit.app.modules.nfc.dto.BindNfcCageRequest;
import com.rabbit.app.modules.nfc.dto.NfcCageBindingView;
import com.rabbit.app.modules.nfc.dto.NfcCageQueueItem;
import com.rabbit.app.modules.nfc.dto.NfcResolvedTarget;
import com.rabbit.app.modules.nfc.dto.NfcTagView;
import com.rabbit.app.modules.nfc.dto.ResolveNfcCageRequest;
import com.rabbit.app.modules.nfc.service.NfcCageService;
import com.rabbit.app.modules.nfc.service.NfcTagAdminService;
import com.rabbit.app.modules.nfc.service.NfcTagService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.HousePerm;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
public class NfcController {
    private final NfcTagService nfcTagService;
    private final NfcTagAdminService nfcTagAdminService;
    private final NfcCageService nfcCageService;

    public NfcController(NfcTagService nfcTagService, NfcTagAdminService nfcTagAdminService, NfcCageService nfcCageService) {
        this.nfcTagService = nfcTagService;
        this.nfcTagAdminService = nfcTagAdminService;
        this.nfcCageService = nfcCageService;
    }

    @PostMapping("/nfc/tags")
    @HousePerm("control")
    public ApiResponse<Object> bind(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody BindNfcTagRequest req) {
        Long userId = requireLogin();
        return ApiResponse.ok(nfcTagService.bind(userId, houseId, req.getTagUid(), req.getTargetType(), req.getTargetId(), req.getRabbitId(), req.getRecordId(), req.getRemark(), req.getRequestId()));
    }

    @GetMapping("/nfc/cages/write-queue")
    @HousePerm("control")
    public ApiResponse<List<NfcCageQueueItem>> cageWriteQueue(@RequestHeader("X-House-Id") Long houseId) {
        return ApiResponse.ok(nfcCageService.listWriteQueue(requireLogin(), houseId));
    }

    @PostMapping("/nfc/cages/bind")
    @HousePerm("control")
    public ApiResponse<NfcCageBindingView> bindCage(
            @RequestHeader("X-House-Id") Long houseId,
            @Valid @RequestBody BindNfcCageRequest request
    ) {
        return ApiResponse.ok(nfcCageService.bind(requireLogin(), houseId, request));
    }

    @PostMapping("/nfc/cages/resolve")
    @HousePerm("view")
    public ApiResponse<NfcCageBindingView> resolveCage(
            @RequestHeader("X-House-Id") Long houseId,
            @Valid @RequestBody ResolveNfcCageRequest request
    ) {
        return ApiResponse.ok(nfcCageService.resolve(requireLogin(), houseId, request));
    }

    @GetMapping("/nfc/resolve")
    @HousePerm("view")
    public ApiResponse<NfcResolvedTarget> resolve(@RequestHeader("X-House-Id") Long houseId, @RequestParam("tagUid") String tagUid) {
        Long userId = requireLogin();
        return ApiResponse.ok(nfcTagService.resolve(userId, houseId, tagUid));
    }

    @GetMapping("/nfc/tags")
    @HousePerm("control")
    public ApiResponse<List<NfcTagView>> list(@RequestHeader("X-House-Id") Long houseId,
                                              @RequestParam(value = "tagUid", required = false) String tagUid,
                                              @RequestParam(value = "targetType", required = false) String targetType,
                                              @RequestParam(value = "targetId", required = false) Long targetId,
                                              @RequestParam(value = "page", required = false) Integer page,
                                              @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        Long userId = requireLogin();
        return ApiResponse.ok(nfcTagAdminService.list(userId, houseId, tagUid, targetType, targetId, page == null ? 1 : page, pageSize == null ? 50 : pageSize));
    }

    @DeleteMapping("/nfc/tags")
    @HousePerm("control")
    public ApiResponse<Object> unbind(@RequestHeader("X-House-Id") Long houseId, @RequestParam("tagUid") String tagUid) {
        Long userId = requireLogin();
        nfcTagAdminService.unbind(userId, houseId, tagUid);
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
