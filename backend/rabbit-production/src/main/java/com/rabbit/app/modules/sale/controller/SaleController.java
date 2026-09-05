package com.rabbit.app.modules.sale.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.sale.dto.CreateSaleOrderRequest;
import com.rabbit.app.modules.sale.dto.SaleOrderDetail;
import com.rabbit.app.modules.sale.entity.SaleOrder;
import com.rabbit.app.modules.sale.service.SaleService;
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
@RequiresPermission(PermissionCode.RABBIT_SALES_LIST)
public class SaleController {
    private final HouseService houseService;
    private final SaleService saleService;

    public SaleController(HouseService houseService, SaleService saleService) {
        this.houseService = houseService;
        this.saleService = saleService;
    }

    @PostMapping("/sales")
    @RequiresPermission(PermissionCode.RABBIT_SALES_ADD)
    public ApiResponse<SaleOrder> create(@RequestHeader("X-House-Id") Long houseId, @Valid @RequestBody CreateSaleOrderRequest req) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "edit");
        saleService.assertRequestAllowed(userId, houseId, req);
        return ApiResponse.ok(saleService.create(userId, houseId, req));
    }

    @GetMapping("/sales")
    public ApiResponse<List<SaleOrder>> list(@RequestHeader("X-House-Id") Long houseId,
                                            @RequestParam(value = "page", required = false) Integer page,
                                            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(saleService.listPage(houseId, page == null ? 1 : page, pageSize == null ? 20 : pageSize));
    }

    @GetMapping("/sales/{id}")
    @RequiresPermission(PermissionCode.RABBIT_SALES_QUERY)
    public ApiResponse<SaleOrderDetail> detail(@RequestHeader("X-House-Id") Long houseId, @PathVariable("id") Long id) {
        Long userId = requireLogin();
        houseService.assertHousePermission(userId, houseId, "view");
        return ApiResponse.ok(saleService.getDetail(houseId, id));
    }

    private Long requireLogin() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
