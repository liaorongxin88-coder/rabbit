package com.rabbit.app.modules.workspace.service;

import com.rabbit.app.modules.workspace.model.FarmingModuleDefinition;
import com.rabbit.app.modules.workspace.model.FarmingWorkspaceCatalog;
import com.rabbit.app.modules.workspace.model.FarmingWorkspaceView;
import com.rabbit.app.modules.workspace.spi.FarmingWorkspaceProvider;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class FarmingWorkspaceService {
    private final List<FarmingWorkspaceProvider> providers;

    public FarmingWorkspaceService(List<FarmingWorkspaceProvider> providers) {
        this.providers = providers.stream()
                .sorted(Comparator.comparing(provider -> provider.module().code()))
                .toList();
        rejectDuplicateModuleCodes(this.providers);
    }

    public FarmingWorkspaceCatalog listForUser(Long userId) {
        List<FarmingModuleDefinition> modules = providers.stream()
                .map(FarmingWorkspaceProvider::module)
                .toList();
        List<FarmingWorkspaceView> workspaces = providers.stream()
                .flatMap(provider -> provider.listForUser(userId).stream())
                .sorted(Comparator
                        .comparing(FarmingWorkspaceView::name)
                        .thenComparing(FarmingWorkspaceView::workspaceKey))
                .toList();
        return new FarmingWorkspaceCatalog(modules, workspaces);
    }

    private static void rejectDuplicateModuleCodes(List<FarmingWorkspaceProvider> providers) {
        Set<String> codes = new HashSet<String>();
        for (FarmingWorkspaceProvider provider : providers) {
            String code = provider.module().code();
            if (!codes.add(code)) {
                throw new IllegalArgumentException("duplicate farming module code: " + code);
            }
        }
    }
}
