package com.rabbit.app.flutter

data class CarrierAuthCapability(
    val available: Boolean,
    val provider: String = "",
    val message: String = "",
)

data class CarrierAuthCredential(
    val provider: String,
    val accessToken: String,
)

sealed interface CarrierAuthResult {
    data class Success(val credential: CarrierAuthCredential) : CarrierAuthResult

    data class Failure(
        val code: String,
        val message: String,
    ) : CarrierAuthResult
}

interface CarrierAuthAdapter {
    fun capability(): CarrierAuthCapability

    // The real SDK adapter owns its post-user-action/network timeout and reports TIMEOUT.
    fun authorize(callback: (CarrierAuthResult) -> Unit)

    fun cancel()
}

class UnavailableCarrierAuthAdapter : CarrierAuthAdapter {
    override fun capability() = CarrierAuthCapability(
        available = false,
        message = "当前安装包未集成运营商认证 SDK",
    )

    override fun authorize(callback: (CarrierAuthResult) -> Unit) {
        callback(
            CarrierAuthResult.Failure(
                code = "UNAVAILABLE",
                message = "当前安装包未集成运营商认证 SDK",
            ),
        )
    }

    override fun cancel() = Unit
}
