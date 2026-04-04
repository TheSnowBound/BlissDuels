package me.thesnowbound.blissDuels.gem;

/**
 * Represents a single gem with its type, tier, and energy level
 */
public class Gem {
    private final GemType type;
    private final GemTier tier;
    private final GemEnergy energy;

    public Gem(GemType type, GemTier tier, GemEnergy energy) {
        this.type = type;
        this.tier = tier;
        this.energy = energy;
    }

    public GemType getType() {
        return type;
    }

    public GemTier getTier() {
        return tier;
    }

    public GemEnergy getEnergy() {
        return energy;
    }

    public int getEnergyLevel() {
        return energy.getLevel();
    }

    @Override
    public String toString() {
        return String.format("%s (Tier %d, Energy %d)", type.getIdentifier(), tier.getLevel(), energy.getLevel());
    }
}

