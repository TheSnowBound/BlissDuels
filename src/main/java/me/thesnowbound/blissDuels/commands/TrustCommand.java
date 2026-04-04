package me.thesnowbound.blissDuels.commands;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.managers.TrustManager;
import me.thesnowbound.blissDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * /trust list
 * /trust add <player>
 * /trust remove <player>
 */
public class TrustCommand implements CommandExecutor, TabCompleter {
    private final BlissDuels plugin;

    public TrustCommand(BlissDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /trust.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("Usage: /trust <list|add|remove> [player]");
            return true;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        TrustManager trustManager = plugin.getTrustManager();

        if ("list".equals(mode)) {
            player.openInventory(trustManager.buildUntrustGui(player));
            return true;
        }

        if (!"add".equals(mode) && !"remove".equals(mode)) {
            player.sendMessage("Usage: /trust <list|add|remove> [player]");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("Usage: /trust " + mode + " <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID targetId = target.getUniqueId();
        if (targetId == null) {
            player.sendMessage("Player not found.");
            return true;
        }

        if (player.getUniqueId().equals(targetId)) {
            player.sendMessage(ColorUtil.color("<##FFD773>🔮 <##FC8888>You cannot " + ("add".equals(mode) ? "trust" : "untrust") + " yourself!"));
            return true;
        }

        String targetName = target.getName() == null ? targetId.toString() : target.getName();

        if ("add".equals(mode)) {
            boolean added = trustManager.addTrusted(player.getUniqueId(), targetId);
            if (!added) {
                player.sendMessage(ColorUtil.color("<##FFD773>🔮 <##B8FFFB>" + targetName + " &cis already trusted!"));
                return true;
            }
            player.sendMessage(ColorUtil.color("<##FFD773>🔮 &aTrusted <##B8FFFB>" + targetName));
            return true;
        }

        boolean removed = trustManager.removeTrusted(player.getUniqueId(), targetId);
        if (!removed) {
            player.sendMessage(ColorUtil.color("<##FFD773>🔮 <##FC8888>" + targetName + " is not trusted."));
            return true;
        }
        player.sendMessage(ColorUtil.color("<##FFD773>🔮 &cUntrusted <##B8FFFB>" + targetName));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("list");
            out.add("add");
            out.add("remove");
            return out;
        }

        if (args.length == 2 && ("add".equalsIgnoreCase(args[0]) || "remove".equalsIgnoreCase(args[0]))) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                out.add(online.getName());
            }
            if (sender instanceof Player player && "remove".equalsIgnoreCase(args[0])) {
                Set<UUID> trusted = plugin.getTrustManager().getTrusted(player.getUniqueId());
                for (UUID uuid : trusted) {
                    OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
                    if (off.getName() != null) {
                        out.add(off.getName());
                    }
                }
            }
        }

        return out;
    }
}

