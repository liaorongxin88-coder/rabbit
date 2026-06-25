package com.rabbit.app.modules.admin.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.modules.admin.dto.AddMerchantUserRequest;
import com.rabbit.app.modules.admin.dto.CreateMerchantRequest;
import com.rabbit.app.modules.admin.dto.MerchantOverview;
import com.rabbit.app.modules.admin.dto.MerchantUserItem;
import com.rabbit.app.modules.admin.dto.PageResult;
import com.rabbit.app.modules.admin.dto.UpdateMerchantRequest;
import com.rabbit.app.modules.admin.dto.UpdateMerchantStatusRequest;
import com.rabbit.app.modules.admin.entity.Merchant;
import com.rabbit.app.modules.admin.service.MerchantAdminService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/admin/merchants")
public class AdminMerchantController {
    private final MerchantAdminService merchantAdminService;

    public AdminMerchantController(MerchantAdminService merchantAdminService) {
        this.merchantAdminService = merchantAdminService;
    }

    @GetMapping
    public ApiResponse<PageResult<Merchant>> list(@RequestParam(value = "page", required = false) Integer page,
                                                  @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                                  @RequestParam(value = "keyword", required = false) String keyword,
                                                  @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.ok(merchantAdminService.list(keyword, status, page, pageSize));
    }

    @GetMapping("/{merchantId}")
    public ApiResponse<Merchant> get(@PathVariable("merchantId") Long merchantId) {
        return ApiResponse.ok(merchantAdminService.get(merchantId));
    }

    @PostMapping
    public ApiResponse<Merchant> create(@Valid @RequestBody CreateMerchantRequest req) {
        return ApiResponse.ok(merchantAdminService.create(req.getName(), req.getContactName(), req.getContactPhone(), req.getRemark()));
    }

    @PutMapping("/{merchantId}")
    public ApiResponse<Merchant> update(@PathVariable("merchantId") Long merchantId, @Valid @RequestBody UpdateMerchantRequest req) {
        return ApiResponse.ok(merchantAdminService.update(merchantId, req.getName(), req.getContactName(), req.getContactPhone(), req.getRemark()));
    }

    @PutMapping("/{merchantId}/status")
    public ApiResponse<Merchant> updateStatus(@PathVariable("merchantId") Long merchantId, @Valid @RequestBody UpdateMerchantStatusRequest req) {
        return ApiResponse.ok(merchantAdminService.updateStatus(merchantId, req.getStatus()));
    }

    @GetMapping("/{merchantId}/users")
    public ApiResponse<List<MerchantUserItem>> users(@PathVariable("merchantId") Long merchantId) {
        return ApiResponse.ok(merchantAdminService.listUsers(merchantId));
    }

    @PostMapping("/{merchantId}/users")
    public ApiResponse<Void> addUser(@PathVariable("merchantId") Long merchantId, @Valid @RequestBody AddMerchantUserRequest req) {
        merchantAdminService.addUser(merchantId, req.getUserId());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{merchantId}/users/{userId}")
    public ApiResponse<Void> removeUser(@PathVariable("merchantId") Long merchantId, @PathVariable("userId") Long userId) {
        merchantAdminService.removeUser(merchantId, userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{merchantId}/overview")
    public ApiResponse<MerchantOverview> overview(@PathVariable("merchantId") Long merchantId) {
        return ApiResponse.ok(merchantAdminService.overview(merchantId));
    }
}
