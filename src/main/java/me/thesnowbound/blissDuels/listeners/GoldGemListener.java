package me.thesnowbound.blissDuels.listeners;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.managers.GemItemManager;
import me.thesnowbound.blissDuels.util.GemUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles gold gem special interactions:
 * - dropping the gold gem cycles its selected gem (drop is cancelled)
 */
public class GoldGemListener implements Listener {
    private final BlissDuels plugin;
    private final GemItemManager gemItemManager;

    public GoldGemListener(BlissDuels plugin) {
        this.plugin = plugin;
        this.gemItemManager = plugin.getGemItemManager();
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (dropped == null) return;

        if (gemItemManager.isGoldGem(dropped) || gemItemManager.isGoldGem(event.getPlayer().getInventory().getItemInMainHand())) {
            // cancel actual drop; cycle selection
            event.setCancelled(true);

            ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
            if (hand == null || hand.getType().isAir()) {
                hand = dropped;
            }

            ItemStack updated = gemItemManager.cycleGoldSelection(hand);
            event.getPlayer().getInventory().setItemInMainHand(updated);
            event.getPlayer().sendMessage("§eGold Gem selection cycled to: " + GemUtil.getGem(updated).getIdentifier() + " (T" + GemUtil.getTier(updated).getLevel() + ")");
        }
    }
}

