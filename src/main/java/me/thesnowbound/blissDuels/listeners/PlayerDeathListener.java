package me.thesnowbound.blissDuels.listeners;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.gem.GemTier;
import me.thesnowbound.blissDuels.util.GemUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Handles death-system parity while gems remain fixed at pristine energy.
 */
public class PlayerDeathListener implements Listener {
    private final BlissDuels plugin;

    public PlayerDeathListener(BlissDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        handleVictimGemDrops(event, victim);
    }

    private void handleVictimGemDrops(PlayerDeathEvent event, Player victim) {
        List<ItemStack> restoreGems = new ArrayList<>();

        Iterator<ItemStack> dropIterator = event.getDrops().iterator();
        while (dropIterator.hasNext()) {
            ItemStack drop = dropIterator.next();
            if (!GemUtil.isGem(drop)) {
                continue;
            }

            GemTier tier = GemUtil.getTier(drop);
            dropIterator.remove();

            if (tier == GemTier.TIER_2) {
                victim.getWorld().dropItemNaturally(victim.getLocation(), plugin.getGemItemManager().createUpgraderItem());
            }

            restoreGems.add(drop.clone());
        }

        if (restoreGems.isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!victim.isOnline()) {
                return;
            }
            for (ItemStack gem : restoreGems) {
                victim.getInventory().addItem(gem);
            }
        }, 1L);
    }
}
