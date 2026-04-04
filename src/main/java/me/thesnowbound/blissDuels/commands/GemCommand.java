package me.thesnowbound.blissDuels.commands;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.gem.GemType;
import me.thesnowbound.blissDuels.util.ColorUtil;
import me.thesnowbound.blissDuels.util.GemUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GemCommand implements CommandExecutor, TabCompleter {
    private final BlissDuels plugin;

    public GemCommand(BlissDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("blissgem.admin")) {
                sender.sendMessage(ColorUtil.color("&cYou do not have permission to reload config."));
                return true;
            }
            plugin.reloadConfig();
            sender.sendMessage(ColorUtil.color("&aReloaded config.yml for /gem settings."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /gem. Use /gem reload from console/admin.");
            return true;
        }

        if (hasAnyGem(player)) {
            player.sendMessage(ColorUtil.color("<##FFD773>You already have a gem."));
            return true;
        }

        int tier = resolveConfiguredTier();
        ItemStack gem = createRandomGem(tier);
        if (gem == null) {
            player.sendMessage(ColorUtil.color("&cFailed to create a gem. Contact an admin."));
            return true;
        }

        player.getInventory().addItem(gem);
        player.sendMessage(ColorUtil.color("<##FFD773>You received a tier " + tier + " gem."));
        return true;
    }

    private boolean hasAnyGem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (GemUtil.isGem(item)) {
                return true;
            }
        }
        return false;
    }

    private int resolveConfiguredTier() {
        int tier = plugin.getConfig().getInt("auto-gem.tier", 2);
        return tier == 1 ? 1 : 2;
    }

    private ItemStack createRandomGem(int tier) {
        GemType[] types = GemType.values();
        GemType type = types[ThreadLocalRandom.current().nextInt(types.length)];
        return plugin.getGemItemManager().getGem(type, tier, 5);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("blissgem.admin")) {
            out.add("reload");
        }
        return out;
    }
}
