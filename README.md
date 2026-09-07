# VBBridge

**Velocity Broadcast Bridge**

A lightweight bridge plugin that lets backend (Paper/Spigot) servers trigger network-wide broadcasts on a Velocity proxy by relaying a simple command through the plugin messaging channel.

It is designed to work with [VelocityBroadcast](https://github.com/dpembo/velocitybroadcast-reborn) (or any proxy plugin that exposes a `/vb` command). When a player (or console) runs `/netbroadcast` on a backend server, VBBridge packages the message and sends it to the proxy, which then executes `/vb <message>`.

## Features

* Simple `/netbroadcast <message>` command on backend servers
* Relays the message to the Velocity proxy via the `globeworks:vb` plugin channel
* Works even when the command is run from console (uses any online player as a carrier for the plugin message)
* Permission-controlled
* Zero configuration required
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

No configuration files are generated or required.

## Commands

|Command|Description|Permission|Default|
|-|-|-|-|
|`/netbroadcast <message>`|Broadcasts the message network-wide via the proxy|`vbbridge.use`|`op`|

**Example:**

```
/netbroadcast Welcome to the network!
```

This will cause the proxy to run:

```
/vb Welcome to the network!
```

## Permissions

|Permission|Description|Default|
|-|-|-|
|`vbbridge.use`|Allows use of `/netbroadcast`|`op`|

## How it works

1. On the **backend** (Paper):

   * The plugin registers the outgoing channel `globeworks:vb`.
   * `/netbroadcast` packs the message into a plugin message and sends it through a connected player (or any online player if run from console).
2. On the **proxy** (Velocity):

   * The plugin listens for messages on `globeworks:vb`.
   * When a message arrives it executes `/vb <message>` as the console.

This allows backend plugins, crates, reward systems, etc. to trigger global broadcasts without needing direct proxy access.

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

