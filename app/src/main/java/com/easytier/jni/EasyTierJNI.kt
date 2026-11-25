package com.easytier.jni

@androidx.annotation.Keep
fun interface ConfigServerEventCallback {
    fun onEvent(eventJson: String)
}

/**
 * JNI symbol owner retained for the reference libeasytier_android_jni binary.
 * App code uses cc.ptoe.easytier.compose.core.EasyTierJni instead.
 */
object EasyTierJNI {
    init {
        System.loadLibrary("easytier_android_jni")
    }

    @JvmStatic external fun startConfigServerClient(
        url: String, hostname: String?, machineId: String, secureMode: Boolean,
        callback: ConfigServerEventCallback?,
    ): Int
    @JvmStatic external fun stopConfigServerClient(): Int
    @JvmStatic external fun isConfigServerClientConnected(): Boolean

    @JvmStatic external fun setTunFd(instanceName: String, fd: Int): Int
    @JvmStatic external fun parseConfig(config: String): Int
    @JvmStatic external fun runNetworkInstance(config: String): Int
    @JvmStatic external fun retainNetworkInstance(instanceNames: Array<String>?): Int
    @JvmStatic external fun collectNetworkInfos(maxLength: Int): String?
    @JvmStatic external fun callJsonRpc(
        serviceName: String,
        methodName: String,
        domainName: String?,
        payloadJson: String,
    ): String?
    @JvmStatic external fun getLastError(): String?
}
