package me.thesnowbound.blissDuels.managers;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles trust relationships and persists them to trust.yml.
 */
public class TrustManager {
    public static final String TRUST_GUI_TITLE = "Trusted Players";

    private final BlissDuels plugin;
    private final File file;
    private final NamespacedKey trustTargetKey;

    private FileConfiguration config;
    private final Map<UUID, Set<UUID>> trusted = new HashMap<>();

    public TrustManager(BlissDuels plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "trust.yml");
        this.trustTargetKey = new NamespacedKey(plugin, "trust_target");
        load();
    }

    public boolean isTrusted(Player owner, LivingEntity target) {
        if (!(target instanceof Player targetPlayer)) {
            return false;
        }
        Set<UUID> list = trusted.get(owner.getUniqueId());
        return list != null && list.contains(targetPlayer.getUniqueId());
    }

    public boolean addTrusted(UUID owner, UUID target) {
        if (owner.equals(target)) {
            return false;
        }
        Set<UUID> list = trusted.computeIfAbsent(owner, ignored -> new HashSet<>());
        boolean added = list.add(target);
        if (added) {
            save();
        }
        return added;
    }

    public boolean removeTrusted(UUID owner, UUID target) {
        Set<UUID> list = trusted.get(owner);
        if (list == null) {
            return false;
        }
        boolean removed = list.remove(target);
        if (removed) {
            if (list.isEmpty()) {
                trusted.remove(owner);
            }
            save();
        }
        return removed;
    }

    public Set<UUID> getTrusted(UUID owner) {
        Set<UUID> list = trusted.get(owner);
        if (list == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(list);
    }

    public Inventory buildUntrustGui(Player owner) {
        Inventory gui = Bukkit.createInventory(null, 27, TRUST_GUI_TITLE);
        int slot = 0;

        for (UUID targetId : getTrusted(owner.getUniqueId())) {
            if (slot >= gui.getSize()) {
                break;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta rawMeta = skull.getItemMeta();
            if (!(rawMeta instanceof SkullMeta meta)) {
                continue;
            }

            meta.setOwningPlayer(target);
            String name = target.getName() == null ? targetId.toString() : target.getName();
            meta.setDisplayName(ColorUtil.color("<##B8FFFB>" + name));
            meta.setLore(ColorUtil.color(List.of("", "&fClick to &cuntrust")));

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(trustTargetKey, PersistentDataType.STRING, targetId.toString());

            skull.setItemMeta(meta);
            gui.setItem(slot++, skull);
        }

        return gui;
    }

    public UUID parseTrustTarget(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(trustTargetKey, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void save() {
        if (config == null) {
            config = new YamlConfiguration();
        }

        config.set("trusted", null);
        for (Map.Entry<UUID, Set<UUID>> entry : trusted.entrySet()) {
            List<String> values = new ArrayList<>();
            for (UUID target : entry.getValue()) {
                values.add(target.toString());
            }
            config.set("trusted." + entry.getKey(), values);
        }

        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save trust.yml: " + exception.getMessage());
        }
    }

    private void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to create trust.yml: " + exception.getMessage());
            }
        }

        this.config = YamlConfiguration.loadConfiguration(file);
        trusted.clear();

        if (config.getConfigurationSection("trusted") == null) {
            return;
        }

        for (String ownerRaw : config.getConfigurationSection("trusted").getKeys(false)) {
            UUID owner;
            try {
                owner = UUID.fromString(ownerRaw);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            List<String> targets = config.getStringList("trusted." + ownerRaw);
            if (targets.isEmpty()) {
                continue;
            }

            Set<UUID> list = trusted.computeIfAbsent(owner, ignored -> new HashSet<>());
            for (String targetRaw : targets) {
                try {
                    list.add(UUID.fromString(targetRaw));
                } catch (IllegalArgumentException ignored) {
                    // Ignore bad entries to keep file resilient.
                }
            }
        }
    }
}

