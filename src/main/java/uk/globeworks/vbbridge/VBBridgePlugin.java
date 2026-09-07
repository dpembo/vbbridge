package uk.globeworks.vbbridge;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class VBBridgePlugin extends JavaPlugin implements CommandExecutor {

    public static final String CHANNEL = "globeworks:vb";

    public static final String RESET = "\u001B[0m";
    public static final String YELLOW = "\u001B[33m";
    public static final String GREEN = "\u001B[32m";
    public static final String BLUE = "\u001B[34m";
    public static final String RED = "\u001B[31m";
    public static final String BROWN = "\u001B[33m"; // brown doesn't exist, use yellow or custom RGB

    @Override
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        var self = getCommand("netbroadcast");
        if (self != null) {
            self.setExecutor(this);
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

        getLogger().info("VBBridge enabled - relaying /netbroadcast to proxy /vb.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsage: /netbroadcast <message>");
            return true;
        }

        String message = String.join(" ", args);

        // Bukkit's plugin messaging channel needs a live player connection to
        // carry the packet to the proxy - it doesn't matter WHICH player,
        // since the message content is independent of who sends it. If the
        // command was run by a player, use them; otherwise (e.g. a crate
        // reward command executed "as console") grab any online player.
        Player carrier = (sender instanceof Player p) ? p : Bukkit.getOnlinePlayers().stream().findAny().orElse(null);

        if (carrier == null) {
            getLogger().warning("Could not relay broadcast - no player online to carry the plugin message.");
            sender.sendMessage("§cCouldn't relay the broadcast: no player is online.");
            return true;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(message);
        carrier.sendPluginMessage(this, CHANNEL, out.toByteArray());

        return true;
    }
}