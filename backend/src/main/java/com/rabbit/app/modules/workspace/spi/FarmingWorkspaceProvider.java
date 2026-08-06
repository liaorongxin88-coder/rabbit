package com.rabbit.app.modules.workspace.spi;

import com.rabbit.app.modules.workspace.model.FarmingModuleDefinition;
import com.rabbit.app.modules.workspace.model.FarmingWorkspaceView;
import java.util.List;

/**
 * Extension point implemented by each concrete farming module.
 */
public interface FarmingWorkspaceProvider {
    FarmingModuleDefinition module();

    List<FarmingWorkspaceView> listForUser(Long userId);
}
