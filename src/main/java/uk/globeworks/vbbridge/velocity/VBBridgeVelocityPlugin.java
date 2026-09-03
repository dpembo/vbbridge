package uk.globeworks.vbbridge.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

@Plugin(id = "vbbridge", name = "VBBridge", version = "1.0.0", authors = {"dpembo"})
public class VBBridgeVelocityPlugin {

    private static final ChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("globeworks:vb");
    public static final String RESET = "\u001B[0m";
    public static final String YELLOW = "\u001B[33m";
    public static final String GREEN = "\u001B[32m";
    public static final String BLUE = "\u001B[34m";
    public static final String RED = "\u001B[31m";
    public static final String BROWN = "\u001B[33m"; // brown doesn't exist, use yellow or custom RGB

    private final ProxyServer server;
    private final Logger logger;

    @Inject
    public VBBridgeVelocityPlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
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
        logger.info("VBBridge registered on channel {}", CHANNEL.getId());
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String message = in.readUTF();

        server.getCommandManager().executeAsync(server.getConsoleCommandSource(), "vb " + message);
    }
}