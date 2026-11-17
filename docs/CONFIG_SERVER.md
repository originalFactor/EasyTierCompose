# Configuration server

EasyTierCompose supports the native EasyTier configuration server protocol.
The client uses `startConfigServerClient`, rather than downloading a TOML file.

## Connect

1. Create or edit a profile in **Profiles**.
2. Enable **Use configuration server**.
3. Enter the full `--config-server` URL, such as `udp://server.example:22020/token`.
4. Save the profile and select **Root TUN**, **VPN Service**, or **No TUN** in Settings.
5. Connect, then assign one network to the device in the EasyTier Web console.

A blank hostname uses the Android device name.
The client stores a random machine UUID outside Android backups.
The UUID stays the same across connections, profile changes, and app updates.
Clearing app data or reinstalling creates a new UUID.
The URL includes the server token, so the editor masks it.
The secure management switch maps to the native client's `secureMode` option.

## Network settings

The server owns the network configuration.
Local network fields and global network overrides do not change the remote TOML.
The client reads the server configuration through `ConfigRpc.GetConfig` to set the host interface's MTU, routes, and Magic DNS.
Unknown server fields stay in the native configuration.
Managed profiles cannot export a local TOML file.

Root TUN uses the interface `etmanaged0`.
For coexistence with another Android VPN, set these flags in the server's network configuration:

```toml
[flags]
bind_device = false
socket_mark = 131072
```

An IPv4 address is required for a host TUN interface.
The client waits for DHCP when the server does not set a static IPv4 address.
A remote `no_tun = true` flag skips the host interface.
Local **No TUN** also skips the host interface.

## Updates and connection state

The native client reconnects to the server after a connection failure.
A server connection failure keeps the current network active while the native client reconnects.
Before the first assignment, the dashboard shows a waiting state without a startup timeout.
Server updates, address changes, and instance restarts rebuild the host interface when needed.
The client releases the old Root TUN control descriptor before it creates a new interface.
Removing the assigned network clears its interface, routes, and DNS settings while the client waits for another assignment.
Disconnect stops the server client, removes managed instances, and clears host resources.
The app can adopt a managed Root TUN session after its process restarts.

This version accepts **one assigned network per Android client**.
Multiple assignments stop the session with an error.
Remove the extra assignments in the Web console, then reconnect.

## Validation

Run the unit tests and build all three APK variants:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Unit tests cover URL validation, profile storage, server settings, remote identity, MTU, DNS, routes, and lifecycle snapshots.
They also cover deletion, replacement, same-name restart, stale reads, and multiple assignments.
Native libraries for all three ABIs export the required configuration server symbols.

A physical Android device and a live EasyTier Web server are still required for integration tests.
Check first assignment, DHCP, server updates, network deletion, reconnection, disconnect cleanup, and Root TUN coexistence on that device.
