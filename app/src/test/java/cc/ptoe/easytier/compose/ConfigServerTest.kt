package cc.ptoe.easytier.compose

import cc.ptoe.easytier.compose.core.*
import cc.ptoe.easytier.compose.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ConfigServerTest {
    private val owner = EasyTierProfile(
        id = "local-profile", name = "Managed device", networkName = "", networkSecret = "",
        virtualIpv4 = null, dhcp = false, enableMagicDns = false, tunMode = TunMode.ROOT_TUN,
        configServer = ConfigServerSettings("udp://server.example:22020/token"),
    )
    private val remoteToml = """
        instance_name = "remote-network"
        routes = ["192.0.2.0/24"]
        [network_identity]
        network_name = "central-network"
        [flags]
        mtu = 1400
        accept_dns = true
    """.trimIndent()

    private class FakeNative : ConfigServerNative {
        var raw = """{"map":{}}"""
        var toml: String? = null
        var changed: () -> Unit = {}
        var duringConfig: () -> Unit = {}
        var stopped = false
        var identity: String? = null
        override fun start(settings: ConfigServerSettings, hostname: String?, machineId: String, changed: () -> Unit): Int {
            this.changed = changed
            identity = machineId
            return 0
        }
        override fun stop(): Int { stopped = true; return 0 }
        override fun connected() = true
        override fun collect() = raw
        override fun config(name: String): String? { duringConfig(); return toml }
    }

    private fun networkRaw(name: String = "remote-network") = """
        {"map":{"$name":{"running":true,"my_node_info":{
            "hostname":"phone","virtual_ipv4":{"address":{"addr":168430082},"network_length":24}
        }}}}
    """.trimIndent()

    @Test fun `managed validation skips local network fields and native TOML parser`() {
        val validator = ProfileValidator(NativeConfigParser { error("Local parser must not run") })
        assertTrue(validator.validate(owner).isEmpty())
        assertTrue(validator.validate(owner.copy(name = "")).containsKey("name"))
        assertTrue(validator.validate(owner.copy(configServer = ConfigServerSettings(""))).containsKey("configServer"))
    }

    @Test fun `server URL requires supported scheme host and final token`() {
        listOf("udp://server.example:22020/token", "tcp://server.example/token", "wss://server.example/path/token", "udp://[::1]:22020/a%20b")
            .forEach { assertTrue(it, isValidConfigServerUrl(it)) }
        listOf("", "server.example/token", "ftp://server.example/token", "udp://server.example:22020/", "udp://:22020/token", "udp://server.example:70000/token", "udp://server.example:0/token", "udp://user:pass@server.example/token", "udp://server.example/token#fragment")
            .forEach { assertFalse(it, isValidConfigServerUrl(it)) }
    }

    @Test fun `profile storage keeps server options and old profiles default to local`() {
        assertEquals(owner, Json.decodeFromString<EasyTierProfile>(Json.encodeToString(owner)))
        val legacy = Json.encodeToString(owner.copy(configServer = null))
        assertNull(Json.decodeFromString<EasyTierProfile>(legacy).configServer)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `managed profile cannot export unrelated local TOML`() { TomlConfigBuilder.build(owner) }

    @Test fun `connected server without assignment waits without error`() {
        val fake = FakeNative()
        val session = ConfigServerSession(fake)
        session.start(owner.configServer!!, "phone", "stable-id")
        assertEquals("stable-id", fake.identity)
        assertTrue(session.connected())
        assertNull(session.snapshot(owner))
        session.stop()
        assertTrue(fake.stopped)
    }

    @Test fun `remote network uses server identity MTU DNS routes and assigned IP`() {
        val fake = FakeNative().apply { raw = networkRaw(); toml = remoteToml }
        val network = ConfigServerSession(fake).snapshot(owner)!!
        assertEquals("remote-network", network.profile.id)
        assertEquals("central-network", network.profile.networkName)
        assertEquals("10.10.10.2/24", network.info.virtualIpv4)
        assertEquals(1400, network.profile.mtu)
        assertTrue(network.profile.enableMagicDns)
        assertEquals(listOf("192.0.2.0/24"), network.profile.manualRoutes)
        assertNull(network.profile.configServer)
        assertFalse(network.noTun)
    }

    @Test fun `same-name remote restart changes revision to force fd attachment`() {
        val fake = FakeNative().apply { raw = networkRaw(); toml = remoteToml }
        val session = ConfigServerSession(fake)
        session.start(owner.configServer!!, null, "stable-id")
        val before = session.snapshot(owner)!!
        fake.changed()
        assertTrue(session.snapshot(owner)!!.revision > before.revision)
    }

    @Test fun `remote deletion clears assignment and allows replacement`() {
        val fake = FakeNative().apply { raw = networkRaw(); toml = remoteToml }
        val session = ConfigServerSession(fake)
        assertNotNull(session.snapshot(owner))
        fake.raw = """{"map":{}}"""
        assertNull(session.snapshot(owner))
        fake.raw = networkRaw("replacement")
        assertEquals("replacement", session.snapshot(owner)!!.profile.id)
    }

    @Test fun `configuration change during read discards stale snapshot`() {
        val fake = FakeNative().apply { raw = networkRaw(); toml = remoteToml }
        val session = ConfigServerSession(fake)
        session.start(owner.configServer!!, null, "stable-id")
        fake.duringConfig = { fake.changed() }
        assertNull(session.snapshot(owner))
    }

    @Test fun `stopped remote instance clears its host interface snapshot`() {
        val fake = FakeNative().apply { raw = networkRaw().replace("true", "false"); toml = remoteToml }
        assertNull(ConfigServerSession(fake).snapshot(owner))
    }

    @Test fun `instance removal before callback does not fail the session`() {
        val fake = FakeNative().apply { raw = networkRaw(); toml = remoteToml }
        val session = ConfigServerSession(fake)
        fake.duringConfig = {
            fake.raw = """{"map":{}}"""
            error("Instance no longer exists")
        }
        assertNull(session.snapshot(owner))
    }

    @Test(expected = IllegalStateException::class)
    fun `persistent config RPC failure stays visible`() {
        val fake = FakeNative().apply { raw = networkRaw(); toml = remoteToml }
        fake.duringConfig = { error("RPC unavailable") }
        ConfigServerSession(fake).snapshot(owner)
    }

    @Test(expected = MultipleManagedNetworksException::class)
    fun `multiple remote networks fail explicitly`() {
        val fake = FakeNative().apply { raw = """{"map":{"one":{},"two":{}}}""" }
        ConfigServerSession(fake).snapshot(owner)
    }

    @Test fun `remote no-tun flag and local no-tun mode both skip host interface`() {
        val fake = FakeNative().apply { raw = networkRaw(); toml = "$remoteToml\nno_tun = true" }
        assertTrue(ConfigServerSession(fake).snapshot(owner)!!.noTun)
        fake.toml = remoteToml
        assertTrue(ConfigServerSession(fake).snapshot(owner.copy(tunMode = TunMode.NO_TUN))!!.noTun)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid remote MTU cannot reach host interface creation`() {
        val fake = FakeNative().apply { raw = networkRaw(); toml = remoteToml.replace("1400", "42") }
        ConfigServerSession(fake).snapshot(owner)
    }
}
