package me.thesnowbound.blissDuels.systems;

import me.thesnowbound.blissDuels.gem.GemEnergy;
import me.thesnowbound.blissDuels.gem.GemTier;
import me.thesnowbound.blissDuels.gem.GemType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Centralized gem lore builder so all generated gems follow one source of truth.
 */
public class GemLoreSystem {

    public List<String> buildLore(GemType type, GemTier tier, GemEnergy energy) {
        List<String> lore = new ArrayList<>();
        lore.add(getDescription(type));
        lore.add(energy.getLoreName());
        lore.add("&f");

        String tierIcon = tier == GemTier.TIER_2 ? "🔮" : "🔺";

        lore.add(type.getDisplayName() + tierIcon + " <##FFE4AB>ᴘᴀssɪᴠᴇs");
        lore.addAll(getPassives(type));
        lore.add("&f");

        lore.add(type.getDisplayName() + tierIcon + " <##82F3FF>&lᴀʙɪʟɪᴛʏ");
        lore.add(getAbility(type));
        lore.add("&f");

        lore.add(type.getDisplayName() + "🔮 <##B8FFFB>&lᴘᴏᴡᴇʀs");
        lore.addAll(getPowers(type, tier));

        return lore;
    }

    private String getDescription(GemType type) {
        return switch (type) {
            case FIRE -> "&f&lᴍᴀɴɪᴘᴜʟᴀᴛᴇ ғɪʀᴇ";
            case FLUX -> "&f&lᴇᴠᴇʀʏᴛʜɪɴɢ ɪs ᴀ ғʟᴜᴄᴛᴜᴀᴛɪᴏɴ";
            case STRENGTH -> "&f&lʜᴀᴠᴇ ᴛʜᴇ sᴛʀᴇɴɢᴛʜ ᴏғ ᴀ ᴀʀᴍʏ";
            case LIFE -> "&f&lᴄᴏɴᴛʀᴏʟ ᴛʜᴇ ʙᴀʟᴀɴᴄᴇ ᴏғ ʟɪғᴇ";
            case SPEED -> "&f&lʙᴇᴄᴏᴍᴇ ᴀ ʙʟᴜʀ";
            case PUFF -> "&f&lʙᴇ ᴛʜᴇ ʙɪɢɢᴇsᴛ ʙɪʀᴅ";
            case WEALTH -> "&f&lғᴜᴇʟ ᴀ ᴇᴍᴘɪʀᴇ";
            case ASTRA -> "&f&lᴍᴀɴᴀɢᴇ ᴛʜᴇ ᴛɪᴅᴇs ᴏғ ᴛʜᴇ ᴄᴏsᴍᴏs";
        };
    }

    private List<String> getPassives(GemType type) {
        return switch (type) {
            case FIRE -> Arrays.asList("&7- Fire Resistance", "&7- Autosmelt", "&7- Flamestrike", "&7- Fireshot");
            case FLUX -> Arrays.asList("&7- Flow State", "&7- Shocking Chance", "&7- Tireless", "&7- Conduction", "&7- Charged");
            case STRENGTH -> Arrays.asList("&7- Strength", "&7- Enchants Sharpness");
            case LIFE -> Arrays.asList("&7- Green Thumb", "&7- Radiant Fist", "&7- Bonus Saturation", "&7- Bonus Absorption", "&7- Wither Immune");
            case SPEED -> Arrays.asList("&7- Speed", "&7- Dolphins grace", "&7- Enchants Efficiency", "&7- Enchants Soul Speed");
            case PUFF -> Arrays.asList("&7- Fall Damage immunity", "&7- Enchants Power", "&7- Enchants Punch", "&7- Sculk Silence", "&7- Crop Tramp-Less");
            case WEALTH -> Arrays.asList("&7- Hero of the Village", "&7- Luck", "&7- Enchants Mending", "&7- Enchants Fortune", "&7- Enchants Looting", "&7- Bonus Ores", "&7- Extra EXP", "&7- Durability Chip", "&7- Double Debris");
            case ASTRA -> Arrays.asList("&7- Phasing", "&7- Soul Healing", "&7- Soul Capture");
        };
    }

    private String getAbility(GemType type) {
        return switch (type) {
            case FIRE -> "&7- <##FE8120>Crisp";
            case FLUX -> "&7- <##03EAFF>Kinetic Burst";
            case STRENGTH -> "&7- <##F10303>Bounty Hunter";
            case LIFE -> "&7- <##FE04B4>Vitalaty Vortex";
            case SPEED -> "&7- <##FEFD17> Thunder Step";
            case PUFF -> "&7- &fDouble Jump";
            case WEALTH -> "&7- <##0EC912>Pockets";
            case ASTRA -> "&7- <##A01FFF>Dimensional Drift";
        };
    }

    private List<String> getPowers(GemType type, GemTier tier) {
        if (tier == GemTier.TIER_1) {
            return List.of("&f&lUnknown");
        }

        return switch (type) {
            case FIRE -> Arrays.asList("&7-&f🧨 <##FF5F33>ғɪʀᴇʙᴀʟʟ &4🧑🏻", "&7-&f🧨<##FF5F33> ᴍᴇᴛᴇᴏʀ ꜱʜᴏᴡᴇʀ &4🤼", "&f", "&7-&f🥾<##248FD1> ᴄᴏᴢʏ Cᴀᴍᴘғɪʀᴇ");
            case FLUX -> Arrays.asList("&7- ☄ <##03EAFF>ᴇɴᴇʀɢʏ ʙᴇᴀᴍ &4🧑🏻", "&7- ☄ <##03EAFF>ɢʀᴏᴜɴᴅ &7 &4🤼", "&f", "&7- 🌀 <##03EAFF>Kinetic Overdrive &a🤼");
            case STRENGTH -> Arrays.asList("&7- &f🤺 <##B5B5B5>ғʀᴀɪʟᴇʀ &4🧑🏻", "&7- &f🤺 <##B5B5B5>ɴᴜʟʟɪғʏ &4🤼", "&f", "&7- &f⚔<##910D0D> ᴄʜᴀᴅ sᴛʀᴇɴɢᴛʜ &a🧑🏻", "&7- &f⚔<##910D0D> ᴄʜᴀᴅ sᴛʀᴇɴɢᴛʜ &a🤼");
            case LIFE -> Arrays.asList("&7- &f💘<##FF429A> ʜᴇᴀʀᴛ ᴅʀᴀɪɴᴇʀ &4🧑🏻", "&7- &f💘<##FF429A> ʜᴇᴀʀᴛʟᴏᴄᴋ &4🤼", "&f", "&7- &f💖<##B8FFFA> ᴄɪʀᴄʟᴇ ᴏғ ʟɪғᴇ &a🧑🏻", "&7- &f💖<##B8FFFA> ᴄɪʀᴄʟᴇ ᴏғ ʟɪғᴇ &a🤼");
            case SPEED -> Arrays.asList("&7- &f🎯 <##FFE86E>ʙʟᴜʀ", "&f", "&7- 🌩 <##61FFEA>sᴘᴇᴇᴅʏ sᴛᴏʀᴍ &4🧑🏻", "&7- 🌩 <##61FFEA>sᴘᴇᴇᴅʏ sᴛᴏʀᴍ &4🤼");
            case PUFF -> Arrays.asList("&7- &f☁ ʙʀᴇᴇᴢʏ ʙᴀsʜ &4🧑🏻", "&7- &f☁ ʙʀᴇᴇᴢʏ ʙᴀsʜ &4🤼", "&f", "&7- &f⏫ ᴅᴀsʜ");
            case WEALTH -> Arrays.asList("&7-&f🍀 &cᴜɴғᴏʀᴛᴜɴᴇ &4🧑🏻", "&7-&f🍀 &cɪᴛᴇᴍ ʟᴏᴄᴋ &4🤼", "&f", "&7-&f💸 <##FFC642>ʀɪᴄʜ ʀᴜsʜ &a🧑🏻", "&7-&f💸 <##FFC642>ᴀᴍʟɪꜰɪᴄᴀᴛɪᴏɴ &a🤼");
            case ASTRA -> Arrays.asList("&7- &f🔪 <##BFB8B8>ᴅᴀɢɢᴇʀs &4🧑🏻", "&7- &f🔪 &7ᴜɴʙᴏᴜɴᴅᴇᴅ &4🤼", "&f", "&7- &f👻 <##AABBBF>ᴀsᴛʀᴀʟ ᴘʀᴏᴊᴇᴄᴛɪᴏɴ &a🧑🏻", "&7- &f👻 <##AABBBF>ᴀsᴛʀᴀʟ ᴠᴏɪᴅ &a🤼");
        };
    }
}

