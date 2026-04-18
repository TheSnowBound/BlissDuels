package me.thesnowbound.blissDuels.systems;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import me.thesnowbound.blissDuels.util.GemUtil;
import me.thesnowbound.blissDuels.gem.GemTier;
import me.thesnowbound.blissDuels.gem.GemType;

/**
 * Manages passive effects that apply based on equipped gem
 */
public class PassiveEffectSystem {
    public PassiveEffectSystem() {
    }

    /**
     * Apply passive effects for the gem the player is holding
     */
    public void applyPassiveEffects(Player player) {
        // Clear previously applied passive potion effects before re-applying.
        clearPassiveEffects(player);

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        // Collect gem info from both hands. If both hands have the same gem type, prefer the higher tier.
        java.util.Map<GemType, GemTier> merged = new java.util.HashMap<>();

        if (GemUtil.isGem(mainHand)) {
            GemType type = GemUtil.getGem(mainHand);
            GemTier tier = GemUtil.getTier(mainHand);
            if (type != null) merged.put(type, tier);
        }

        if (GemUtil.isGem(offHand)) {
            GemType type = GemUtil.getGem(offHand);
            GemTier tier = GemUtil.getTier(offHand);
            if (type != null) {
                merged.merge(type, tier, (existing, incoming) -> incoming.ordinal() > existing.ordinal() ? incoming : existing);
            }
        }

        if (merged.isEmpty()) return;

        applyGemPassivesCombined(player, merged);
    }

    /**
     * Clear any passive potion effects that gems apply.
     */
    public void clearPassiveEffects(Player player) {
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.LUCK);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        // For Flux, we previously removed SLOWNESS/HUNGER/WEAKNESS; don't re-add them here.
    }

    /**
     * Apply passive effects based on merged gem types from both hands.
     * If both hands have the same gem type, the higher tier is used.
     */
    private void applyGemPassivesCombined(Player player, java.util.Map<GemType, GemTier> gems) {
        // FIRE
        if (gems.containsKey(GemType.FIRE)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 120, 0, false, false));
        }

        // SPEED
        if (gems.containsKey(GemType.SPEED)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1, false, false));
        }

        // STRENGTH
        if (gems.containsKey(GemType.STRENGTH)) {
            GemTier tier = gems.get(GemType.STRENGTH);
            int amplifier = tier == GemTier.TIER_2 ? 1 : 0;
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 120, amplifier, false, false));
        }

        // LIFE
        if (gems.containsKey(GemType.LIFE)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 0, false, false));
            if (player.getFoodLevel() < 20) {
                player.setFoodLevel(Math.min(20, player.getFoodLevel() + 1));
            }
        }

        // PUFF — no continuous potion here; handled elsewhere

        // WEALTH
        if (gems.containsKey(GemType.WEALTH)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 120, 1, false, false));
        }

        // FLUX
        if (gems.containsKey(GemType.FLUX)) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.HUNGER);
            player.removePotionEffect(PotionEffectType.WEAKNESS);
        }

        // ASTRA
        if (gems.containsKey(GemType.ASTRA)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 120, 0, false, false));
        }
    }
}
