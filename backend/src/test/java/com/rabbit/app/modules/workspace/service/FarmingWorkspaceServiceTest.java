package com.rabbit.app.modules.workspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rabbit.app.modules.workspace.model.FarmingModuleDefinition;
import com.rabbit.app.modules.workspace.model.FarmingWorkspaceCatalog;
import com.rabbit.app.modules.workspace.model.FarmingWorkspaceView;
import com.rabbit.app.modules.workspace.spi.FarmingWorkspaceProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class FarmingWorkspaceServiceTest {
    @Test
    void aggregatesModulesAndOnlyNarrowsByMerchant() {
        FarmingWorkspaceProvider rabbit = provider(
                "rabbit",
                workspace("RABBIT", 10L, 100L, "甲兔场"),
                workspace("RABBIT", 11L, 200L, "乙兔场")
        );
        FarmingWorkspaceProvider poultry = provider(
                "poultry",
                workspace("POULTRY", 20L, 100L, "鸡舍")
        );
        FarmingWorkspaceService service = new FarmingWorkspaceService(List.of(rabbit, poultry));

        FarmingWorkspaceCatalog catalog = service.listForUser(7L, 100L);

        assertEquals(List.of("POULTRY", "RABBIT"), catalog.modules().stream()
                .map(FarmingModuleDefinition::code)
                .toList());
        assertEquals(List.of("RABBIT:10", "POULTRY:20"), catalog.workspaces().stream()
                .map(FarmingWorkspaceView::workspaceKey)
                .toList());
    }

    @Test
    void rejectsDuplicateModuleCodesRegardlessOfCase() {
        FarmingWorkspaceProvider first = provider("rabbit");
        FarmingWorkspaceProvider duplicate = provider("RABBIT");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new FarmingWorkspaceService(List.of(first, duplicate))
        );

        assertEquals("duplicate farming module code: RABBIT", error.getMessage());
    }

    private FarmingWorkspaceProvider provider(String code, FarmingWorkspaceView... workspaces) {
        FarmingModuleDefinition module = new FarmingModuleDefinition(code, code + " farming", List.of("members"));
        return new FarmingWorkspaceProvider() {
            @Override
            public FarmingModuleDefinition module() {
                return module;
            }

            @Override
            public List<FarmingWorkspaceView> listForUser(Long userId) {
                return List.of(workspaces);
            }
        };
    }

    private FarmingWorkspaceView workspace(String businessType, Long resourceId, Long merchantId, String name) {
        return new FarmingWorkspaceView(
                FarmingWorkspaceView.key(businessType, resourceId),
                resourceId,
                merchantId,
                7L,
                name,
                businessType,
                businessType + " farming",
                List.of("members"),
                "VIEWER",
                List.of("workspace:query"),
                "X-Workspace-Id"
        );
    }
}
