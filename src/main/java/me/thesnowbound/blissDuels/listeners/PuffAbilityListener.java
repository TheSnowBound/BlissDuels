package me.thesnowbound.blissDuels.listeners;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.gem.GemTier;
import me.thesnowbound.blissDuels.gem.GemType;
import me.thesnowbound.blissDuels.util.AbilityParticleUtil;
import me.thesnowbound.blissDuels.util.ColorUtil;
import me.thesnowbound.blissDuels.util.FormatUtil;
import me.thesnowbound.blissDuels.util.GemUtil;
import org.bukkit.Color;
import org.bukkit.GameEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Puff gem conversion slice based on reference/Gems/puff.sk.
 */
public class PuffAbilityListener implements Listener {
    private static final Particle.DustOptions PUFF_DUST = new Particle.DustOptions(Color.fromRGB(239, 239, 239), 1.0f);

    private final BlissDuels plugin;

    private final Set<UUID> doubleJumping = new HashSet<>();

    public PuffAbilityListener(BlissDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler()
    public void onGameEvent(GenericGameEvent event) {
        if (event.getEvent() != GameEvent.SCULK_SENSOR_TENDRILS_CLICKING) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (hasPuffWithEnergy(player.getInventory().getItemInMainHand(), 2)
            || hasPuffWithEnergy(player.getInventory().getItemInOffHand(), 2)) {
            event.setCancelled(true);
        }
    }

    @EventHandler()
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();

        Player player = event.getPlayer();

        if (action.isRightClick()) {
            tryDash(player);
            return;
        }

        if (action.isLeftClick()) {
            tryGroupBreezyBash(player);
            return;
        }

        // Block trample immunity.
        if (action == Action.PHYSICAL && event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.FARMLAND) {
            if (hasPuffWithEnergy(player.getInventory().getItemInMainHand(), 2)
                || hasPuffWithEnergy(player.getInventory().getItemInOffHand(), 2)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler( priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        // Single target Breezy Bash from melee.
        ItemStack held = attacker.getInventory().getItemInMainHand();
        if (!isPuffTier2WithEnergy(held)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(attacker, "BreezyBash")) {
            return;
        }
        if (isTrusted(attacker, victim)) {
            return;
        }

        plugin.getCooldownManager().setCooldown(attacker, "BreezyBash", breezyCooldownForEnergy(GemUtil.getEnergyLevel(held)));
        AbilityParticleUtil.drawAbilityLine(attacker, victim, org.bukkit.Color.fromRGB(239, 239, 239), true);
        attacker.sendMessage(ColorUtil.color("<#EFEFEF> Breezy Bash used on " + safeName(victim)));
        if (victim instanceof Player victimPlayer) {
            victimPlayer.sendMessage(ColorUtil.color("<#EFEFEF> You were hit by Breezy Bash"));
        }

        victim.setVelocity(victim.getVelocity().setY(1.4));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (victim.isValid() && !victim.isDead()) {
                victim.setVelocity(victim.getVelocity().setY(-1.5));
            }
        }, 19L);
    }

    @EventHandler()
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            double prevented = event.getDamage();
            boolean preventedByOffhand = hasPuffWithEnergy(victim.getInventory().getItemInOffHand(), 2);
            boolean preventedByHeld = hasPuffWithEnergy(victim.getInventory().getItemInMainHand(), 2);

            // Skript increments once per qualifying hand check.
            if (preventedByOffhand) {
                event.setCancelled(true);
            }
            if (preventedByHeld) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler()
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode().name().equals("SURVIVAL")
            && (hasPuffWithEnergy(player.getInventory().getItemInMainHand(), 1)
            || hasPuffWithEnergy(player.getInventory().getItemInOffHand(), 1))
            && plugin.getCooldownManager().isCooldownReady(player, "DoubleJump")) {
            player.setAllowFlight(true);
        }
    }

    @EventHandler()
    public void onFlightToggle(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!player.getGameMode().name().equals("SURVIVAL")) {
            return;
        }

        if (!(hasPuffWithEnergy(player.getInventory().getItemInMainHand(), 1)
            || hasPuffWithEnergy(player.getInventory().getItemInOffHand(), 1))) {
            return;
        }

        if (!plugin.getCooldownManager().isCooldownReady(player, "DoubleJump")) {
            return;
        }

        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);

        Vector vector = player.getLocation().getDirection().normalize();
        vector.setY(1.8);
        player.setVelocity(vector);

        plugin.getCooldownManager().setCooldown(player, "DoubleJump", FormatUtil.toMilliseconds(0, 5));
        doubleJumping.add(player.getUniqueId());
        player.sendMessage(ColorUtil.color("<#EFEFEF> Double Jump used"));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    doubleJumping.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                if (!doubleJumping.contains(player.getUniqueId())) {
                    cancel();
                    return;
                }

                if (player.getLocation().subtract(0, 1, 0).getBlock().getType().isAir()) {
                    player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 2, 0.0, 0.0, 0.0, 0.01);
                } else {
                    doubleJumping.remove(player.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }

    private void tryDash(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isPuffTier2WithEnergy(held)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(player, "Dash")) {
            return;
        }

        int energy = GemUtil.getEnergyLevel(held);
        plugin.getCooldownManager().setCooldown(player, "Dash", dashCooldownForEnergy(energy));

        Vector vector = player.getEyeLocation().getDirection().multiply(3.5);
        if (player.getLocation().getPitch() <= -15) {
            vector.setY(2.85);
        } else if (player.getLocation().subtract(0, 1, 0).getBlock().getType().isSolid()) {
            vector.setY(0.35);
        }

        player.setVelocity(vector);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
        player.sendMessage(ColorUtil.color("<#EFEFEF> Dash activated"));

        // Dash collision ticks.
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= 40) {
                    cancel();
                    return;
                }

                for (Entity entity : player.getNearbyEntities(2.5, 2.5, 2.5)) {
                    if (!(entity instanceof LivingEntity living) || living.equals(player)) {
                        continue;
                    }
                    if (isTrusted(player, living)) {
                        continue;
                    }
                    living.damage(2.5, player);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void tryGroupBreezyBash(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isPuffTier2WithEnergy(held)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(player, "BreezyBash")) {
            return;
        }

        int energy = GemUtil.getEnergyLevel(held);
        plugin.getCooldownManager().setCooldown(player, "BreezyBash", breezyCooldownForEnergy(energy));

        double radius = switch (energy) {
            case 1 -> 1.5;
            case 2, 3 -> 2.5;
            case 4 -> 3.5;
            default -> 4.0;
        };

        player.sendMessage(ColorUtil.color("<#EFEFEF> Group Breezy Bash activated"));

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living.equals(player)) {
                continue;
            }
            if (isTrusted(player, living)) {
                continue;
            }

            Vector away = living.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(3.0);
            away.setY(1.0);
            living.setVelocity(away);
        }

        drawPuffRing(player.getLocation(), radius);
    }

    private void drawPuffRing(Location center, double radius) {
        for (int i = 0; i < 72; i++) {
            double angle = Math.toRadians(i * 5);
            Location point = center.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
            point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0.01, PUFF_DUST);
            point.getWorld().spawnParticle(Particle.SMOKE, point, 1, 0, 0, 0, 0.01);
        }
    }

    private long dashCooldownForEnergy(int energy) {
        return switch (energy) {
            case 1 -> FormatUtil.toMilliseconds(1, 5);
            case 2 -> FormatUtil.toMilliseconds(1, 0);
            case 3 -> FormatUtil.toMilliseconds(0, 55);
            case 4 -> FormatUtil.toMilliseconds(0, 50);
            default -> FormatUtil.toMilliseconds(0, 45);
        };
    }

    private long breezyCooldownForEnergy(int energy) {
        return switch (energy) {
            case 1 -> FormatUtil.toMilliseconds(1, 35);
            case 2 -> FormatUtil.toMilliseconds(1, 30);
            case 3 -> FormatUtil.toMilliseconds(1, 25);
            case 4 -> FormatUtil.toMilliseconds(1, 20);
            default -> FormatUtil.toMilliseconds(1, 15);
        };
    }

    private boolean hasPuffWithEnergy(ItemStack item, int minEnergyExclusive) {
        return isPuffGem(item) && GemUtil.getEnergyLevel(item) > minEnergyExclusive - 1;
    }

    private boolean isPuffTier2WithEnergy(ItemStack item) {
        return isPuffGem(item) && GemUtil.getTier(item) == GemTier.TIER_2 && GemUtil.getEnergyLevel(item) > 0;
    }

    private boolean isPuffGem(ItemStack item) {
        return GemUtil.isGem(item) && GemUtil.getGem(item) == GemType.PUFF;
    }

    private String safeName(LivingEntity entity) {
        if (entity instanceof Player player) {
            return player.isInvisible() ? "???" : player.getName();
        }
        return entity.getType().name().toLowerCase();
    }

    private boolean isTrusted(Player source, LivingEntity target) {
        return plugin.getTrustManager().isTrusted(source, target);
    }
}
