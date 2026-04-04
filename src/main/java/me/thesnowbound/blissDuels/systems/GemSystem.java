package me.thesnowbound.blissDuels.systems;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.util.ColorUtil;
import me.thesnowbound.blissDuels.util.GemUtil;
import me.thesnowbound.blissDuels.gem.GemType;

import java.util.*;

/**
 * Handles gem-related game mechanics
 */
public class GemSystem {
    private static final int PRISTINE_ENERGY_LEVEL = 5;
    private final BlissDuels plugin;
    private final List<String> gemNames = Arrays.asList(
        "strength", "fire", "speed", "wealth", "flux", "astra", "puff", "life"
    );

    public GemSystem(BlissDuels plugin) {
        this.plugin = plugin;
    }

    /**
     * Trades player's current gem to a random different gem
     */
    public void randomGem(Player player) {
        PlayerInventory inventory = player.getInventory();

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null) continue;

            if (!GemUtil.isGem(item)) continue;

            // Get current gem info
            GemType currentGem = GemUtil.getGem(item);
            int tier = GemUtil.getTier(item).getLevel();

            // Get random different gem
            List<String> availableGems = new ArrayList<>(gemNames);
            if (currentGem != null) {
                availableGems.remove(currentGem.getIdentifier());
            }

            if (availableGems.isEmpty()) {
                continue;
            }

            String randomGemName = availableGems.get((int) (Math.random() * availableGems.size()));
            GemType newGemType = GemType.fromIdentifier(randomGemName);

            if (newGemType != null) {
                // Energy system removed: all gems are recreated at pristine level 5.
                ItemStack newGem = plugin.getGemItemManager().getGem(newGemType, tier, PRISTINE_ENERGY_LEVEL);
                if (newGem != null) {
                    inventory.setItem(i, newGem);
                    String gemColorName = getGemColorName(newGemType);
                    player.sendMessage(ColorUtil.color("<##FFD773>🔮 <##A4FEF1>You have traded your current gem to " + gemColorName));
                }
            }
        }

        player.playSound(player.getLocation(), "entity.player.levelup", 1.0f, 1.0f);
    }

    /**
     * Gets the colored name of a gem type
     */
    public String getGemColorName(GemType type) {
        if (type == null) return "N/A";

        return switch (type) {
            case FIRE -> "<##FE8120>&lғɪʀᴇ";
            case FLUX -> "<##5ED7FF>&lғʟᴜx";
            case STRENGTH -> "<##F10103>&lsᴛʀᴇɴɢᴛʜ";
            case LIFE -> "<##FE04B4>&lʟɪғᴇ";
            case SPEED -> "<##FEFD17>&lsᴘᴇᴇᴅ";
            case PUFF -> "<##EFEFEF>&lᴘᴜғғ";
            case WEALTH -> "<##0EC912>&lᴡᴇᴀʟᴛʜ";
            case ASTRA -> "<##A01FFF>&lᴀsᴛʀᴀ";
        };
    }

    /**
     * Restores a gem to a random type with tier 1 and energy 5
     */
    public void restoreGem(Player player) {
        PlayerInventory inventory = player.getInventory();

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null) continue;

            if (!GemUtil.isGem(item)) continue;

            // Get random gem
            GemType[] allGems = GemType.values();
            GemType randomGem = allGems[(int) (Math.random() * allGems.length)];

            ItemStack newGem = plugin.getGemItemManager().getGem(randomGem, 1, PRISTINE_ENERGY_LEVEL);
            if (newGem != null) {
                inventory.setItem(i, newGem);
            }
        }

        player.playSound(player.getLocation(), "entity.player.levelup", 1.0f, 1.0f);
    }

    /**
     * Energy system removed: this now normalizes all gems to pristine level 5.
     */
    public void setEnergy(Player player, int energyLevel) {
        PlayerInventory inventory = player.getInventory();

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null) continue;

            if (!GemUtil.isGem(item)) continue;

            GemType gemType = GemUtil.getGem(item);
            int tier = GemUtil.getTier(item).getLevel();

            if (gemType != null) {
                ItemStack newGem = plugin.getGemItemManager().getGem(gemType, tier, PRISTINE_ENERGY_LEVEL);
                if (newGem != null) {
                    inventory.setItem(i, newGem);
                }
            }
        }
    }

    /**
     * Gets the list of all gem names
     */
    public List<String> getGemNames() {
        return new ArrayList<>(gemNames);
    }
}
