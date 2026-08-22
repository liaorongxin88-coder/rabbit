package com.rabbit.app.modules.workspace.model;

import java.util.List;

public record FarmingWorkspaceCatalog(
        List<FarmingModuleDefinition> modules,
        List<FarmingWorkspaceView> workspaces
) {
    public FarmingWorkspaceCatalog {
        modules = List.copyOf(modules);
        workspaces = List.copyOf(workspaces);
    }
}
