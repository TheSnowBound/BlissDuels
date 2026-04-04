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
}

