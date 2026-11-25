package cc.ptoe.easytier.compose.core

import com.easytier.jni.EasyTierJNI

/** App-owned facade over the bundled reference EasyTier JNI library. */
object EasyTierJni {
    fun startConfigServerClient(url: String, hostname: String?, machineId: String, secureMode: Boolean, onEvent: (String) -> Unit): Int =
        EasyTierJNI.startConfigServerClient(url, hostname, machineId, secureMode, com.easytier.jni.ConfigServerEventCallback(onEvent))
    fun stopConfigServerClient(): Int = EasyTierJNI.stopConfigServerClient()
    fun isConfigServerClientConnected(): Boolean = EasyTierJNI.isConfigServerClientConnected()

    fun setTunFd(instanceName: String, fd: Int): Int = EasyTierJNI.setTunFd(instanceName, fd)
    fun parseConfig(config: String): Int = EasyTierJNI.parseConfig(config)
    fun runNetworkInstance(config: String): Int = EasyTierJNI.runNetworkInstance(config)
    fun retainNetworkInstance(instanceNames: Array<String>?): Int = EasyTierJNI.retainNetworkInstance(instanceNames)
    fun collectNetworkInfos(maxLength: Int): String? = EasyTierJNI.collectNetworkInfos(maxLength)

    /**
     * Calls an exposed EasyTier RPC method with protobuf JSON payloads. Used to
     * query instance-targeted read-only RPCs (e.g. VpnPortalRpc.GetVpnPortalInfo)
     * that collectNetworkInfos does not surface.
     */
    fun callJsonRpc(
        serviceName: String,
        methodName: String,
        domainName: String?,
        payloadJson: String,
    ): String? = EasyTierJNI.callJsonRpc(serviceName, methodName, domainName, payloadJson)

    fun getLastError(): String? = EasyTierJNI.getLastError()
}
