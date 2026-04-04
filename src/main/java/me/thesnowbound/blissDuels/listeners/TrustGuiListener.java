package me.thesnowbound.blissDuels.listeners;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.managers.TrustManager;
import me.thesnowbound.blissDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Handles click-to-untrust behavior for /trust list GUI.
 */
public class TrustGuiListener implements Listener {
    private final BlissDuels plugin;

    public TrustGuiListener(BlissDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!TrustManager.TRUST_GUI_TITLE.equals(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        UUID targetId = plugin.getTrustManager().parseTrustTarget(clicked);
        if (targetId == null) {
            return;
        }

        boolean removed = plugin.getTrustManager().removeTrusted(player.getUniqueId(), targetId);
        if (!removed) {
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        String name = target.getName() == null ? targetId.toString() : target.getName();
        player.sendMessage(ColorUtil.color("<##FFD773>🔮 &cUntrusted <##B8FFFB>" + name));
        player.openInventory(plugin.getTrustManager().buildUntrustGui(player));
    }
}
