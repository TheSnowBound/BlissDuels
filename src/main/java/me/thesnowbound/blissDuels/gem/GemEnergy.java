package me.thesnowbound.blissDuels.gem;

/**
 * Represents the energy level of a gem (0-10)
 * Each level has a display name for lore
 */
public enum GemEnergy {
    BROKEN(0, "&7Broken", "&f&lᴜsᴇʟᴇss"),
    RUINED(1, "<##FF1111>Ruined", "&f(<##FF1111>Ruined&f)"),
    DAMAGED(2, "<##FFC929>Damaged", "&f(<##FFC929>Damaged&f)"),
    CRACKED(3, "<##7958DB>Cracked", "&f(<##7958DB>Cracked&f)"),
    SCRATCHED(4, "<##57FF8F>Scratched", "&f(<##57FF8F>Scratched&f)"),
    PRISTINE(5, "<##57FFC7>Pristine", "&f(<##57FFC7>Pristine&f)"),
    PRISTINE_PLUS_1(6, "<##57FFC7>Pristine &f+ <##96FFD9>1", "&f(<##57FFC7>Pristine&f + <##96FFD9>1&f)"),
    PRISTINE_PLUS_2(7, "<##57FFC7>Pristine &f+ <##96FFD9>2", "&f(<##57FFC7>Pristine&f + <##96FFD9>2&f)"),
    PRISTINE_PLUS_3(8, "<##57FFC7>Pristine &f+ <##96FFD9>3", "&f(<##57FFC7>Pristine&f + <##96FFD9>3&f)"),
    PRISTINE_PLUS_4(9, "<##57FFC7>Pristine &f+ <##96FFD9>4", "&f(<##57FFC7>Pristine&f + <##96FFD9>4&f)"),
    PRISTINE_PLUS_5(10, "<##57FFC7>Pristine &f+ <##96FFD9>5", "&f(<##57FFC7>Pristine&f + <##96FFD9>5&f)");

    private final int level;
    private final String coloredName;
    private final String loreName;

    GemEnergy(int level, String coloredName, String loreName) {
        this.level = level;
        this.coloredName = coloredName;
        this.loreName = loreName;
    }

    public int getLevel() {
        return level;
    }

    public String getColoredName() {
        return coloredName;
    }

    public String getLoreName() {
        return loreName;
    }

    public static GemEnergy fromLevel(int level) {
        for (GemEnergy energy : values()) {
            if (energy.level == level) {
                return energy;
            }
        }
        return BROKEN;
    }
}

