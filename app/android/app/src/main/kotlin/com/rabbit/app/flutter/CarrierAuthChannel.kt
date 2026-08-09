package com.rabbit.app.flutter

import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class CarrierAuthChannel(
    messenger: BinaryMessenger,
    private val adapter: CarrierAuthAdapter,
) : MethodChannel.MethodCallHandler {
    companion object {
        private const val CHANNEL = "com.rabbit.app.flutter/carrier_auth"
    }

    private val channel = MethodChannel(messenger, CHANNEL)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingResult: MethodChannel.Result? = null

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getCapability" -> sendCapability(result)
            "authorize" -> authorize(result)
            "cancelAuthorization" -> cancelAuthorization(result)
            else -> result.notImplemented()
        }
    }

    private fun sendCapability(result: MethodChannel.Result) {
        val capability = adapter.capability()
        result.success(
            mapOf(
                "available" to capability.available,
                "provider" to capability.provider,
                "message" to capability.message,
            ),
        )
    }

    private fun authorize(result: MethodChannel.Result) {
        val capability = adapter.capability()
        if (!capability.available) {
            result.error("UNAVAILABLE", capability.message, null)
            return
        }
        if (pendingResult != null) {
            result.error("IN_PROGRESS", "一键登录正在进行中", null)
            return
        }

        pendingResult = result
        adapter.authorize { authResult ->
            mainHandler.post {
                if (pendingResult !== result) return@post
                pendingResult = null
                when (authResult) {
                    is CarrierAuthResult.Success -> result.success(
                        mapOf(
                            "provider" to authResult.credential.provider,
                            "accessToken" to authResult.credential.accessToken,
                        ),
                    )

                    is CarrierAuthResult.Failure -> result.error(
                        authResult.code,
                        authResult.message,
                        null,
                    )
                }
            }
        }
    }

    private fun cancelAuthorization(result: MethodChannel.Result) {
        val authorizationResult = pendingResult
        pendingResult = null
        adapter.cancel()
        authorizationResult?.error("CANCELLED", "一键登录已取消", null)
        result.success(null)
    }

    fun dispose() {
        adapter.cancel()
        pendingResult = null
        channel.setMethodCallHandler(null)
    }
}
