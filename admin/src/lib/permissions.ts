export interface PermissionGrant {
  permissions?: readonly string[]
}

export function hasPermission(
  grant: PermissionGrant | null | undefined,
  permission: string,
) {
  return grant?.permissions?.includes(permission) ?? false
}
