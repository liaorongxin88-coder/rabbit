package com.rabbit.app.flutter

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL = "com.rabbit.app.flutter/nfc_intents"
        private const val EXTERNAL_TYPE = "dzht.top:rabbit-cage"

        /**
         * 只供验收使用的标签注入动作。
         *
         * 模拟器没有 NFC 硬件，实体标签又有限，而读取路径的错误分支（签名被改、
         * 跨兔舍）本来就需要写出“坏”标签才能覆盖。注入点故意选在与真实读卡
         * 完全相同的位置：只给原始 payload 字符串，后续的后端签名校验、兔舍归属
         * 判断、笼位解析一律照走，绕过的仅仅是射频和 NDEF 解析。
         */
        private const val DEBUG_TAG_ACTION = "com.rabbit.app.flutter.DEBUG_NFC_TAG"
    }

    private var channel: MethodChannel? = null
    private var carrierAuthChannel: CarrierAuthChannel? = null
    private var otaUpdateChannel: OtaUpdateChannel? = null
    private var pendingEvent: Map<String, Any>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureNfcIntent(intent)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channel =
            MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).also { methodChannel ->
                methodChannel.setMethodCallHandler { call, result ->
                    when (call.method) {
                        "takePendingIntent" -> {
                            result.success(pendingEvent)
                            pendingEvent = null
                        }

                        else -> {
                            result.notImplemented()
                        }
                    }
                }
            }
        carrierAuthChannel =
            CarrierAuthChannel(
                flutterEngine.dartExecutor.binaryMessenger,
                UnavailableCarrierAuthAdapter(),
            )
        otaUpdateChannel =
            OtaUpdateChannel(
                flutterEngine.dartExecutor.binaryMessenger,
                this,
            )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureNfcIntent(intent)
    }

    override fun onDestroy() {
        carrierAuthChannel?.dispose()
        carrierAuthChannel = null
        otaUpdateChannel?.dispose()
        otaUpdateChannel = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun captureNfcIntent(intent: Intent?) {
        if (captureInjectedTag(intent)) return
        if (intent?.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return
        val messages =
            intent
                .getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                ?.mapNotNull { it as? NdefMessage }
                .orEmpty()
        val record = messages.firstOrNull()?.records?.firstOrNull() ?: return
        if (String(record.type, Charsets.US_ASCII) != EXTERNAL_TYPE) return

        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        val event =
            mapOf(
                "payload" to String(record.payload, Charsets.US_ASCII),
                "tagUid" to (tag?.id?.joinToString("") { byte -> "%02X".format(byte.toInt() and 0xFF) } ?: ""),
                "receivedAt" to System.currentTimeMillis(),
            )
        pendingEvent = event
        channel?.invokeMethod("nfcIntent", event)
    }

    /**
     * 处理验收用的注入标签，返回是否已接管该 intent。
     *
     * 发布包不可调试，FLAG_DEBUGGABLE 为 0，此时直接忽略注入：外部应用即使发来
     * 这个 action 也不会被当成标签。即便在调试包里，payload 仍需通过服务端
     * HMAC 校验，所以伪造不出一个能用的笼位绑定。
     */
    private fun captureInjectedTag(intent: Intent?): Boolean {
        if (intent?.action != DEBUG_TAG_ACTION) return false
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return true

        val payload = intent.getStringExtra("payload") ?: return true
        val event =
            mapOf(
                "payload" to payload,
                "tagUid" to (intent.getStringExtra("tagUid") ?: "INJECTED-TAG"),
                "receivedAt" to System.currentTimeMillis(),
            )
        pendingEvent = event
        channel?.invokeMethod("nfcIntent", event)
        return true
    }
}
