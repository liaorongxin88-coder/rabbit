package com.rabbit.app.flutter

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File

class ApkInstallerChannel(
    private val activity: MainActivity,
    messenger: BinaryMessenger,
) : MethodChannel.MethodCallHandler {
    companion object {
        private const val CHANNEL = "com.rabbit.app.flutter/apk_installer"
    }

    private val channel = MethodChannel(messenger, CHANNEL)

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "install" -> install(call.argument<String>("path"), result)
            else -> result.notImplemented()
        }
    }

    private fun install(path: String?, result: MethodChannel.Result) {
        if (path.isNullOrBlank()) {
            result.error("INVALID_PATH", "安装包路径为空", null)
            return
        }
        val file = File(path)
        if (!file.isFile) {
            result.error("MISSING_FILE", "安装包不存在", null)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            settings.data = Uri.parse("package:${activity.packageName}")
            activity.startActivity(settings)
            result.error("PERMISSION", "请先允许安装未知应用，然后再点立即安装", null)
            return
        }

        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
        result.success(null)
    }

    fun dispose() {
        channel.setMethodCallHandler(null)
    }
}
