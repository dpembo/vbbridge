# VBBridge

**Velocity Broadcast Bridge**

A lightweight bridge plugin that lets backend (Paper/Spigot) servers trigger network-wide broadcasts on a Velocity proxy by relaying a simple command through the plugin messaging channel.

It is designed to work with [VelocityBroadcast](https://github.com/dpembo/velocitybroadcast-reborn) (or any proxy plugin that exposes a `/vb` command). When a player (or console) runs `/netbroadcast` on a backend server, VBBridge packages the message and sends it to the proxy, which then executes `/vb <message>`.

## Features

* Simple `/netbroadcast <message>` command on backend servers
* Relays the message to the Velocity proxy via the `globeworks:vb` plugin channel
* Works even when the command is run from console (uses any online player as a carrier for the plugin message)
* Permission-controlled
* Optional debug logging (off by default) to avoid log spam on empty servers
* Version handshake between Paper and Velocity — warns if the two sides run different jar versions
* Supports Paper 1.21+ and Velocity 3.4+

## Requirements

* **Proxy**: Velocity 3.4+
* **Backend**: Paper (or compatible) 1.21+
* A proxy-side broadcast plugin that provides the `/vb` command (e.g. VelocityBroadcast)
* Java 21

## Installation

1. Build the project (or download a release JAR if available):

```bash
   mvn clean package
   ```

   The resulting JAR will be in `target/vbbridge-1.0.0.jar`.

2. Place the **same JAR** in both:

   * The Velocity proxy `plugins/` folder
   * Every backend Paper server `plugins/` folder
3. Restart the proxy and all backend servers.
4. Ensure your proxy has a plugin that handles the `/vb` command (VelocityBroadcast or equivalent).
5. (Optional) Edit `plugins/VBBridge/config.yml` on each backend if you want debug logging on by default.

A `config.yml` is generated on first run with `debug: false`.

## Commands

|Command|Description|Permission|Default|
|-|-|-|-|
|`/netbroadcast <message>`|Broadcasts the message network-wide via the proxy|`vbbridge.use`|`op`|
|`/vbbridge debug [on\|off\|toggle\|status]`|Toggle debug logging for relay failures/successes|`vbbridge.debug`|`op`|
|`/vbbridge reload`|Reload config from disk|`vbbridge.reload`|`op`|
|`/vbbridge handshake`|Manually re-run version HELLO with the proxy (aliases: `version`, `hello`, `ping`)|`vbbridge.handshake` or `vbbridge.debug`|`op`|

**Example:**

```
/netbroadcast Welcome to the network!
```

This will cause the proxy to run:

```
/vb Welcome to the network!
```

When **debug** is off (default), a broadcast attempted with no players online is silently skipped — no warning in the log and no message to the sender. This avoids flooding logs during quiet periods (e.g. scheduled crate rewards on an empty server). With debug on, the previous warning and feedback are shown, plus a log line on successful relays.

## Permissions

|Permission|Description|Default|
|-|-|-|
|`vbbridge.use`|Allows use of `/netbroadcast`|`op`|
|`vbbridge.debug`|Allows toggling debug logging|`op`|
|`vbbridge.reload`|Allows reloading config|`op`|
|`vbbridge.handshake`|Allows `/vbbridge handshake` to re-check Paper ↔ Velocity versions|`op`|

## How it works

1. On the **backend** (Paper):

   * The plugin registers the outgoing/incoming channel `globeworks:vb`.
   * `/netbroadcast` packs the message into a plugin message and sends it through a connected player (or any online player if run from console).
2. On the **proxy** (Velocity):

   * The plugin listens for messages on `globeworks:vb`.
   * When a broadcast message arrives it executes `/vb <message>` as the console.

This allows backend plugins, crates, reward systems, etc. to trigger global broadcasts without needing direct proxy access.

### Version handshake

Plugin messaging needs a player connection, so a pure “on enable with zero players” check is not possible. Instead:

* When a player connects to a backend, **Velocity** sends a `HELLO` packet with its version to that server.
* When a player joins (or the first broadcast is sent), **Paper** sends a `HELLO` with its version to the proxy.

If the versions differ, both sides log a **warning once** (e.g. `VBBridge version mismatch! …`). Keep the **same jar** on proxy and all backends.

After updating only one side, run **`/vbbridge handshake`** on the Paper server (needs at least one player online). That forces a new HELLO, the proxy replies with its version, and you get an in-game match/mismatch message (5s timeout if the proxy never answers).

Broadcast packets use a `BROADCAST` type prefix; older single-string messages are still accepted on the proxy for compatibility.

## Building from Source

```bash
git clone https://github.com/dpembo/vbbridge.git
cd vbbridge
mvn clean package
```

Requires Maven and JDK 21.

## License

This project is licensed under the **GNU General Public License v3.0**.  
See the [LICENSE](LICENSE) file for details.

## Author

* **dpembo**

