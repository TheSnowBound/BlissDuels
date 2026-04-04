package me.thesnowbound.blissDuels.commands;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.gem.GemType;
import me.thesnowbound.blissDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /dbg <power> <player>
 * power: all or an ability name (e.g. thunder, speedstorm, blur, campfire, fireball, frailer)
 */
public class AbilityDebugCommand implements CommandExecutor, TabCompleter {
    private final BlissDuels plugin;

    private static final Map<String, GemType> POWER_TO_GEM = new HashMap<>();
    private static final List<String> POWER_SUGGESTIONS = List.of(
        "all",
        "thunder", "speedstorm", "blur",
        "heartlock", "lifecircle", "vitalityvortex", "heartdrainer", "radiantfist",
        "campfire", "fireball", "meteorshower",
        "kineticburst", "energybeam", "kineticoverdrive",
        "dash", "breezybash", "doublejump",
        "unfortunate", "richrush", "amplification", "pockets",
        "frailer", "chadstrength", "bountyhunter",
        "astral", "drift", "daggers", "unbounded"
    );

    static {
        registerPower(GemType.SPEED, "speed", "thunder", "thunderstep", "speedstorm", "blur");
        registerPower(GemType.LIFE, "life", "heartlock", "lifecircle", "vitalityvortex", "heartdrainer", "radiantfist");
        registerPower(GemType.FIRE, "fire", "campfire", "cozycampfire", "fireball", "meteorshower");
        registerPower(GemType.FLUX, "flux", "kineticburst", "energybeam", "kineticoverdrive", "beam");
        registerPower(GemType.PUFF, "puff", "dash", "breezybash", "doublejump");
        registerPower(GemType.WEALTH, "wealth", "unfortunate", "richrush", "amplification", "pockets", "itemlock");
        registerPower(GemType.STRENGTH, "strength", "frailer", "chadstrength", "bountyhunter", "strengthistrength");
        registerPower(GemType.ASTRA, "astra", "astral", "drift", "daggers", "unbounded", "astralvoid", "astralprojection");
    }

    public AbilityDebugCommand(BlissDuels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /dbg <power> <player>");
            return true;
        }

        String rawPower = args[0];
        String power = normalize(rawPower);
        int energy = 5;
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("Player not found.");
            return true;
        }

        plugin.getCooldownManager().resetAll(target);

        if ("all".equals(power)) {
            for (GemType gemType : GemType.values()) {
                target.getInventory().addItem(plugin.getGemItemManager().getGem(gemType, 1, energy));
                target.getInventory().addItem(plugin.getGemItemManager().getGem(gemType, 2, energy));
            }
            target.getInventory().addItem(plugin.getGemItemManager().createTraderItem());
            target.getInventory().addItem(plugin.getGemItemManager().createUpgraderItem());
            target.sendMessage(ColorUtil.color("<##FFD773>DBG: all powers loaded at pristine 5. Cooldowns reset."));
            sender.sendMessage("DBG gave ALL powers to " + target.getName() + " at pristine 5.");
            return true;
        }

        GemType gemType = POWER_TO_GEM.get(power);
        if (gemType == null) {
            sender.sendMessage("Unknown power. Example: thunder, blur, campfire, fireball, heartlock, richrush, daggers, frailer, kineticburst");
            return true;
        }

        target.getInventory().addItem(plugin.getGemItemManager().getGem(gemType, 1, energy));
        target.getInventory().addItem(plugin.getGemItemManager().getGem(gemType, 2, energy));

        target.sendMessage(ColorUtil.color("<##FFD773>DBG: " + rawPower + " loaded at pristine 5. Cooldowns reset."));
        sender.sendMessage("DBG gave " + rawPower + " kit (" + gemType.getIdentifier() + ") to " + target.getName() + " at pristine 5.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(POWER_SUGGESTIONS);
            return out;
        }
        if (args.length == 2) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                out.add(online.getName());
            }
            return out;
        }
        return out;
    }

    private static void registerPower(GemType type, String... aliases) {
        for (String alias : aliases) {
            POWER_TO_GEM.put(normalize(alias), type);
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }
}
