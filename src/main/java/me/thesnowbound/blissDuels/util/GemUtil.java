package me.thesnowbound.blissDuels.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.gem.*;

/**
 * Utility class for checking and extracting gem data from ItemStacks
 */
public class GemUtil {
    private static final NamespacedKey GEM_TYPE_KEY = new NamespacedKey(BlissDuels.getInstance(), "gem_type");
    private static final NamespacedKey GEM_TIER_KEY = new NamespacedKey(BlissDuels.getInstance(), "gem_tier");
    private static final NamespacedKey GEM_ENERGY_KEY = new NamespacedKey(BlissDuels.getInstance(), "gem_energy");

    private static final GemEnergy FIXED_GEM_ENERGY = GemEnergy.PRISTINE;

    /**
     * Checks if an item is a gem by examining its lore for gem-specific text
     */
    public static boolean isGem(ItemStack item) {
        return getOrMigrateGemData(item) != null;
    }

    /**
     * Gets the gem type from an item's display name
     */
    public static GemType getGem(ItemStack item) {
        Gem data = getOrMigrateGemData(item);
        return data == null ? null : data.getType();
    }

    private static GemType getGemFromCustomModelData(int customModelData) {
        for (GemType gem : GemType.values()) {
            if (customModelData == gem.getCustomModelData() || customModelData == gem.getTier1CustomModelData()) {
                return gem;
            }
        }
        return null;
    }

    /**
     * Gets the tier level of a gem from the item type
     * Amethyst Shard = Tier 1, Prismarine Shard = Tier 2
     */
    public static GemTier getTier(ItemStack item) {
        Gem data = getOrMigrateGemData(item);
        if (data != null) {
            return data.getTier();
        }
        return GemTier.TIER_1;
    }

    /**
     * Gets the energy level from a gem's lore
     */
    public static GemEnergy getEnergy(ItemStack item) {
        Gem data = getOrMigrateGemData(item);
        if (data != null) {
            return FIXED_GEM_ENERGY;
        }
        return GemEnergy.BROKEN;
    }

    /**
     * Gets the energy level as an integer
     */
    public static int getEnergyLevel(ItemStack item) {
        return isGem(item) ? FIXED_GEM_ENERGY.getLevel() : GemEnergy.BROKEN.getLevel();
    }

    /**
     * Saves gem data to NBT tags on the item
     */
    public static void saveGemData(ItemStack item, GemType type, GemTier tier, GemEnergy energy) {
        if (item == null || item.getItemMeta() == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        container.set(GEM_TYPE_KEY, PersistentDataType.STRING, type.getIdentifier());
        container.set(GEM_TIER_KEY, PersistentDataType.INTEGER, tier.getLevel());
        // Energy system removed: always persist pristine energy.
        container.set(GEM_ENERGY_KEY, PersistentDataType.INTEGER, FIXED_GEM_ENERGY.getLevel());

        item.setItemMeta(meta);
    }

    /**
     * Loads gem data from NBT tags on the item
     */
    public static Gem loadGemData(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        if (!container.has(GEM_TYPE_KEY, PersistentDataType.STRING)) {
            return null;
        }

        String typeId = container.get(GEM_TYPE_KEY, PersistentDataType.STRING);
        int tierLevel = container.getOrDefault(GEM_TIER_KEY, PersistentDataType.INTEGER, 1);

        GemType type = GemType.fromIdentifier(typeId);
        if (type == null) {
            return null;
        }

        GemTier tier = GemTier.fromLevel(tierLevel);
        return new Gem(type, tier, FIXED_GEM_ENERGY);
    }

    private static Gem getOrMigrateGemData(ItemStack item) {
        Gem tagged = loadGemData(item);
        if (tagged != null) {
            return tagged;
        }

        Gem legacy = inferLegacyGemData(item);
        if (legacy == null) {
            return null;
        }

        saveGemData(item, legacy.getType(), legacy.getTier(), legacy.getEnergy());
        return loadGemData(item);
    }

    private static Integer readCustomModelData(ItemMeta meta) {
        if (meta == null) {
            return null;
        }

        if (meta.hasCustomModelData()) {
            return meta.getCustomModelData();
        }

        // 1.21+ component fallback.
        try {
            Object component = meta.getClass().getMethod("getCustomModelDataComponent").invoke(meta);
            if (component == null) {
                return null;
            }
            Object floatsObj = component.getClass().getMethod("getFloats").invoke(component);
            if (!(floatsObj instanceof java.util.List<?> floats) || floats.isEmpty()) {
                return null;
            }
            Object first = floats.get(0);
            if (first instanceof Number number) {
                return Math.round(number.floatValue());
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }

        return null;
    }

    private static Gem inferLegacyGemData(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        GemType type = null;
        Integer cmdData = readCustomModelData(meta);
        if (cmdData != null) {
            type = getGemFromCustomModelData(cmdData);
        }

        if (type == null && meta.hasDisplayName()) {
            String plain = ChatColor.stripColor(meta.getDisplayName());
            if (plain == null) {
                plain = meta.getDisplayName();
            }
            String lower = plain.toLowerCase();
            for (GemType gem : GemType.values()) {
                if (lower.contains(gem.getIdentifier())) {
                    type = gem;
                    break;
                }
            }
        }

        if (type == null) {
            return null;
        }

        GemTier tier = inferLegacyTier(item, meta, type);
        GemEnergy energy = inferLegacyEnergy(meta);
        return new Gem(type, tier, energy);
    }

    private static GemTier inferLegacyTier(ItemStack item, ItemMeta meta, GemType type) {
        Integer cmd = readCustomModelData(meta);
        if (cmd != null) {
            if (cmd == type.getTier1CustomModelData()) {
                return GemTier.TIER_1;
            }
            if (cmd == type.getCustomModelData()) {
                return GemTier.TIER_2;
            }
        }

        if (item.getType() == Material.AMETHYST_SHARD) {
            return GemTier.TIER_1;
        }
        if (item.getType() == Material.PRISMARINE_SHARD) {
            return GemTier.TIER_2;
        }
        return GemTier.TIER_1;
    }

    private static GemEnergy inferLegacyEnergy(ItemMeta meta) {
        // Energy system removed: legacy gems are normalized to pristine.
        return FIXED_GEM_ENERGY;
    }
}
