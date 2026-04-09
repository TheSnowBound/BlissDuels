package me.thesnowbound.blissDuels.managers;

import me.thesnowbound.blissDuels.BlissDuels;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Loads cooldown multipliers from cooldowns.yml.
 */
public class CooldownConfigManager {
    private final BlissDuels plugin;
    private final File cooldownFile;
    private FileConfiguration cooldownConfig;

    public CooldownConfigManager(BlissDuels plugin) {
        this.plugin = plugin;
        this.cooldownFile = new File(plugin.getDataFolder(), "cooldowns.yml");
        reload();
    }

    public void reload() {
        if (!cooldownFile.exists()) {
            plugin.saveResource("cooldowns.yml", false);
        }
        cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);
    }

    public double getMultiplier(String abilityKey) {
        double global = cooldownConfig.getDouble("multipliers.default", 1.0D);
        return cooldownConfig.getDouble("multipliers." + abilityKey, global);
    }

    public java.util.Set<String> getAllAbilityKeys() {
        if (cooldownConfig == null) return java.util.Set.of();
        if (!cooldownConfig.isConfigurationSection("multipliers")) return java.util.Set.of();
        java.util.Set<String> keys = cooldownConfig.getConfigurationSection("multipliers").getKeys(false);
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String k : keys) {
            if (k == null) continue;
            if (k.equalsIgnoreCase("default")) continue;
            out.add(k);
        }
        return out;
    }
}
