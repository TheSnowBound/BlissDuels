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
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        // Check main hand
        if (GemUtil.isGem(mainHand)) {
            applyGemPassives(player, GemUtil.getGem(mainHand), GemUtil.getTier(mainHand));
        }
        // Check off hand
        else if (GemUtil.isGem(offHand)) {
            applyGemPassives(player, GemUtil.getGem(offHand), GemUtil.getTier(offHand));
        }
    }

    /**
     * Apply passive effects based on gem type
     */
    private void applyGemPassives(Player player, GemType gemType, GemTier tier) {
        if (gemType == null) return;

        switch (gemType) {
            case FIRE:
                // Fire Resistance, Saturation
                player.addPotionEffect(
                    new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 120, 0, false, false)
                );
                break;

            case SPEED:
                // Speed, Dolphin's Grace
                player.addPotionEffect(
                    new PotionEffect(PotionEffectType.SPEED, 120, 1, false, false)
                );
                break;

            case STRENGTH:
                int amplifier = tier == GemTier.TIER_2 ? 1 : 0;
                player.addPotionEffect(
                    new PotionEffect(PotionEffectType.STRENGTH, 120, amplifier, false, false)
                );
                break;

            case LIFE:
                // Regeneration, Extra Saturation
                player.addPotionEffect(
                    new PotionEffect(PotionEffectType.REGENERATION, 120, 0, false, false)
                );
                // Bonus saturation
                if (player.getFoodLevel() < 20) {
                    player.setFoodLevel(Math.min(20, player.getFoodLevel() + 1));
                }
                break;

            case PUFF:
                // Puff has no continuous potion passive; fall safety is handled by damage listener logic.
                break;

            case WEALTH:
                // Luck
                player.addPotionEffect(
                    new PotionEffect(PotionEffectType.LUCK, 120, 1, false, false)
                );
                break;

            case FLUX:
                // Conduit Power (Flow State)
                player.removePotionEffect(
                    PotionEffectType.SLOWNESS
                );
                player.removePotionEffect(
                        PotionEffectType.HUNGER
                );
                player.removePotionEffect(
                        PotionEffectType.WEAKNESS
                );
                break;

            case ASTRA:
                // Night Vision
                player.addPotionEffect(
                    new PotionEffect(PotionEffectType.NIGHT_VISION, 120, 0, false, false)
                );
                break;
        }
    }
}
