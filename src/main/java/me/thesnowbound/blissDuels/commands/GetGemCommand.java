package me.thesnowbound.blissDuels.commands;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.gem.GemType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /getgem <gem|random|trader|upgrader|selector|selector1|selector2> [tier] [player]
 */
public class GetGemCommand implements CommandExecutor, TabCompleter {
    private final BlissDuels plugin;

    public GetGemCommand(BlissDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("Usage: /getgem <gem|random|trader|upgrader|selector|selector1|selector2> [tier] [player]");
            return true;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("Console must provide a player: /getgem <gem> <tier> <player>");
            return true;
        }

        if (target == null) {
            sender.sendMessage("Player not found.");
            return true;
        }

        String token = args[0].toLowerCase(Locale.ROOT);
        if (token.equals("trader")) {
            target.getInventory().addItem(plugin.getGemItemManager().createTraderItem());
            sender.sendMessage("Gave trader to " + target.getName());
            return true;
        }
        if (token.equals("gold")) {
            if (!sender.isOp()) {
                sender.sendMessage("Only operators can give the gold gem.");
                return true;
            }
            target.getInventory().addItem(plugin.getGemItemManager().createGoldGemItem());
            sender.sendMessage("Gave Gold Gem to " + target.getName());
            return true;
        }
        if (token.equals("upgrader")) {
            target.getInventory().addItem(plugin.getGemItemManager().createUpgraderItem());
            sender.sendMessage("Gave upgrader to " + target.getName());
            return true;
        }
        if (token.equals("selector") || token.equals("selector1") || token.equals("selector2")) {
            if (!sender.isOp()) {
                sender.sendMessage("Only operators can give gem selectors.");
                return true;
            }
            int selectorTier = token.endsWith("1") ? 1 : (token.endsWith("2") ? 2 : parseBoundedInt(args.length >= 2 ? args[1] : "2", 1, 2, 2));
            target.getInventory().addItem(plugin.getGemItemManager().createGemSelectorItem(selectorTier));
            sender.sendMessage("Gave gem selector T" + selectorTier + " to " + target.getName());
            return true;
        }

        int tier = parseBoundedInt(args.length >= 2 ? args[1] : "2", 1, 2, 2);

        ItemStack gem;
        if (token.equals("random")) {
            gem = plugin.getGemItemManager().getRandomGem(tier, 5);
        } else {
            GemType type = GemType.fromIdentifier(token);
            if (type == null) {
                sender.sendMessage("Unknown gem. Use: fire, flux, strength, life, speed, puff, wealth, astra");
                return true;
            }
            gem = plugin.getGemItemManager().getGem(type, tier, 5);
        }

        if (gem == null) {
            sender.sendMessage("Could not create gem with requested values.");
            return true;
        }

        target.getInventory().addItem(gem);
        sender.sendMessage("Gave gem to " + target.getName() + " (tier " + tier + ", pristine 5)");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("random");
            out.add("trader");
            out.add("upgrader");
            out.add("selector");
            out.add("selector1");
            out.add("selector2");
            out.add("gold");
            for (GemType type : GemType.values()) {
                out.add(type.getIdentifier());
            }
            return out;
        }
        if (args.length == 2) {
            out.add("1");
            out.add("2");
            return out;
        }
        return out;
    }

    private int parseBoundedInt(String value, int min, int max, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min) {
                return min;
            }
            if (parsed > max) {
                return max;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
