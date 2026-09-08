package uk.globeworks.vbbridge.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(id = "vbbridge", name = "VBBridge", version = "1.0.1", authors = {"dpembo"})
public class VBBridgeVelocityPlugin {

    private static final ChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("globeworks:vb");

    public static final String TYPE_HELLO = "HELLO";
    public static final String TYPE_BROADCAST = "BROADCAST";

    public static final String RESET = "\u001B[0m";
    public static final String YELLOW = "\u001B[33m";
    public static final String GREEN = "\u001B[32m";
    public static final String BLUE = "\u001B[34m";
    public static final String RED = "\u001B[31m";
    public static final String BROWN = "\u001B[33m";

    private final ProxyServer server;
    private final Logger logger;
    private final PluginContainer container;

    /** Backend server name → whether we already warned about a version mismatch. */
    private final Map<String, Boolean> mismatchWarned = new ConcurrentHashMap<>();
    /** Backend server name → whether we already sent HELLO for this connection era. */
    private final Map<String, Boolean> helloSent = new ConcurrentHashMap<>();

    @Inject
    public VBBridgeVelocityPlugin(ProxyServer server, Logger logger, PluginContainer container) {
        this.server = server;
        this.logger = logger;
        this.container = container;
    }

    private String ownVersion() {
        return container.getDescription().getVersion().orElse("unknown");
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(CHANNEL);
        String logo = "\n" +
        GREEN + "_______ _____   " + BLUE + "_______ " + GREEN + "______ " + BLUE + "_______\n" +
        GREEN + "|     __|     |_|" + BLUE + "       |" + GREEN + "   __ \\" + BLUE + "    ___|\n" +
        GREEN + "|    |  |       |" + BLUE + "   -   |" + GREEN + "   __ <" + BLUE + "    ___|\n" +
        BROWN + "|_______|_______|" + BLUE + "_______|" + BROWN + "______/" + BLUE + "_______\n" +
        RED + "________ _______ ______ __  __ _______\n" +
        RED + "|  |  |  |       |   __ \\  |/  |     __|\n" +
        RED + "|  |  |  |   -   |      <     <|__     |\n" +
        RED + "|________|_______|___|__|__|\\__|_______|\n" +
        YELLOW + "            <VBBridge>\n" +
        RESET;
        logger.info(logo);
        logger.info("VBBridge {} registered on channel {}", ownVersion(), CHANNEL.getId());
        logger.info("Version handshake with backends will run when a player connects to each server.");
    }

    /**
     * As soon as a player lands on a backend, push our version so the Paper side
     * can warn if the jars differ. Also the earliest moment we can talk to that server.
     */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        Optional<ServerConnection> conn = player.getCurrentServer();
        if (conn.isEmpty()) {
            return;
        }
        ServerConnection serverConnection = conn.get();
        String serverName = serverConnection.getServerInfo().getName();

        if (Boolean.TRUE.equals(helloSent.putIfAbsent(serverName, Boolean.TRUE))) {
            return;
        }

        if (!sendHello(serverConnection, serverName)) {
            helloSent.remove(serverName);
        }
    }

    private boolean sendHello(ServerConnection serverConnection, String serverName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(TYPE_HELLO);
        out.writeUTF(ownVersion());
        boolean sent = serverConnection.sendPluginMessage(CHANNEL, out.toByteArray());
        if (!sent) {
            logger.warn("Could not send VBBridge HELLO to backend '{}'.", serverName);
        } else {
            logger.info("Sent HELLO (version {}) to backend '{}'.", ownVersion(), serverName);
        }
        return sent;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String first;
        try {
            first = in.readUTF();
        } catch (Exception e) {
            logger.warn("Malformed VBBridge plugin message (empty?).");
            return;
        }

        if (TYPE_HELLO.equals(first)) {
            handleHello(event, in);
            return;
        }

        String message;
        if (TYPE_BROADCAST.equals(first)) {
            try {
                message = in.readUTF();
            } catch (Exception e) {
                logger.warn("Malformed VBBridge BROADCAST packet.");
                return;
            }
        } else {
            // Legacy: single UTF was the broadcast text (pre-1.0.1).
            message = first;
        }

        server.getCommandManager().executeAsync(server.getConsoleCommandSource(), "vb " + message);
    }

    private void handleHello(PluginMessageEvent event, ByteArrayDataInput in) {
        String backendVersion;
        try {
            backendVersion = in.readUTF();
        } catch (Exception e) {
            logger.warn("Malformed VBBridge HELLO packet.");
            return;
        }

        String serverName = resolveBackendName(event);
        String local = ownVersion();

        if (local.equals(backendVersion)) {
            logger.info("Backend '{}' VBBridge version matches: {}", serverName, backendVersion);
        } else {
            String key = serverName != null ? serverName : backendVersion;
            if (mismatchWarned.putIfAbsent(key, Boolean.TRUE) == null) {
                logger.warn("VBBridge version mismatch! Velocity proxy is {} but backend '{}' reported {}. "
                        + "Update both sides to the same jar to avoid protocol issues.",
                    local, serverName != null ? serverName : "unknown", backendVersion);
            } else {
                // Manual re-check from backend: always log again when they send HELLO.
                logger.warn("VBBridge version mismatch (re-check)! Velocity proxy is {} but backend '{}' reported {}.",
                    local, serverName != null ? serverName : "unknown", backendVersion);
            }
        }

        // Reply so Paper's /vbbridge handshake can confirm the proxy version.
        ServerConnection replyTarget = resolveServerConnection(event);
        if (replyTarget != null) {
            String name = replyTarget.getServerInfo().getName();
            sendHello(replyTarget, name);
        }
    }

    private String resolveBackendName(PluginMessageEvent event) {
        ServerConnection sc = resolveServerConnection(event);
        return sc != null ? sc.getServerInfo().getName() : null;
    }

    private ServerConnection resolveServerConnection(PluginMessageEvent event) {
        if (event.getSource() instanceof ServerConnection sc) {
            return sc;
        }
        if (event.getSource() instanceof Player player) {
            return player.getCurrentServer().orElse(null);
        }
        return null;
    }
}
