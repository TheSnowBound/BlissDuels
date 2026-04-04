package me.thesnowbound.blissDuels.managers;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.entity.Player;

/**
 * Manages ability cooldowns for players
 */
public class CooldownManager {
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final CooldownConfigManager configManager;

    public CooldownManager(CooldownConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Sets a cooldown for a player ability
     */
    public void setCooldown(Player player, String abilityName, long milliseconds) {
        String key = getCooldownKey(player, abilityName);
        double multiplier = configManager.getMultiplier(abilityName);
        long adjusted = Math.max(0L, Math.round(milliseconds * multiplier));
        cooldowns.put(key, System.currentTimeMillis() + adjusted);
    }

    /**
     * Checks if a cooldown is ready (or doesn't exist)
     */
    public boolean isCooldownReady(Player player, String abilityName) {
        String key = getCooldownKey(player, abilityName);
        Long cooldownTime = cooldowns.get(key);
        if (cooldownTime == null) {
            return true;
        }
        return System.currentTimeMillis() >= cooldownTime;
    }

    /**
     * Gets remaining cooldown in milliseconds
     */
    public long getRemaining(Player player, String abilityName) {
        String key = getCooldownKey(player, abilityName);
        Long cooldownTime = cooldowns.get(key);
        if (cooldownTime == null) {
            return 0;
        }
        long remaining = cooldownTime - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * Gets remaining cooldown in seconds
     */
    public int getRemainingSeconds(Player player, String abilityName) {
        return (int)(getRemaining(player, abilityName) / 1000);
    }

    /**
     * Resets all cooldowns for a player
     */
    public void resetAll(Player player) {
        cooldowns.keySet().removeIf(key -> key.startsWith(player.getUniqueId().toString()));
    }

    /**
     * Resets a specific cooldown for a player
     */
    public void reset(Player player, String abilityName) {
        String key = getCooldownKey(player, abilityName);
        cooldowns.remove(key);
    }

    private String getCooldownKey(Player player, String abilityName) {
        return player.getUniqueId() + ":" + abilityName;
    }
}
