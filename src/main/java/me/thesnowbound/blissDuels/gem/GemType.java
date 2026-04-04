package me.thesnowbound.blissDuels.gem;

public enum GemType {
    FIRE("<##FE8120>&lғɪʀᴇ", "fire", 2),
    FLUX("<##5ED7FF>&lғʟᴜx", "flux", 16),
    STRENGTH("<##F10103>&lsᴛʀᴇɴɢᴛʜ", "strength", 10),
    LIFE("<##FE04B4>&lʟɪғᴇ", "life", 4),
    SPEED("<##FEFD17>&lsᴘᴇᴇᴅ", "speed", 8),
    PUFF("<##EFEFEF>&lᴘᴜғғ", "puff", 6),
    WEALTH("<##0EC912>&lᴡᴇᴀʟᴛʜ", "wealth", 12),
    ASTRA("<##A01FFF>&lᴀsᴛʀᴀ", "astra", 14);

    private final String displayName;
    private final String identifier;
    private final int customModelData;

    GemType(String displayName, String identifier, int customModelData) {
        this.displayName = displayName;
        this.identifier = identifier;
        this.customModelData = customModelData;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIdentifier() {
        return identifier;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public int getTier2CustomModelData() {
        return customModelData;
    }

    public int getTier1CustomModelData() {
        return customModelData - 1;
    }

    public static GemType fromIdentifier(String id) {
        for (GemType gem : values()) {
            if (gem.identifier.equalsIgnoreCase(id)) {
                return gem;
            }
        }
        return null;
    }
}

