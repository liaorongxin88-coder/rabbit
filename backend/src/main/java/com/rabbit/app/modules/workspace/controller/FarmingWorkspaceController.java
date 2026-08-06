package com.rabbit.app.modules.workspace.controller;

import com.rabbit.app.common.ApiResponse;
import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.workspace.model.FarmingWorkspaceCatalog;
import com.rabbit.app.modules.workspace.service.FarmingWorkspaceService;
import com.rabbit.app.security.AuthContext;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
public class FarmingWorkspaceController {
    private final FarmingWorkspaceService workspaceService;

    public FarmingWorkspaceController(FarmingWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    @RequiresPermission(PermissionCode.MERCHANT_MEMBERSHIPS_LIST)
    public ApiResponse<FarmingWorkspaceCatalog> listMine(
            @RequestParam(value = "merchantId", required = false) Long merchantId
    ) {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return ApiResponse.ok(workspaceService.listForUser(userId, merchantId));
    }
}
