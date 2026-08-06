package com.rabbit.app.modules.house.workspace;

import com.rabbit.app.modules.house.dto.HousePermissionInfo;
import com.rabbit.app.modules.house.entity.RabbitHouse;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.workspace.model.FarmingModuleDefinition;
import com.rabbit.app.modules.workspace.model.FarmingWorkspaceView;
import com.rabbit.app.modules.workspace.model.WorkspaceCapability;
import com.rabbit.app.modules.workspace.spi.FarmingWorkspaceProvider;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RabbitHouseWorkspaceProvider implements FarmingWorkspaceProvider {
    private static final String BUSINESS_TYPE = "RABBIT";
    private static final String SCOPE_HEADER = "X-House-Id";
    private static final FarmingModuleDefinition MODULE = new FarmingModuleDefinition(
            BUSINESS_TYPE,
            "兔养殖",
            Arrays.stream(WorkspaceCapability.values()).map(WorkspaceCapability::code).toList()
    );

    private final HouseService houseService;

    public RabbitHouseWorkspaceProvider(HouseService houseService) {
        this.houseService = houseService;
    }

    @Override
    public FarmingModuleDefinition module() {
        return MODULE;
    }

    @Override
    public List<FarmingWorkspaceView> listForUser(Long userId) {
        return houseService.listMyHouses(userId).stream()
                .map(house -> toWorkspace(userId, house))
                .toList();
    }

    private FarmingWorkspaceView toWorkspace(Long userId, RabbitHouse house) {
        HousePermissionInfo access = houseService.getMyHousePermission(userId, house.getId());
        return new FarmingWorkspaceView(
                FarmingWorkspaceView.key(BUSINESS_TYPE, house.getId()),
                house.getId(),
                house.getMerchantId(),
                house.getOwnerUserId(),
                house.getName(),
                BUSINESS_TYPE,
                MODULE.displayName(),
                MODULE.capabilities(),
                access.getRole(),
                access.getPermissions(),
                SCOPE_HEADER
        );
    }
}
