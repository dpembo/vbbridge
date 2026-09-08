package uk.globeworks.vbbridge;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class VBBridgePlugin extends JavaPlugin implements CommandExecutor, TabCompleter, PluginMessageListener, Listener {

    public static final String CHANNEL = "globeworks:vb";

    /** Packet types on the channel (first UTF string). */
    public static final String TYPE_HELLO = "HELLO";
    public static final String TYPE_BROADCAST = "BROADCAST";

    public static final String RESET = "\u001B[0m";
    public static final String YELLOW = "\u001B[33m";
    public static final String GREEN = "\u001B[32m";
    public static final String BLUE = "\u001B[34m";
    public static final String RED = "\u001B[31m";
    public static final String BROWN = "\u001B[33m";

    private boolean debug;
    private boolean versionMismatchWarned;
    private boolean helloSent;
    /** Sender waiting for a proxy HELLO reply from /vbbridge handshake. */
    private CommandSender pendingHandshakeSender;
    private int handshakeTimeoutTaskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.debug = getConfig().getBoolean("debug", false);

        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        Bukkit.getPluginManager().registerEvents(this, this);

        var netbroadcast = getCommand("netbroadcast");
        if (netbroadcast != null) {
            netbroadcast.setExecutor(this);
        }
        var vbbridge = getCommand("vbbridge");
        if (vbbridge != null) {
            vbbridge.setExecutor(this);
            vbbridge.setTabCompleter(this);
        }

        String logo = "\n" +
        GREEN + " _______ _____   " + BLUE + "_______ " + GREEN + "______ " + BLUE + "_______\n" +
        GREEN + "|     __|     |_|" + BLUE + "       |" + GREEN + "   __ \\" + BLUE + "    ___|\n" +
        GREEN + "|    |  |       |" + BLUE + "   -   |" + GREEN + "   __ <" + BLUE + "    ___|\n" +
        BROWN + "|_______|_______|" + BLUE + "_______|" + BROWN + "______/" + BLUE + "_______\n" +
        RED + " ________ _______ ______ __  __ _______\n" +
        RED + "|  |  |  |       |   __ \\  |/  |     __|\n" +
        RED + "|  |  |  |   -   |      <     <|__     |\n" +
        RED + "|________|_______|___|__|__|\\__|_______|\n" +
        YELLOW + "            <VBBridge>\n" +
        RESET;
        getLogger().info(logo);

        getLogger().info("VBBridge " + getDescription().getVersion()
            + " enabled - relaying /netbroadcast to proxy /vb.");
        getLogger().info("Debug logging: " + (debug ? "on" : "off"));
        getLogger().info("Version handshake with proxy will run when a player is online.");
    }

    @Override
    public void onDisable() {
        cancelHandshakeTimeout();
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL, this);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Plugin messaging needs a carrier; first join is the earliest practical handshake.
        if (!helloSent) {
            Bukkit.getScheduler().runTaskLater(this, () -> trySendHello(event.getPlayer(), false), 20L);
        }
    }

    /**
     * @param force if true, always send (manual /vbbridge handshake)
     * @return true if a HELLO packet was sent
     */
    private boolean trySendHello(Player player, boolean force) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!force && helloSent) {
            return false;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(TYPE_HELLO);
        out.writeUTF(getDescription().getVersion());
        player.sendPluginMessage(this, CHANNEL, out.toByteArray());
        helloSent = true;
        if (debug || force) {
            getLogger().info("Sent HELLO (version " + getDescription().getVersion()
                + ") to proxy via " + player.getName());
        }
        return true;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String type = in.readUTF();
            if (TYPE_HELLO.equals(type)) {
                String proxyVersion = in.readUTF();
                checkProxyVersion(proxyVersion);
            } else if (debug) {
                getLogger().info("Ignored inbound plugin message type: " + type);
            }
        } catch (Exception e) {
            if (debug) {
                getLogger().warning("Failed to parse inbound plugin message: " + e.getMessage());
            }
        }
    }

    private void checkProxyVersion(String proxyVersion) {
        String local = getDescription().getVersion();
        boolean match = local.equals(proxyVersion);

        CommandSender waiter = pendingHandshakeSender;
        pendingHandshakeSender = null;
        cancelHandshakeTimeout();

        if (match) {
            String ok = "Proxy VBBridge version matches: " + proxyVersion
                + " (this server: " + local + ")";
            getLogger().info(ok);
            if (waiter != null) {
                waiter.sendMessage("§a" + ok);
            }
            versionMismatchWarned = false;
            return;
        }

        String warn = "VBBridge version mismatch! Paper backend is " + local
            + " but Velocity proxy reported " + proxyVersion
            + ". Update both sides to the same jar to avoid protocol issues.";
        if (!versionMismatchWarned || waiter != null) {
            getLogger().warning(warn);
            versionMismatchWarned = true;
        }
        if (waiter != null) {
            waiter.sendMessage("§c" + warn);
        }
    }

    private void cancelHandshakeTimeout() {
        if (handshakeTimeoutTaskId != -1) {
            Bukkit.getScheduler().cancelTask(handshakeTimeoutTaskId);
            handshakeTimeoutTaskId = -1;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("vbbridge")) {
            return handleAdmin(sender, args);
        }
        return handleNetbroadcast(sender, args);
    }

    private boolean handleNetbroadcast(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /netbroadcast <message>");
            return true;
        }

        String message = String.join(" ", args);

        Player carrier = (sender instanceof Player p) ? p : Bukkit.getOnlinePlayers().stream().findAny().orElse(null);

        if (carrier == null) {
            if (debug) {
                getLogger().warning("Could not relay broadcast - no player online to carry the plugin message.");
                sender.sendMessage("§cCouldn't relay the broadcast: no player is online.");
            }
            return true;
        }

        if (!helloSent) {
            trySendHello(carrier, false);
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(TYPE_BROADCAST);
        out.writeUTF(message);
        carrier.sendPluginMessage(this, CHANNEL, out.toByteArray());

        if (debug) {
            getLogger().info("Relayed broadcast via " + carrier.getName() + ": " + message);
        }

        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§eUsage: /vbbridge <debug|reload|handshake|version> [args]");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("reload")) {
            if (!sender.hasPermission("vbbridge.reload")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            reloadConfig();
            this.debug = getConfig().getBoolean("debug", false);
            sender.sendMessage("§aVBBridge config reloaded. Debug: §f" + (debug ? "on" : "off"));
            return true;
        }

        if (sub.equals("handshake") || sub.equals("version") || sub.equals("hello") || sub.equals("ping")) {
            if (!sender.hasPermission("vbbridge.handshake") && !sender.hasPermission("vbbridge.debug")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            return runHandshake(sender);
        }

        if (sub.equals("debug")) {
            if (!sender.hasPermission("vbbridge.debug")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            if (args.length == 1) {
                setDebug(!debug);
                sender.sendMessage(debug
                    ? "§aDebug logging §fenabled§a."
                    : "§eDebug logging §fdisabled§e.");
                return true;
            }
            String arg = args[1].toLowerCase(Locale.ROOT);
            if (arg.equals("on") || arg.equals("true") || arg.equals("enable") || arg.equals("1")) {
                setDebug(true);
                sender.sendMessage("§aDebug logging §fenabled§a.");
            } else if (arg.equals("off") || arg.equals("false") || arg.equals("disable") || arg.equals("0")) {
                setDebug(false);
                sender.sendMessage("§eDebug logging §fdisabled§e.");
            } else if (arg.equals("toggle")) {
                setDebug(!debug);
                sender.sendMessage(debug
                    ? "§aDebug logging §fenabled§a."
                    : "§eDebug logging §fdisabled§e.");
            } else if (arg.equals("status") || arg.equals("?")) {
                sender.sendMessage("§7Debug logging is currently "
                    + (debug ? "§aon" : "§coff") + "§7.");
            } else {
                sender.sendMessage("§eUsage: /vbbridge debug [on|off|toggle|status]");
            }
            return true;
        }

        sender.sendMessage("§eUsage: /vbbridge <debug|reload|handshake|version> [args]");
        return true;
    }

    private boolean runHandshake(CommandSender sender) {
        String local = getDescription().getVersion();
        sender.sendMessage("§7This server (Paper) VBBridge version: §f" + local);

        Player carrier = (sender instanceof Player p) ? p : Bukkit.getOnlinePlayers().stream().findAny().orElse(null);
        if (carrier == null) {
            sender.sendMessage("§cCannot handshake: no player online to carry the plugin message to the proxy.");
            return true;
        }

        // Allow a fresh mismatch warning / reply for this manual check.
        versionMismatchWarned = false;
        pendingHandshakeSender = sender;
        cancelHandshakeTimeout();
        handshakeTimeoutTaskId = Bukkit.getScheduler().runTaskLater(this, () -> {
            handshakeTimeoutTaskId = -1;
            if (pendingHandshakeSender == sender) {
                pendingHandshakeSender = null;
                sender.sendMessage("§cNo HELLO reply from the proxy within 5s. "
                    + "Is VBBridge installed and the same version on Velocity?");
                getLogger().warning("Handshake timed out waiting for proxy HELLO reply.");
            }
        }, 100L).getTaskId();

        boolean sent = trySendHello(carrier, true);
        if (sent) {
            sender.sendMessage("§eSent HELLO to proxy via §f" + carrier.getName()
                + "§e — waiting for reply…");
        } else {
            cancelHandshakeTimeout();
            pendingHandshakeSender = null;
            sender.sendMessage("§cFailed to send HELLO (carrier went offline?).");
        }
        return true;
    }

    private void setDebug(boolean enabled) {
        this.debug = enabled;
        getConfig().set("debug", enabled);
        saveConfig();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("vbbridge")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission("vbbridge.debug")) {
                subs.add("debug");
                subs.add("handshake");
                subs.add("version");
            } else if (sender.hasPermission("vbbridge.handshake")) {
                subs.add("handshake");
                subs.add("version");
            }
            if (sender.hasPermission("vbbridge.reload")) subs.add("reload");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return subs.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")
            && sender.hasPermission("vbbridge.debug")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("on", "off", "toggle", "status").stream()
                .filter(s -> s.startsWith(prefix))
                .collect(Collectors.toList());
        }
        return List.of();
    }
}
