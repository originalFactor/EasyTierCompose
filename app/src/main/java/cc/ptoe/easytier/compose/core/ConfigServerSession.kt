package cc.ptoe.easytier.compose.core

import cc.ptoe.easytier.compose.data.ConfigServerSettings
import cc.ptoe.easytier.compose.data.EasyTierProfile
import cc.ptoe.easytier.compose.data.TunMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.tomlj.Toml
import java.net.URI
import java.util.concurrent.atomic.AtomicLong

/** No native calls run on the native callback thread. */
internal class ConfigServerSession(private val native: ConfigServerNative = JniConfigServerNative) {
    private val revision = AtomicLong()

    fun start(settings: ConfigServerSettings, hostname: String?, machineId: String) {
        check(native.start(settings, hostname, machineId) { revision.incrementAndGet() } == 0) {
            "Could not start configuration server client"
        }
    }

    fun stop() { check(native.stop() == 0) { "Could not stop configuration server client" } }
    fun connected(): Boolean = native.connected()
    fun generation(): Long = revision.get()

    fun snapshot(owner: EasyTierProfile): ManagedNetwork? {
        // Two entries suffice to detect unsupported multiple-network assignments.
        val raw = native.collect() ?: error("Could not read managed network status")
        val map = Json.parseToJsonElement(raw).jsonObject["map"]?.jsonObject ?: return null
        if (map.size > 1) throw MultipleManagedNetworksException()
        val name = map.keys.singleOrNull() ?: return null
        val info = raw.networkInfo(name) ?: error("Invalid managed network status")
        info.error?.takeIf(String::isNotBlank)?.let { error(it) }
        if (info.running != true) return null
        val generation = revision.get()
        val config = try {
            native.config(name)
        } catch (error: Exception) {
            if (revision.get() != generation) return null
            // Removal can precede its callback. A second status read distinguishes
            // that race from a persistent RPC failure on the same running instance.
            if (native.collect()?.networkInfo(name)?.running != true) return null
            throw error
        } ?: return null
        // The instance can disappear while the server applies a new configuration.
        if (revision.get() != generation) return null
        return managedNetwork(owner, name, config, info, generation)
    }
}

internal class MultipleManagedNetworksException : IllegalStateException()

internal data class ManagedNetwork(
    val profile: EasyTierProfile,
    val info: NetworkInfo,
    val revision: Long,
    val noTun: Boolean,
    val config: String,
)

/** Reads only host interface settings. The native manager owns the original remote TOML. */
internal fun managedNetwork(owner: EasyTierProfile, name: String, config: String, info: NetworkInfo, revision: Long): ManagedNetwork {
    val toml = Toml.parse(config)
    require(!toml.hasErrors()) { "Invalid managed network configuration" }
    val remoteMtu = toml.getLong("flags.mtu") ?: 1380L
    require(remoteMtu in 576L..9000L) { "Managed MTU must be between 576 and 9000" }
    val mtu = remoteMtu.toInt()
    val routes = toml.getArray("routes")?.toList()?.map { it as String }.orEmpty()
    val profile = owner.copy(
        id = name,
        configServer = null,
        networkName = toml.getString("network_identity.network_name").orEmpty(),
        hostname = toml.getString("hostname"),
        virtualIpv4 = info.virtualIpv4,
        dhcp = true,
        mtu = mtu,
        manualRoutes = routes,
        proxyNetworks = emptyList(),
        enableMagicDns = toml.getBoolean("flags.accept_dns") ?: false,
        tunDeviceName = "etmanaged0",
    )
    return ManagedNetwork(profile, info, revision,
        owner.tunMode == TunMode.NO_TUN || toml.getBoolean("flags.no_tun") == true, config)
}

internal interface ConfigServerNative {
    fun start(settings: ConfigServerSettings, hostname: String?, machineId: String, changed: () -> Unit): Int
    fun stop(): Int
    fun connected(): Boolean
    fun collect(): String?
    fun config(name: String): String?
}

private object JniConfigServerNative : ConfigServerNative {
    override fun start(settings: ConfigServerSettings, hostname: String?, machineId: String, changed: () -> Unit): Int =
        EasyTierJni.startConfigServerClient(settings.url.trim(), hostname, machineId, settings.secureMode) { raw ->
            // Native code owns start/delete ordering. Every successful event invalidates the TUN fd.
            val success = runCatching {
                Json.parseToJsonElement(raw).jsonObject["success"]?.jsonPrimitive?.booleanOrNull
            }.getOrNull()
            if (success == true) changed()
        }
    override fun stop(): Int = EasyTierJni.stopConfigServerClient()
    override fun connected(): Boolean = EasyTierJni.isConfigServerClientConnected()
    override fun collect(): String? = EasyTierJni.collectNetworkInfos(2)
    override fun config(name: String): String? {
        val payload = """{"instance":{"instance_selector":{"name":${Json.encodeToString(name)}}}}"""
        val response = EasyTierJni.callJsonRpc("api.config.ConfigRpcService", "get_config", null, payload) ?: return null
        return Json.parseToJsonElement(response).jsonObject["toml_config"]?.jsonPrimitive?.content
    }
}

fun isValidConfigServerUrl(value: String): Boolean = runCatching {
    val uri = URI(value.trim())
    uri.scheme in setOf("tcp", "udp", "ws", "wss", "quic", "wg", "faketcp", "http", "https", "txt", "srv") &&
        !uri.host.isNullOrBlank() && uri.port in -1..65535 && uri.port != 0 &&
        !uri.rawPath.isNullOrBlank() && uri.rawPath.substringAfterLast('/').isNotBlank() &&
        uri.rawUserInfo == null && uri.fragment == null
}.getOrDefault(false)
