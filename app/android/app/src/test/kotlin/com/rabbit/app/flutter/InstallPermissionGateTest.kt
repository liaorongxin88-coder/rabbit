package com.rabbit.app.flutter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallPermissionGateTest {
    @Test
    fun `旧版 Android 不要求未知来源安装权限`() {
        var checks = 0
        val gate =
            InstallPermissionGate(
                permissionRequired = false,
                canRequestPackageInstalls = {
                    checks += 1
                    false
                },
            )

        assertTrue(gate.canInstallPackages())
        assertTrue(gate.canInstallPackages())
        assertTrue("无需授权时不应读取 PackageManager", checks == 0)
    }

    @Test
    fun `每次安装前都重新读取授权状态`() {
        var granted = false
        var checks = 0
        val gate =
            InstallPermissionGate(
                permissionRequired = true,
                canRequestPackageInstalls = {
                    checks += 1
                    granted
                },
            )

        assertFalse(gate.canInstallPackages())
        granted = true
        assertTrue(gate.canInstallPackages())
        granted = false
        assertFalse(gate.canInstallPackages())
        assertTrue("拒绝、授权和撤销都必须重新检查", checks == 3)
    }
}
