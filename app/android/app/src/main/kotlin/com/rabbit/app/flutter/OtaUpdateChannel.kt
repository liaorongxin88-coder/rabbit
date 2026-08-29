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

    /**
     * 所有分支都包一层异常兜底。
     *
     * <p>Flutter 的 MethodChannel 只 catch RuntimeException。受检异常
     * (例如 FileProvider 解析元数据抛的 XmlPullParserException) 会一路逃到
     * DartMessenger，它记一条日志然后回 null，Dart 侧把 null 回包当成
     * MissingPluginException，界面上就变成「设备不支持」这种查不下去的提示。
     * 在这里收口，保证任何失败都带着原因回到 Dart。
     */
    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
    ) {
        val guarded = ReplyOnce(result)
        try {
            when (call.method) {
                "appInfo" -> appInfo(guarded)
                "updateDirectory" -> updateDirectory(guarded)
                "canInstallPackages" -> guarded.success(canInstallPackages())
                "openInstallPermissionSettings" -> openInstallPermissionSettings(guarded)
                "installApk" -> installApk(call, guarded)
                else -> guarded.notImplemented()
            }
        } catch (throwable: Throwable) {
            guarded.error(
                "CHANNEL_FAILURE",
                "${call.method} 执行失败：${throwable.javaClass.simpleName}: ${throwable.message}",
                null,
            )
        }
    }

    /** 保证一次调用只回一次，兜底 catch 才不会撞上「已经回过了」。 */
    private class ReplyOnce(
        private val delegate: MethodChannel.Result,
    ) : MethodChannel.Result {
        private var replied = false

        override fun success(value: Any?) {
            if (replied) return
            replied = true
            delegate.success(value)
        }

        override fun error(
            code: String,
            message: String?,
            details: Any?,
        ) {
            if (replied) return
            replied = true
            delegate.error(code, message, details)
        }

        override fun notImplemented() {
            if (replied) return
            replied = true
            delegate.notImplemented()
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
        // getUriForFile 会去读 @xml/ota_file_paths。那个资源只被清单引用，曾经
        // 被 shrinkResources 删掉过，导致这里抛受检异常。keep.xml 保住了资源，
        // 这里再兜一层，装不上至少能说清是哪一步的问题。
        val uri =
            try {
                FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.ota.fileprovider",
                    apk,
                )
            } catch (throwable: Throwable) {
                result.error(
                    "FILE_PROVIDER_UNAVAILABLE",
                    "无法生成安装包访问地址：${throwable.javaClass.simpleName}: ${throwable.message}",
                    null,
                )
                return
            }
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
