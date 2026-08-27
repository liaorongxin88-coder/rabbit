package com.rabbit.app.flutter

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.security.MessageDigest

class OtaUpdateChannel(
    messenger: BinaryMessenger,
    private val activity: Activity,
) : MethodChannel.MethodCallHandler {
    companion object {
        private const val CHANNEL = "com.rabbit.app.flutter/app_update"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }

    private val channel = MethodChannel(messenger, CHANNEL)

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
    ) {
        when (call.method) {
            "appInfo" -> appInfo(result)
            "updateDirectory" -> updateDirectory(result)
            "canInstallPackages" -> result.success(canInstallPackages())
            "openInstallPermissionSettings" -> openInstallPermissionSettings(result)
            "installApk" -> installApk(call, result)
            else -> result.notImplemented()
        }
    }

    fun dispose() {
        channel.setMethodCallHandler(null)
    }

    @Suppress("DEPRECATION")
    private fun appInfo(result: MethodChannel.Result) {
        val packageInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.packageManager.getPackageInfo(
                    activity.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                activity.packageManager.getPackageInfo(activity.packageName, 0)
            }
        val buildNumber =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
        result.success(
            mapOf(
                "versionName" to (packageInfo.versionName ?: ""),
                "buildNumber" to buildNumber,
            ),
        )
    }

    private fun updateDirectory(result: MethodChannel.Result) {
        val directory = File(activity.cacheDir, "ota")
        if (!directory.exists() && !directory.mkdirs()) {
            result.error("DIRECTORY_UNAVAILABLE", "无法创建升级下载目录", null)
            return
        }
        result.success(directory.absolutePath)
    }

    private fun openInstallPermissionSettings(result: MethodChannel.Result) {
        try {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
            result.success(null)
        } catch (_: ActivityNotFoundException) {
            result.error("SETTINGS_UNAVAILABLE", "无法打开安装授权页面", null)
        }
    }

    private fun installApk(
        call: MethodCall,
        result: MethodChannel.Result,
    ) {
        val path = call.argument<String>("path")
        val expectedSha256 = call.argument<String>("sha256")
        if (path.isNullOrBlank() || expectedSha256.isNullOrBlank()) {
            result.success("INVALID_APK")
            return
        }
        if (!canInstallPackages()) {
            result.success("PERMISSION_REQUIRED")
            return
        }
        val apk = File(path)
        if (!apk.isFile || !apk.canRead()) {
            result.success("INVALID_APK")
            return
        }
        if (!apkSha256(apk).equals(expectedSha256, ignoreCase = true)) {
            result.success("HASH_MISMATCH")
            return
        }
        val uri =
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.ota.fileprovider",
                apk,
            )
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activity.startActivity(intent)
            result.success("INSTALLER_OPENED")
        } catch (_: ActivityNotFoundException) {
            result.success("INVALID_APK")
        }
    }

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            activity.packageManager.canRequestPackageInstalls()

    private fun apkSha256(apk: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
