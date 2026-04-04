package me.thesnowbound.blissDuels.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.util.GemUtil;

/**
 * Handles inventory click events for trading and gem management
 */
public class InventoryClickListener implements Listener {
    private final BlissDuels plugin;

    public InventoryClickListener(BlissDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack cursorItem = event.getCursor();
        ItemStack clickedItem = event.getCurrentItem();

        // Prevent gem drops during trading
        if (GemUtil.isGem(clickedItem)) {
            // TODO: Add trading logic here
        }
    }
}

