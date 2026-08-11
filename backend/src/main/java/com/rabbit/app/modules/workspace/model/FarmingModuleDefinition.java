package com.rabbit.app.modules.workspace.model;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record FarmingModuleDefinition(
        String code,
        String displayName,
        List<String> capabilities
) {
    public FarmingModuleDefinition {
        code = requireText(code, "code").toUpperCase(Locale.ROOT);
        displayName = requireText(displayName, "displayName");
        capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
