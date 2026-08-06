package com.rabbit.app.security.permission;

import java.util.Arrays;
import java.util.List;

public enum PermissionCode {
    ACCOUNT_PROFILE_QUERY("account:profile:query", PermissionScope.BUSINESS, 1),
    ACCOUNT_PROFILE_EDIT("account:profile:edit", PermissionScope.BUSINESS, 1),
    ACCOUNT_PASSWORD_EDIT("account:password:edit", PermissionScope.BUSINESS, 1),
    MERCHANT_MEMBERSHIPS_LIST("merchant:memberships:list", PermissionScope.BUSINESS, 1),
    RABBIT_HOUSES_LIST("rabbit:houses:list", PermissionScope.BUSINESS, 1),
    RABBIT_HOUSES_ADD("rabbit:houses:add", PermissionScope.BUSINESS, 1),
    USER_SETTINGS_QUERY("account:settings:query", PermissionScope.BUSINESS, 1),
    USER_SETTINGS_EDIT("account:settings:edit", PermissionScope.BUSINESS, 1),
    DASHBOARD_QUERY("rabbit:dashboard:query", PermissionScope.BUSINESS, 1),

    MERCHANT_MEMBERS_LIST("merchant:members:list", PermissionScope.MERCHANT, MerchantRole.OWNER.rank()),
    MERCHANT_MEMBERS_ADD("merchant:members:add", PermissionScope.MERCHANT, MerchantRole.OWNER.rank()),
    MERCHANT_MEMBERS_EDIT("merchant:members:edit", PermissionScope.MERCHANT, MerchantRole.OWNER.rank()),
    MERCHANT_MEMBERS_REMOVE("merchant:members:remove", PermissionScope.MERCHANT, MerchantRole.OWNER.rank()),
    MERCHANT_HOUSES_ADD("merchant:houses:add", PermissionScope.MERCHANT, MerchantRole.ADMIN.rank()),

    RABBIT_HOUSES_QUERY("rabbit:houses:query", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_HOUSES_EDIT("rabbit:houses:edit", PermissionScope.HOUSE, HouseRole.MANAGER.rank()),
    RABBIT_HOUSES_REMOVE("rabbit:houses:remove", PermissionScope.HOUSE, HouseRole.OWNER.rank()),
    RABBIT_HOUSE_MEMBERS_LIST("rabbit:house-members:list", PermissionScope.HOUSE, HouseRole.OWNER.rank()),
    RABBIT_HOUSE_MEMBERS_QUERY("rabbit:house-members:query", PermissionScope.HOUSE, HouseRole.OWNER.rank()),
    RABBIT_HOUSE_MEMBERS_ADD("rabbit:house-members:add", PermissionScope.HOUSE, HouseRole.OWNER.rank()),
    RABBIT_HOUSE_MEMBERS_EDIT("rabbit:house-members:edit", PermissionScope.HOUSE, HouseRole.OWNER.rank()),
    RABBIT_HOUSE_MEMBERS_REMOVE("rabbit:house-members:remove", PermissionScope.HOUSE, HouseRole.OWNER.rank()),
    RABBIT_HOUSE_MEMBERS_LEAVE("rabbit:house-members:leave", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_CAGES_LIST("rabbit:cages:list", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_CAGES_QUERY("rabbit:cages:query", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_CAGES_ADD("rabbit:cages:add", PermissionScope.HOUSE, HouseRole.MANAGER.rank()),
    RABBIT_CAGES_EDIT("rabbit:cages:edit", PermissionScope.HOUSE, HouseRole.MANAGER.rank()),
    RABBIT_CAGES_REMOVE("rabbit:cages:remove", PermissionScope.HOUSE, HouseRole.MANAGER.rank()),
    RABBIT_CAGES_RECOUNT("rabbit:cages:recount", PermissionScope.HOUSE, HouseRole.MANAGER.rank()),
    RABBIT_RABBITS_LIST("rabbit:rabbits:list", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_RABBITS_QUERY("rabbit:rabbits:query", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_RABBITS_ADD("rabbit:rabbits:add", PermissionScope.HOUSE, HouseRole.STAFF.rank()),
    RABBIT_RABBITS_EDIT("rabbit:rabbits:edit", PermissionScope.HOUSE, HouseRole.STAFF.rank()),
    RABBIT_RABBITS_CONTROL("rabbit:rabbits:control", PermissionScope.HOUSE, HouseRole.MANAGER.rank()),
    RABBIT_BATCHES_LIST("rabbit:batches:list", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_BATCHES_QUERY("rabbit:batches:query", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_BATCHES_ADD("rabbit:batches:add", PermissionScope.HOUSE, HouseRole.STAFF.rank()),
    RABBIT_BATCHES_EDIT("rabbit:batches:edit", PermissionScope.HOUSE, HouseRole.STAFF.rank()),
    RABBIT_EVENTS_LIST("rabbit:events:list", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_EVENTS_ACK("rabbit:events:ack", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_MAINTENANCE_EXECUTE("rabbit:maintenance:execute", PermissionScope.HOUSE, HouseRole.MANAGER.rank()),
    RABBIT_FEED_LIST("rabbit:feed:list", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_FEED_ADD("rabbit:feed:add", PermissionScope.HOUSE, HouseRole.STAFF.rank()),
    RABBIT_INVENTORY_LIST("rabbit:inventory:list", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_INVENTORY_EXPORT("rabbit:inventory:export", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_INVENTORY_EDIT("rabbit:inventory:edit", PermissionScope.HOUSE, HouseRole.STAFF.rank()),
    RABBIT_AUDIT_LIST("rabbit:audit:list", PermissionScope.HOUSE, HouseRole.MANAGER.rank()),
    RABBIT_AUDIT_EXPORT("rabbit:audit:export", PermissionScope.HOUSE, HouseRole.MANAGER.rank()),
    RABBIT_OUTBOUND_LIST("rabbit:outbound:list", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_OUTBOUND_QUERY("rabbit:outbound:query", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_OUTBOUND_EDIT("rabbit:outbound:edit", PermissionScope.HOUSE, HouseRole.STAFF.rank()),
    RABBIT_ABNORMAL_LIST("rabbit:abnormal:list", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_ABNORMAL_EDIT("rabbit:abnormal:edit", PermissionScope.HOUSE, HouseRole.STAFF.rank()),
    RABBIT_RECORDS_LIST("rabbit:records:list", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_REPORTS_QUERY("rabbit:reports:query", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_REPORTS_EXPORT("rabbit:reports:export", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_SALES_LIST("rabbit:sales:list", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_SALES_QUERY("rabbit:sales:query", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_SALES_ADD("rabbit:sales:add", PermissionScope.HOUSE, HouseRole.STAFF.rank()),
    RABBIT_SETTINGS_QUERY("rabbit:settings:query", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_SETTINGS_EDIT("rabbit:settings:edit", PermissionScope.HOUSE, HouseRole.MANAGER.rank()),
    RABBIT_TREATMENTS_LIST("rabbit:treatments:list", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_TREATMENTS_EDIT("rabbit:treatments:edit", PermissionScope.HOUSE, HouseRole.STAFF.rank()),
    RABBIT_WEIGHTS_LIST("rabbit:weights:list", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_WEIGHTS_ADD("rabbit:weights:add", PermissionScope.HOUSE, HouseRole.STAFF.rank()),
    RABBIT_NFC_QUERY("rabbit:nfc:query", PermissionScope.HOUSE, HouseRole.VIEWER.rank()),
    RABBIT_NFC_CONTROL("rabbit:nfc:control", PermissionScope.HOUSE, HouseRole.MANAGER.rank()),
    RABBIT_HARDWARE_CONTROL("rabbit:hardware:control", PermissionScope.HOUSE, HouseRole.MANAGER.rank()),

    PLATFORM_MERCHANTS_LIST("platform:merchants:list", PermissionScope.PLATFORM, PlatformRole.ADMIN.rank()),
    PLATFORM_MERCHANTS_QUERY("platform:merchants:query", PermissionScope.PLATFORM, PlatformRole.ADMIN.rank()),
    PLATFORM_MERCHANTS_ADD("platform:merchants:add", PermissionScope.PLATFORM, PlatformRole.ADMIN.rank()),
    PLATFORM_MERCHANTS_EDIT("platform:merchants:edit", PermissionScope.PLATFORM, PlatformRole.ADMIN.rank()),
    PLATFORM_MERCHANT_ACCOUNTS_LIST("platform:merchant-accounts:list", PermissionScope.PLATFORM, PlatformRole.ADMIN.rank()),
    PLATFORM_MERCHANT_ACCOUNTS_ADD("platform:merchant-accounts:add", PermissionScope.PLATFORM, PlatformRole.ADMIN.rank()),
    PLATFORM_MERCHANT_MEMBERSHIP_EDIT("platform:merchant-membership:edit", PermissionScope.PLATFORM, PlatformRole.ADMIN.rank()),
    PLATFORM_MERCHANT_POLICY_QUERY("platform:merchant-policy:query", PermissionScope.PLATFORM, PlatformRole.ADMIN.rank()),
    PLATFORM_MERCHANT_POLICY_EDIT("platform:merchant-policy:edit", PermissionScope.PLATFORM, PlatformRole.ADMIN.rank()),
    PLATFORM_MERCHANT_OVERVIEW_QUERY("platform:merchant-overview:query", PermissionScope.PLATFORM, PlatformRole.ADMIN.rank()),
    PLATFORM_ACCOUNTS_LIST("platform:accounts:list", PermissionScope.PLATFORM, PlatformRole.SUPER_ADMIN.rank()),
    PLATFORM_ACCOUNTS_QUERY("platform:accounts:query", PermissionScope.PLATFORM, PlatformRole.SUPER_ADMIN.rank()),
    PLATFORM_ACCOUNTS_ADD("platform:accounts:add", PermissionScope.PLATFORM, PlatformRole.SUPER_ADMIN.rank()),
    PLATFORM_ACCOUNTS_EDIT("platform:accounts:edit", PermissionScope.PLATFORM, PlatformRole.SUPER_ADMIN.rank()),
    PLATFORM_ACCOUNTS_REMOVE("platform:accounts:remove", PermissionScope.PLATFORM, PlatformRole.SUPER_ADMIN.rank()),
    PLATFORM_GLOBAL_MERCHANT_ACCOUNTS_LIST("platform:global-merchant-accounts:list", PermissionScope.PLATFORM, PlatformRole.SUPER_ADMIN.rank()),
    PLATFORM_GLOBAL_MERCHANT_ACCOUNTS_EDIT("platform:global-merchant-accounts:edit", PermissionScope.PLATFORM, PlatformRole.SUPER_ADMIN.rank());

    private final String code;
    private final PermissionScope scope;
    private final int minimumRank;

    PermissionCode(String code, PermissionScope scope, int minimumRank) {
        this.code = code;
        this.scope = scope;
        this.minimumRank = minimumRank;
    }

    public String code() {
        return code;
    }

    public PermissionScope scope() {
        return scope;
    }

    public int minimumRank() {
        return minimumRank;
    }

    public static List<String> granted(PermissionScope scope, ScopedRole role) {
        return Arrays.stream(values())
                .filter(permission -> permission.scope == scope && role.rank() >= permission.minimumRank)
                .map(PermissionCode::code)
                .sorted()
                .toList();
    }

    public static List<String> all(PermissionScope scope) {
        return Arrays.stream(values())
                .filter(permission -> permission.scope == scope)
                .map(PermissionCode::code)
                .sorted()
                .toList();
    }
}
