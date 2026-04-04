package me.thesnowbound.blissDuels.systems;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.gem.GemTier;
import me.thesnowbound.blissDuels.gem.GemType;
import me.thesnowbound.blissDuels.listeners.FluxAbilityListener;
import me.thesnowbound.blissDuels.managers.CooldownManager;
import me.thesnowbound.blissDuels.util.ColorUtil;
import me.thesnowbound.blissDuels.util.GemUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Skript-style action bar cooldown HUD for held/offhand gem powers.
 */
public class ActionBarUpdater {
    private final BlissDuels plugin;
    private BukkitTask task;

    public ActionBarUpdater(BlissDuels plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack gemItem = getDisplayGem(player);
            if (gemItem == null) {
                player.sendActionBar(" ");
                continue;
            }

            GemType type = GemUtil.getGem(gemItem);
            GemTier tier = GemUtil.getTier(gemItem);
            int energy = GemUtil.getEnergyLevel(gemItem);
            if (type == null || tier == null) {
                player.sendActionBar(" ");
                continue;
            }

            if (energy <= 0) {
                player.sendActionBar(ColorUtil.color("&cBROKEN"));
                continue;
            }

            String bar = buildGemBar(player, type, tier);
            player.sendActionBar(ColorUtil.color(bar));
        }
    }

    private String buildGemBar(Player player, GemType type, GemTier tier) {
        return switch (type) {
            case FIRE -> fireBar(player, tier);
            case LIFE -> lifeBar(player, tier);
            case STRENGTH -> strengthBar(player, tier);
            case ASTRA -> astraBar(player, tier);
            case PUFF -> puffBar(player, tier);
            case FLUX -> fluxBar(player, tier);
            case SPEED -> speedBar(player, tier);
            case WEALTH -> wealthBar(player, tier);
        };
    }

    private String fireBar(Player player, GemTier tier) {
        String crisp = cooldownDisplay(player, "Crisp");
        if (tier == GemTier.TIER_2) {
            return "&f🧨 " + cooldownDisplay(player, "Fireball") + "  <##FE8120>🔮 " + crisp + "  &f🥾 " + cooldownDisplay(player, "Campfire");
        }
        return "<##FE8120>🔺 " + crisp;
    }

    private String lifeBar(Player player, GemTier tier) {
        String vitality = cooldownDisplayAny(player, "Vitality", "VitalityVortex");
        if (tier == GemTier.TIER_2) {
            return "&f💘 " + cooldownDisplay(player, "HeartLock") + "  <##FE04B4>🔮 " + vitality + "  &f💖 " + cooldownDisplay(player, "LifeCircle");
        }
        return "<##FE04B4>🔺 " + vitality;
    }

    private String strengthBar(Player player, GemTier tier) {
        String bounty = cooldownDisplay(player, "Bounty");
        if (tier == GemTier.TIER_2) {
            return "&f🤺 " + cooldownDisplay(player, "Frailer") + "  <##F10303>🔮 " + bounty + "  &f⚔ " + cooldownDisplay(player, "ChadStrength");
        }
        return "<##F10303>🔺 " + bounty;
    }

    private String astraBar(Player player, GemTier tier) {
        String drift = cooldownDisplay(player, "Drift");
        if (tier == GemTier.TIER_2) {
            return "&f🔪 " + cooldownDisplay(player, "Daggers") + "  <##A01FFF>🔮 " + drift + "  &f👻 " + cooldownDisplay(player, "Astral");
        }
        return "<##A01FFF>🔺 " + drift;
    }

    private String puffBar(Player player, GemTier tier) {
        String jump = cooldownDisplay(player, "DoubleJump");
        if (tier == GemTier.TIER_2) {
            return "&f☁ " + cooldownDisplay(player, "BreezyBash") + "  &f🔮 " + jump + "  &f⏫ " + cooldownDisplay(player, "Dash");
        }
        return "&f🔺 " + jump;
    }

    private String fluxBar(Player player, GemTier tier) {
        String burstDisplay;
        if (plugin.getCooldownManager().isCooldownReady(player, "KineticBurst")) {
            double staticCharge = plugin.getFluxParityListener() == null ? 0.0 : plugin.getFluxParityListener().getStaticBurstCharge(player);
            burstDisplay = "&b" + formatOneDecimal(staticCharge);
        } else {
            burstDisplay = cooldownDisplay(player, "KineticBurst");
        }

        if (tier == GemTier.TIER_2) {
            FluxAbilityListener flux = plugin.getFluxAbilityListener();
            double beamCharge = flux == null ? 0.0 : flux.getKinetic(player);
            return "&f☄ " + cooldownDisplay(player, "EnergyBeam")
                + " <##5ED7FF>🔮 " + burstDisplay
                + " <##5ED7FF>🔮 &b" + formatOneDecimal(beamCharge)
                + " &f🌀 " + cooldownDisplay(player, "KineticOverdrive");
        }

        return "<##5ED7FF>🔺 " + burstDisplay;
    }

    private String speedBar(Player player, GemTier tier) {
        String thunder = cooldownDisplay(player, "Thunder");
        if (tier == GemTier.TIER_2) {
            return "&f🎯 " + cooldownDisplay(player, "Blur") + "  <##E5EE2C>🔮 " + thunder + "  &f🌩 " + cooldownDisplay(player, "SpeedStorm");
        }
        return "<##E5EE2C>🔺 " + thunder;
    }

    private String wealthBar(Player player, GemTier tier) {
        if (tier == GemTier.TIER_2) {
            return "&f🍀 " + cooldownDisplay(player, "Unfortunate") + " <##0EC912>🔮 &f💸 " + cooldownDisplay(player, "RichRush");
        }
        return "<##0EC912>🔺";
    }

    private String cooldownDisplay(Player player, String key) {
        CooldownManager cooldowns = plugin.getCooldownManager();
        long remaining = cooldowns.getRemaining(player, key);
        if (remaining <= 0) {
            return "&aReady!";
        }

        long totalSeconds = Math.max(1, (long) Math.ceil(remaining / 1000.0));
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        if (minutes > 0) {
            if (seconds <= 0) {
                return "&b" + minutes + "m";
            }
            return "&b" + minutes + "m " + seconds + "s";
        }
        return "&b" + seconds + "s";
    }

    private String cooldownDisplayAny(Player player, String... keys) {
        CooldownManager cooldowns = plugin.getCooldownManager();
        long best = Long.MAX_VALUE;
        for (String key : keys) {
            long remaining = cooldowns.getRemaining(player, key);
            if (remaining < best) {
                best = remaining;
            }
        }
        return best == Long.MAX_VALUE ? "&aReady!" : (best <= 0 ? "&aReady!" : cooldownDisplayFromMillis(best));
    }

    private String cooldownDisplayFromMillis(long remaining) {
        long totalSeconds = Math.max(1, (long) Math.ceil(remaining / 1000.0));
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes > 0) {
            if (seconds <= 0) {
                return "&b" + minutes + "m";
            }
            return "&b" + minutes + "m " + seconds + "s";
        }
        return "&b" + seconds + "s";
    }

    private String formatOneDecimal(double value) {
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private ItemStack getDisplayGem(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (GemUtil.isGem(held)) {
            return held;
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (GemUtil.isGem(offhand)) {
            return offhand;
        }
        return null;
    }
}
