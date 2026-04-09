package me.thesnowbound.blissDuels.listeners;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.util.GemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Anti-duplication and anti-stash guard for gem items.
 * Ported from reference/imp/nodupe.sk behavior.
 */
public class NoDupeListener implements Listener {
    private final BlissDuels plugin;
    private final boolean enabled;

    public NoDupeListener(BlissDuels plugin) {
        this.plugin = plugin;
        this.enabled = readEnabledFlag(plugin);

        if (!enabled) {
            return;
        }

        // Skript parity: periodically enforce single-gem inventory invariant.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                removeExtraGems(player);
            }
        }, 100L, 100L);
    }

    @EventHandler()
    public void onDrop(PlayerDropItemEvent event) {
        if (!enabled) {
            return;
        }
        if (isProtectedItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!enabled) {
            return;
        }
        Player player = event.getEntity();
        List<ItemStack> keptGems = new ArrayList<>();

        event.getDrops().removeIf(drop -> {
            if (!GemUtil.isGem(drop)) {
                return false;
            }
            // If this drop is a Gold Gem, allow it to drop (per user request)
            boolean isGold = BlissDuels.getInstance().getGemItemManager().isGoldGem(drop);
            if (isGold) {
                return false; // keep gold gem in drops
            }

            // otherwise remove normal gems from drops and keep them for later
            keptGems.add(drop.clone());
            return true;
        });

        if (keptGems.isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            for (ItemStack gem : keptGems) {
                player.getInventory().addItem(gem);
            }
            removeExtraGems(player);
        }, 1L);
    }

    private void removeExtraGems(Player player) {
        // Allow one normal gem and one gold gem in inventory; remove any additional gems
        boolean foundNormal = false;
        boolean foundGold = false;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!GemUtil.isGem(item)) {
                continue;
            }
            boolean isGold = BlissDuels.getInstance().getGemItemManager().isGoldGem(item);
            if (isGold) {
                if (!foundGold) {
                    foundGold = true;
                    continue;
                }
                // extra gold gem -> remove
                player.getInventory().setItem(slot, null);
                continue;
            }
            // normal gem
            if (!foundNormal) {
                foundNormal = true;
                continue;
            }
            // extra normal gem -> remove
            player.getInventory().setItem(slot, null);
        }
    }

    private boolean isBundle(ItemStack item) {
        return item != null && item.getType() == Material.BUNDLE;
    }

    private boolean isProtectedItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return GemUtil.isGem(item) || item.getType() == Material.DRAGON_EGG || item.getType().name().equals("MACE");
    }

    private boolean readEnabledFlag(BlissDuels plugin) {
        File featuresFile = new File(plugin.getDataFolder(), "features.yml");
        if (!featuresFile.exists()) {
            return true;
        }

        FileConfiguration features = YamlConfiguration.loadConfiguration(featuresFile);
        return features.getBoolean("features.nodupe", true);
    }
}
