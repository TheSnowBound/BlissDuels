package me.thesnowbound.blissDuels.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.Player;
import me.thesnowbound.blissDuels.systems.PassiveEffectSystem;
import org.bukkit.Bukkit;

/**
 * Handles passive effects when player holds a gem
 */
public class PassiveEffectListener implements Listener {
    private final PassiveEffectSystem passiveEffectSystem;

    public PassiveEffectListener() {
        this.passiveEffectSystem = new PassiveEffectSystem();
    }

    @EventHandler
    public void onPlayerHoldItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // Apply passive effects when player switches items
        passiveEffectSystem.applyPassiveEffects(player);
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        // Offhand change - reapply passives
        passiveEffectSystem.applyPassiveEffects(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Apply passives on join
        Bukkit.getScheduler().runTaskLater(me.thesnowbound.blissDuels.BlissDuels.getInstance(), () -> passiveEffectSystem.applyPassiveEffects(player), 1L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Inventory equip/unequip can be slightly delayed; schedule a tick later to re-evaluate
        Bukkit.getScheduler().runTaskLater(me.thesnowbound.blissDuels.BlissDuels.getInstance(), () -> passiveEffectSystem.applyPassiveEffects(player), 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        passiveEffectSystem.clearPassiveEffects(player);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        // Re-evaluate passives after a short tick to allow the drop to apply
        Bukkit.getScheduler().runTaskLater(me.thesnowbound.blissDuels.BlissDuels.getInstance(), () -> passiveEffectSystem.applyPassiveEffects(player), 1L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Reapply passives on respawn
        Bukkit.getScheduler().runTaskLater(me.thesnowbound.blissDuels.BlissDuels.getInstance(), () -> passiveEffectSystem.applyPassiveEffects(player), 1L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        // Clear passives on death; they will be re-applied on respawn/join
        passiveEffectSystem.clearPassiveEffects(player);
    }
}
