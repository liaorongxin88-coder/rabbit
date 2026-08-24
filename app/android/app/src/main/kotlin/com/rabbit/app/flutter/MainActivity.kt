package com.rabbit.app.flutter

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL = "com.rabbit.app.flutter/nfc_intents"
        private const val EXTERNAL_TYPE = "dzht.top:rabbit-cage"
    }

    private var channel: MethodChannel? = null
    private var carrierAuthChannel: CarrierAuthChannel? = null
    private var apkInstallerChannel: ApkInstallerChannel? = null
    private var pendingEvent: Map<String, Any>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureNfcIntent(intent)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).also { methodChannel ->
            methodChannel.setMethodCallHandler { call, result ->
                when (call.method) {
                    "takePendingIntent" -> {
                        result.success(pendingEvent)
                        pendingEvent = null
                    }
                    else -> result.notImplemented()
                }
            }
        }
        carrierAuthChannel = CarrierAuthChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            UnavailableCarrierAuthAdapter(),
        )
        apkInstallerChannel = ApkInstallerChannel(
            this,
            flutterEngine.dartExecutor.binaryMessenger,
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
        apkInstallerChannel?.dispose()
        apkInstallerChannel = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun captureNfcIntent(intent: Intent?) {
        if (intent?.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return
        val messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            ?.mapNotNull { it as? NdefMessage }
            .orEmpty()
        val record = messages.firstOrNull()?.records?.firstOrNull() ?: return
        if (String(record.type, Charsets.US_ASCII) != EXTERNAL_TYPE) return

        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        val event = mapOf(
            "payload" to String(record.payload, Charsets.US_ASCII),
            "tagUid" to (tag?.id?.joinToString("") { byte -> "%02X".format(byte.toInt() and 0xFF) } ?: ""),
            "receivedAt" to System.currentTimeMillis(),
        )
        pendingEvent = event
        channel?.invokeMethod("nfcIntent", event)
    }
}
