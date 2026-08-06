package com.rabbit.app.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbit.app.security.permission.HouseRole;
import com.rabbit.app.security.permission.MerchantRole;
import com.rabbit.app.security.permission.PermissionCode;
import com.rabbit.app.security.permission.PermissionScope;
import com.rabbit.app.security.permission.PlatformRole;
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
    void merchantAndPlatformRolesKeepTheirOwnScopes() {
        List<String> merchantAdmin = PermissionCode.granted(PermissionScope.MERCHANT, MerchantRole.ADMIN);
        List<String> merchantOwner = PermissionCode.granted(PermissionScope.MERCHANT, MerchantRole.OWNER);
        List<String> platformAdmin = PermissionCode.granted(PermissionScope.PLATFORM, PlatformRole.ADMIN);
        List<String> superAdmin = PermissionCode.granted(PermissionScope.PLATFORM, PlatformRole.SUPER_ADMIN);

        assertTrue(merchantAdmin.contains("merchant:houses:add"));
        assertFalse(merchantAdmin.contains("merchant:members:list"));
        assertTrue(merchantOwner.contains("merchant:members:list"));
        assertTrue(platformAdmin.contains("platform:merchants:list"));
        assertFalse(platformAdmin.contains("platform:accounts:list"));
        assertTrue(superAdmin.contains("platform:accounts:list"));
    }

    @Test
    void authenticatedBusinessUsersReceiveBusinessPermissionsOnly() {
        List<String> business = PermissionCode.all(PermissionScope.BUSINESS);

        assertTrue(business.contains("account:profile:query"));
        assertTrue(business.contains("rabbit:houses:list"));
        assertFalse(business.contains("merchant:members:list"));
        assertFalse(business.contains("rabbit:rabbits:list"));
    }
}
