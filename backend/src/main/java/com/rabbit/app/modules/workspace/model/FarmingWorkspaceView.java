package com.rabbit.app.modules.workspace.model;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record FarmingWorkspaceView(
        String workspaceKey,
        Long resourceId,
        Long merchantId,
        Long ownerUserId,
        String name,
        String businessType,
        String businessName,
        List<String> capabilities,
        String role,
        List<String> permissions,
        String scopeHeader
) {
    public FarmingWorkspaceView {
        businessType = requireText(businessType, "businessType").toUpperCase(Locale.ROOT);
        resourceId = requirePositive(resourceId, "resourceId");
        merchantId = requirePositive(merchantId, "merchantId");
        ownerUserId = requirePositive(ownerUserId, "ownerUserId");
        workspaceKey = requireText(workspaceKey, "workspaceKey");
        if (!workspaceKey.equals(businessType + ":" + resourceId)) {
            throw new IllegalArgumentException("workspaceKey must match businessType and resourceId");
        }
        name = requireText(name, "name");
        businessName = requireText(businessName, "businessName");
        capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        role = requireText(role, "role");
        permissions = List.copyOf(Objects.requireNonNull(permissions, "permissions"));
        scopeHeader = requireText(scopeHeader, "scopeHeader");
    }

    public static String key(String businessType, Long resourceId) {
        return requireText(businessType, "businessType").toUpperCase(Locale.ROOT)
                + ":" + requirePositive(resourceId, "resourceId");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
