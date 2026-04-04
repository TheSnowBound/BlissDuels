package me.thesnowbound.blissDuels.managers;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.gem.*;
import me.thesnowbound.blissDuels.systems.GemLoreSystem;
import me.thesnowbound.blissDuels.util.ColorUtil;
import me.thesnowbound.blissDuels.util.GemUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages all gem item definitions and creation
 */
public class GemItemManager {
    private final Map<String, ItemStack> gemItems = new HashMap<>();
    private final GemLoreSystem loreSystem = new GemLoreSystem();

    private static final String TRADER_NAME = "<##E9C2FF>&lTRADER";
    private static final String UPGRADER_NAME = "<##ACFF82>&lUPGRADER";
    private static final String SELECTOR_NAME = "<##E5B8FF>&lGEM SELECTOR";

    private final NamespacedKey specialItemKey = new NamespacedKey(BlissDuels.getInstance(), "special_item");
    private final NamespacedKey energyFromKey = new NamespacedKey(BlissDuels.getInstance(), "energy_from");
    private final NamespacedKey energyMinutesKey = new NamespacedKey(BlissDuels.getInstance(), "energy_expire_minutes");

    private static final GemEnergy FIXED_GEM_ENERGY = GemEnergy.PRISTINE;

    public GemItemManager() {
        initializeAllGems();
    }

    private void initializeAllGems() {
        for (GemType type : GemType.values()) {
            // Energy system removed: only cache pristine gems.
            createAndStoreGem(type, GemTier.TIER_2, FIXED_GEM_ENERGY);
            createAndStoreGem(type, GemTier.TIER_1, FIXED_GEM_ENERGY);
        }
    }

    private void createAndStoreGem(GemType type, GemTier tier, GemEnergy energy) {
        ItemStack item = createGemItem(type, tier, energy);
        String key = getGemKey(type, tier, energy);
        gemItems.put(key, item);
    }

    private ItemStack createGemItem(GemType type, GemTier tier, GemEnergy energy) {
        Material material = tier == GemTier.TIER_2 ? Material.PRISMARINE_SHARD : Material.AMETHYST_SHARD;
        ItemStack item = new ItemStack(material);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // Set display name
        String colorCode = tier == GemTier.TIER_2 ? "<##FFD773>" : "<##C7c7c7>";
        meta.setDisplayName(ColorUtil.color(type.getDisplayName() + " " + colorCode + "ɢᴇᴍ"));

        // Build lore
        java.util.List<String> lore = loreSystem.buildLore(type, tier, energy);
        meta.setLore(ColorUtil.color(lore));

        int customModelData = tier == GemTier.TIER_2 ?
            type.getCustomModelData() :
            type.getTier1CustomModelData();
        applyCustomModelData(meta, customModelData);

        item.setItemMeta(meta);

        // Persist gem data for reliable parsing in gameplay listeners.
        GemUtil.saveGemData(item, type, tier, energy);
        return item;
    }

    private String getGemKey(GemType type, GemTier tier, GemEnergy energy) {
        return String.format("%s:%d:%d", type.getIdentifier(), tier.getLevel(), energy.getLevel());
    }

    public ItemStack getGem(GemType type, GemTier tier, GemEnergy energy) {
        String key = getGemKey(type, tier, FIXED_GEM_ENERGY);
        ItemStack item = gemItems.get(key);
        return item != null ? item.clone() : null;
    }

    public ItemStack getGem(GemType type, int tierLevel, int energyLevel) {
        GemTier tier = GemTier.fromLevel(tierLevel);
        return getGem(type, tier, FIXED_GEM_ENERGY);
    }


    public ItemStack getRandomGem(int tier, int energy) {
        GemType[] gems = GemType.values();
        GemType randomGem = gems[(int)(Math.random() * gems.length)];
        return getGem(randomGem, tier, FIXED_GEM_ENERGY.getLevel());
    }

    public ItemStack createTraderItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(ColorUtil.color(TRADER_NAME));
        meta.setLore(ColorUtil.color(Arrays.asList(
            "&7Left click for ultimate trading menu",
            "&7Right click to receive a random gem",
            "&f",
            "&cWarning: this consumes your current gem"
        )));
        applyCustomModelData(meta, 320);
        meta.getPersistentDataContainer().set(specialItemKey, PersistentDataType.STRING, "trader");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createUpgraderItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(ColorUtil.color(UPGRADER_NAME));
        meta.setLore(ColorUtil.color(Arrays.asList(
            "&7Right click to upgrade your gem",
            "&f",
            "&cWarning: upgrader drops on death"
        )));
        applyCustomModelData(meta, 321);
        meta.getPersistentDataContainer().set(specialItemKey, PersistentDataType.STRING, "upgrader");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createEnergyBottle(int expiresMinutes, String fromName) {
        ItemStack item = new ItemStack(Material.NAUTILUS_SHELL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String safeFrom = (fromName == null || fromName.isBlank()) ? "None" : fromName;
        int safeMinutes = Math.max(0, expiresMinutes);

        meta.setDisplayName(ColorUtil.color("<##96FFD9>&lᴇɴᴇʀɢʏ <##FFE4AB>ɪɴ ᴀ ʙᴏᴛᴛʟᴇ"));
        meta.setLore(ColorUtil.color(Arrays.asList(
            "<##96FFD9>&lFrom: <##FFE4AB>" + safeFrom,
            "&7Expires in: &f&l" + safeMinutes + " <##FFE4AB>minutes"
        )));
        applyCustomModelData(meta, 300);
        meta.getPersistentDataContainer().set(specialItemKey, PersistentDataType.STRING, "energy_bottle");
        meta.getPersistentDataContainer().set(energyFromKey, PersistentDataType.STRING, safeFrom);
        meta.getPersistentDataContainer().set(energyMinutesKey, PersistentDataType.INTEGER, safeMinutes);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isEnergyBottle(ItemStack item) {
        return hasSpecialType(item, "energy_bottle");
    }


    private boolean hasSpecialType(ItemStack item, String type) {
        if (item == null || item.getType() == Material.AIR || item.getItemMeta() == null) {
            return false;
        }
        String current = item.getItemMeta().getPersistentDataContainer().get(specialItemKey, PersistentDataType.STRING);
        return type.equalsIgnoreCase(current);
    }

    public boolean isTraderItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getItemMeta() == null) {
            return false;
        }
        String type = item.getItemMeta().getPersistentDataContainer().get(specialItemKey, PersistentDataType.STRING);
        return "trader".equalsIgnoreCase(type);
    }

    public boolean isUpgraderItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getItemMeta() == null) {
            return false;
        }
        String type = item.getItemMeta().getPersistentDataContainer().get(specialItemKey, PersistentDataType.STRING);
        return "upgrader".equalsIgnoreCase(type);
    }

    public ItemStack createGemSelectorItem(int tier) {
        int safeTier = tier == 1 ? 1 : 2;
        ItemStack item = new ItemStack(Material.NAUTILUS_SHELL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(ColorUtil.color(SELECTOR_NAME + " <##FFD773>T" + safeTier));
        meta.setLore(ColorUtil.color(Arrays.asList(
            "&7Right click to open gem selection",
            "&7Tier: &f" + safeTier,
            "&f",
            "&cConsumed on selection"
        )));
        applyCustomModelData(meta, safeTier == 1 ? 330 : 331);
        meta.getPersistentDataContainer().set(specialItemKey, PersistentDataType.STRING, safeTier == 1 ? "gem_selector_t1" : "gem_selector_t2");
        item.setItemMeta(meta);
        return item;
    }

    public boolean isGemSelectorItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getItemMeta() == null) {
            return false;
        }
        String type = item.getItemMeta().getPersistentDataContainer().get(specialItemKey, PersistentDataType.STRING);
        return "gem_selector_t1".equalsIgnoreCase(type) || "gem_selector_t2".equalsIgnoreCase(type);
    }

    public int getGemSelectorTier(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getItemMeta() == null) {
            return 2;
        }
        String type = item.getItemMeta().getPersistentDataContainer().get(specialItemKey, PersistentDataType.STRING);
        return "gem_selector_t1".equalsIgnoreCase(type) ? 1 : 2;
    }

    private void applyCustomModelData(ItemMeta meta, int customModelData) {
        // Keep legacy integer path for older APIs/resource packs.
        meta.setCustomModelData(customModelData);

        // Also set the 1.21+ component data when available.
        try {
            Object component = meta.getClass().getMethod("getCustomModelDataComponent").invoke(meta);
            if (component != null) {
                component.getClass().getMethod("setFloats", java.util.List.class)
                    .invoke(component, java.util.List.of((float) customModelData));
                meta.getClass().getMethod("setCustomModelDataComponent", component.getClass()).invoke(meta, component);
            }
        } catch (ReflectiveOperationException ignored) {
            // Runtime is on legacy API; integer CMD above is sufficient.
        }
    }
}
