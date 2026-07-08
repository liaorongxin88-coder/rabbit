package com.rabbit.app.modules.admin.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.modules.admin.dto.AdminAccountItem;
import com.rabbit.app.modules.admin.dto.CreateAdminAccountRequest;
import com.rabbit.app.modules.admin.dto.MerchantAccountItem;
import com.rabbit.app.modules.admin.dto.PageResult;
import com.rabbit.app.modules.admin.dto.UpdateAdminAccountRequest;
import com.rabbit.app.modules.admin.dto.UpdateMerchantAccountRequest;
import com.rabbit.app.modules.admin.service.MerchantAccountAdminService;
import com.rabbit.app.modules.admin.service.PlatformAdminAccountService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {
    private final PlatformAdminAccountService platformAdminAccountService;
    private final MerchantAccountAdminService merchantAccountAdminService;

    public AdminAccountController(
            PlatformAdminAccountService platformAdminAccountService,
            MerchantAccountAdminService merchantAccountAdminService
    ) {
        this.platformAdminAccountService = platformAdminAccountService;
        this.merchantAccountAdminService = merchantAccountAdminService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminAccountItem>> list(@RequestParam(value = "page", required = false) Integer page,
                                                          @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                          @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(platformAdminAccountService.list(keyword, page, pageSize));
    }

    @GetMapping("/merchant-users")
    public ApiResponse<PageResult<MerchantAccountItem>> listMerchantAccounts(@RequestParam(value = "page", required = false) Integer page,
                                                                             @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                                             @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(merchantAccountAdminService.list(keyword, page, pageSize));
    }

    @PutMapping("/merchant-users/{userId}")
    public ApiResponse<MerchantAccountItem> updateMerchantAccount(@PathVariable("userId") Long userId,
                                                                  @Valid @RequestBody UpdateMerchantAccountRequest req) {
        return ApiResponse.ok(merchantAccountAdminService.update(userId, req.getUserName(), req.getPassword(), req.getConfirmPassword()));
    }

    @GetMapping("/{accountId}")
    public ApiResponse<AdminAccountItem> get(@PathVariable("accountId") Long accountId) {
        return ApiResponse.ok(platformAdminAccountService.get(accountId));
    }

    @PostMapping
    public ApiResponse<AdminAccountItem> create(@Valid @RequestBody CreateAdminAccountRequest req) {
        return ApiResponse.ok(platformAdminAccountService.create(req.getUserName(), req.getPassword(), req.getRole(), req.getEnabled()));
    }

    @PutMapping("/{accountId}")
    public ApiResponse<AdminAccountItem> update(@PathVariable("accountId") Long accountId, @Valid @RequestBody UpdateAdminAccountRequest req) {
        return ApiResponse.ok(platformAdminAccountService.update(accountId, req.getUserName(), req.getPassword(), req.getRole(), req.getEnabled()));
    }

    @DeleteMapping("/{accountId}")
    public ApiResponse<Void> delete(@PathVariable("accountId") Long accountId) {
        platformAdminAccountService.delete(accountId);
        return ApiResponse.ok(null);
    }
}
