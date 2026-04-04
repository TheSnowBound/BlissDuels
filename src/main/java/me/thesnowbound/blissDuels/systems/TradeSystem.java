package me.thesnowbound.blissDuels.systems;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.util.ColorUtil;
import me.thesnowbound.blissDuels.util.GemUtil;
import me.thesnowbound.blissDuels.gem.GemType;

/**
 * Handles gem trading mechanics and UI
 */
public class TradeSystem {
    private final BlissDuels plugin;

    public TradeSystem(BlissDuels plugin) {
        this.plugin = plugin;
    }

    /**
     * Upgrades a gem from tier 1 to tier 2
     */
    public boolean upgradeGem(Player player, ItemStack gem) {
        if (!GemUtil.isGem(gem)) {
            return false;
        }

        GemType gemType = GemUtil.getGem(gem);
        int tierLevel = GemUtil.getTier(gem).getLevel();
        int energyLevel = GemUtil.getEnergyLevel(gem);

        // Can only upgrade tier 1 to tier 2
        if (tierLevel != 1) {
            player.sendMessage(ColorUtil.color("<##FFD773>🔮 <##FF686F>Your gem is already upgraded"));
            return false;
        }

        // Create tier 2 version
        ItemStack upgradedGem = plugin.getGemItemManager().getGem(gemType, 2, energyLevel);
        if (upgradedGem == null) {
            return false;
        }

        // Replace in inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.equals(gem)) {
                player.getInventory().setItem(i, upgradedGem);
                player.sendMessage(ColorUtil.color("<##FFD773>🔮 <##B8FFFB>You have upgraded your gem to tier 2"));
                player.playSound(player.getLocation(), "entity.player.levelup", 1.0f, 1.0f);
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if a player has a specific gem
     */
    public boolean hasGem(Player player, GemType gemType) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && GemUtil.isGem(item)) {
                if (GemUtil.getGem(item) == gemType) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets player's current gem (first gem found)
     */
    public ItemStack getPlayerGem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && GemUtil.isGem(item)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Counts how many gems a player has
     */
    public int getGemCount(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && GemUtil.isGem(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }
}
