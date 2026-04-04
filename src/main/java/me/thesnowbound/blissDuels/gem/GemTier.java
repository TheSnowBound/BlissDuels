package me.thesnowbound.blissDuels.gem;

/**
 * Represents the tier of a gem
 * Tier 1: Amethyst Shard (lower)
 * Tier 2: Prismarine Shard (higher)
 */
public enum GemTier {
    TIER_1(1),
    TIER_2(2);

    private final int level;

    GemTier(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public static GemTier fromLevel(int level) {
        for (GemTier tier : values()) {
            if (tier.level == level) {
                return tier;
            }
        }
        return TIER_1;
    }
}

