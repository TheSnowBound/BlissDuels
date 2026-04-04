package me.thesnowbound.blissDuels.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.entity.Player;
import me.thesnowbound.blissDuels.systems.PassiveEffectSystem;

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
}
