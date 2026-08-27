package com.rabbit.app.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbit.app.security.permission.HouseRole;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.PermissionScope;
import com.rabbit.app.security.permission.PlatformRole;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionModelTest {
    @Test
    void houseRolesExposeActionPermissionsByRank() {
        List<String> viewer = PermissionCode.granted(PermissionScope.HOUSE, HouseRole.VIEWER);
        List<String> staff = PermissionCode.granted(PermissionScope.HOUSE, HouseRole.STAFF);
        List<String> manager = PermissionCode.granted(PermissionScope.HOUSE, HouseRole.MANAGER);
        List<String> owner = PermissionCode.granted(PermissionScope.HOUSE, HouseRole.OWNER);

        assertTrue(viewer.contains("rabbit:rabbits:list"));
        assertFalse(viewer.contains("rabbit:rabbits:add"));
        assertTrue(staff.contains("rabbit:rabbits:add"));
        assertFalse(staff.contains("rabbit:houses:edit"));
        assertTrue(manager.contains("rabbit:houses:edit"));
        assertFalse(manager.contains("rabbit:house-members:list"));
        assertTrue(owner.contains("rabbit:house-members:list"));
    }

    @Test
    void platformRolesExposeFarmAndUserAdministrationWithoutMerchantPermissions() {
        List<String> platformAdmin = PermissionCode.granted(PermissionScope.PLATFORM, PlatformRole.ADMIN);
        List<String> superAdmin = PermissionCode.granted(PermissionScope.PLATFORM, PlatformRole.SUPER_ADMIN);

        assertTrue(platformAdmin.contains("platform:farms:list"));
        assertTrue(platformAdmin.contains("platform:users:list"));
        assertFalse(platformAdmin.contains("platform:accounts:list"));
        assertTrue(superAdmin.contains("platform:accounts:list"));
        assertTrue(Arrays.stream(PermissionCode.values())
                .map(PermissionCode::code)
                .noneMatch(code -> code.startsWith("merchant:") || code.startsWith("platform:merchant")));
    }

    @Test
    void authenticatedBusinessUsersReceiveBusinessPermissionsOnly() {
        List<String> business = PermissionCode.all(PermissionScope.BUSINESS);

        assertTrue(business.contains("account:profile:query"));
        assertTrue(business.contains("rabbit:houses:list"));
        assertTrue(business.contains("workspaces:list"));
        assertTrue(business.stream().noneMatch(code -> code.startsWith("merchant:")));
        assertFalse(business.contains("rabbit:rabbits:list"));
    }

    @Test
    void theOnlyAuthorizationScopesAreBusinessHouseAndPlatform() {
        assertEquals(
                List.of("BUSINESS", "HOUSE", "PLATFORM"),
                Arrays.stream(PermissionScope.values()).map(Enum::name).toList()
        );
        assertEquals(
                List.of("VIEWER", "STAFF", "MANAGER", "OWNER"),
                Arrays.stream(HouseRole.values()).map(Enum::name).toList()
        );
    }
}
