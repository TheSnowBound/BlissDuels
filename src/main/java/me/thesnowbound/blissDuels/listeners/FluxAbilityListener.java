package me.thesnowbound.blissDuels.listeners;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.gem.GemTier;
import me.thesnowbound.blissDuels.gem.GemType;
import me.thesnowbound.blissDuels.util.ColorUtil;
import me.thesnowbound.blissDuels.util.FormatUtil;
import me.thesnowbound.blissDuels.util.GemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Flux ability controller. Beam particles are delegated to {@link FluxBeamParticles}
 * so scaling behavior mirrors Skript beam function groups.
 */
public class FluxAbilityListener implements Listener {
    private static final Particle.DustOptions FLUX_DUST = new Particle.DustOptions(Color.fromRGB(94, 215, 255), 1.0f);
    private static final double MAX_KINETIC = 100.0;

    private final BlissDuels plugin;
    private final FluxBeamParticles beamParticles;
    private final Map<UUID, Double> fluxKinetic = new HashMap<>();
    private final Map<UUID, Long> kineticBurstDoubleClick = new HashMap<>();
    private final Set<UUID> fluxBeamActive = new HashSet<>();

    public FluxAbilityListener(BlissDuels plugin) {
        this.plugin = plugin;
        this.beamParticles = new FluxBeamParticles(plugin, this::isTrusted);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();


        Player player = event.getPlayer();
        if (isDisabled(player)) {
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            // Kinetic Burst remains a block-click exception.
            tryKineticBurst(player);
            return;
        }

        if (action.isLeftClick()) {
            tryEnergyBeamRelease(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        fluxKinetic.remove(uuid);
        kineticBurstDoubleClick.remove(uuid);
        fluxBeamActive.remove(uuid);
    }

    private void tryKineticBurst(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack held = player.getInventory().getItemInMainHand();

        if (!isFluxGemWithEnergy(offhand)) {
            return;
        }
        if (!isSwordAxeOrAir(held)) {
            return;
        }

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long expiresAt = kineticBurstDoubleClick.getOrDefault(id, 0L);

        // Skript behavior: right-click double tap to fire Kinetic Burst.
        if (now > expiresAt) {
            kineticBurstDoubleClick.put(id, now + 700L);
            return;
        }
        kineticBurstDoubleClick.remove(id);

        if (!plugin.getCooldownManager().isCooldownReady(player, "KineticBurst")) {
            return;
        }

        // Kinetic Burst is knockback utility; push strength scales with static-burst charge.
        double staticCharge = 0.0;
        if (plugin.getFluxParityListener() != null) {
            staticCharge = plugin.getFluxParityListener().getStaticBurstCharge(player);
        }
        double pushStrength = 2.0 + Math.min(3.5, staticCharge * 0.08);

        for (Entity nearby : player.getNearbyEntities(5.0, 5.0, 5.0)) {
            if (!(nearby instanceof LivingEntity living) || living.equals(player)) {
                continue;
            }
            if (isTrusted(player, living)) {
                continue;
            }

            Vector direction = living.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(pushStrength);
            living.setVelocity(direction);
        }

        for (int i = 0; i < 10; i++) {
            int step = i;
            runLater(i, () -> {
                double radius = 1.0 + (step * 0.5);
                Location center = player.getLocation();
                for (int angle = 0; angle < 360; angle += 10) {
                    double rad = Math.toRadians(angle);
                    Location point = center.clone().add(Math.cos(rad) * radius, 0.15, Math.sin(rad) * radius);
                    player.getWorld().spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, FLUX_DUST);
                }
            });
        }

        player.sendMessage(ColorUtil.color("<##5ED7FF> Kinetic Burst activated &7(static "
            + String.format(java.util.Locale.US, "%.1f", staticCharge) + ")"));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.4f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1.1, 0), 16, 0.4, 0.5, 0.4, 0.001, FLUX_DUST);

        plugin.getCooldownManager().setCooldown(player, "KineticBurst", FormatUtil.toMilliseconds(0, 35));
    }

    private void tryEnergyBeamRelease(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isFluxTier2WithEnergy(held)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(player, "EnergyBeam")) {
            return;
        }

        UUID id = player.getUniqueId();
        double kinetic = fluxKinetic.getOrDefault(id, 0.0);

        fluxKinetic.put(id, 0.0);
        plugin.getCooldownManager().setCooldown(player, "EnergyBeam", energyBeamCooldown(player));

        Location pl2 = player.getEyeLocation().clone().add(player.getLocation().getDirection().normalize().multiply(1.0));
        Location pl3 = player.getEyeLocation().clone().add(player.getLocation().getDirection().normalize().multiply(1.7));
        Location plFar = player.getEyeLocation().clone().add(player.getLocation().getDirection().normalize().multiply(3.0));
        Set<UUID> dontDmgAgain = new HashSet<>();

        // Skript waits 1 tick before evaluating any kinetic branch.
        Bukkit.getScheduler().runTaskLater(plugin, () -> executeKineticRelease(player, kinetic, pl2, pl3, plFar, dontDmgAgain), 1L);
    }

    private void executeKineticRelease(Player player, double kinetic, Location pl2, Location pl3, Location plFar, Set<UUID> dontDmgAgain) {
        if (!player.isOnline()) {
            return;
        }

        Vector beamDir = player.getLocation().getDirection().normalize();
        Location beam1 = player.getEyeLocation().clone().add(beamDir.clone().multiply(1.0));
        Location beam13 = player.getEyeLocation().clone().add(beamDir.clone().multiply(1.3));
        Location beam2 = player.getEyeLocation().clone().add(beamDir.clone().multiply(2.0));
        Location beam3 = player.getEyeLocation().clone().add(beamDir.clone().multiply(3.0));

        if (kinetic <= 0.01) {
            // Skript parity: 0% branch spawns from 1.6 above player, not 1m forward.
            Location zeroBeamOrigin = player.getLocation().clone().add(0.0, 1.6, 0.0);
            playFluxSound(player, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.2f, 1.0f);
            playFluxSound(player, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.0f);
            playFluxSound(player, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 2.0f);
            playFluxSound(player, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.2f, 1.0f);
            playFluxSound(player, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.2f, 1.0f);
            playFluxSound(player, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.8f);
            beamParticles.energyboom651(zeroBeamOrigin, player, kinetic, dontDmgAgain);
            scheduleRepeat(6, 3L, tick -> beamParticles.energyboom6544844(zeroBeamOrigin));
            return;
        }

        if (kinetic > 0.01 && kinetic < 5.0) {
            playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f);
            runLater(14L, () -> {
                playPrimaryReleaseStack(player);
                beamParticles.energyboom659(beam1, player, kinetic, dontDmgAgain);
                scheduleRepeat(6, 3L, tick -> beamParticles.energyboom654444(beam1));
            });
            return;
        }

        if (Math.abs(kinetic - 5.0) < 0.001) {
            playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f);
            runLater(14L, () -> {
                playPrimaryReleaseStack(player);
                beamParticles.energyboom65444(beam1, player, kinetic, dontDmgAgain);
                scheduleRepeat(6, 3L, tick -> beamParticles.energyboom654444(beam1));
            });
            return;
        }

        if (kinetic > 5.0 && kinetic < 11.0) {
            playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f);
            runLater(14L, () -> {
                playPrimaryReleaseStack(player);
                beamParticles.energyboom654(beam1, player, kinetic, dontDmgAgain);
                scheduleRepeat(6, 3L, tick -> beamParticles.energyboom6544(beam1));
            });
            return;
        }

        if (kinetic >= 11.0 && kinetic < 20.0) {
            playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f);
            runLater(14L, () -> {
                playPrimaryReleaseStack(player);
                beamParticles.energyboom607(beam1, player, kinetic, dontDmgAgain);
                scheduleRepeat(5, 5L, tick -> beamParticles.energyboom6077(beam1));
            });
            return;
        }

        if (kinetic >= 20.0 && kinetic < 41.0) {
            playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f);
            runLater(14L, () -> {
                playPrimaryReleaseStack(player);
                beamParticles.energyboom609999(beam1, player, kinetic, dontDmgAgain);
                scheduleRepeat(8, 3L, tick -> beamParticles.energyboom60999(beam1));
            });
            return;
        }

        if (kinetic >= 41.0 && kinetic < 56.0) {
            playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f);
            runLater(5L, () -> playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f));
            runLater(19L, () -> {
                playPrimaryReleaseStack(player);
                beamParticles.energyboom609(beam1, player, kinetic, dontDmgAgain);
                scheduleRepeat(8, 3L, tick -> beamParticles.energyboom6099(beam1));
            });
            return;
        }

        if (kinetic >= 56.0 && kinetic < 65.0) {
            startBeamWindow(player, 80L);
            playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f);
            runLater(5L, () -> playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f));
            runLater(19L, () -> {
                playPrimaryReleaseStack(player);
                beamParticles.energyboom314dmg(beam1, player, kinetic, dontDmgAgain);
                beamParticles.energyboom314(beam1, player, kinetic, dontDmgAgain);
                beamParticles.energyboom315(beam1);
                beamParticles.energyboom316(beam1, player);
                beamParticles.energyboom1002(beam1, player, beamActive(player));
            });
            runLater(35L, () -> {
                beamParticles.energyboom314(beam1, player, kinetic, dontDmgAgain);
                beamParticles.energyboom315(beam1);
                beamParticles.energyboom317(beam1, player);
                beamParticles.energyboom1002(beam1, player, beamActive(player));
            });
            runLater(53L, () -> playFluxSound(player, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 1.6f));
            runLater(67L, () -> {
                beamParticles.energyboom314(beam1, player, kinetic, dontDmgAgain);
                beamParticles.energyboom315(beam1);
                beamParticles.energyboom318(beam1, player);
                beamParticles.energyboom1002(beam1, player, beamActive(player));
            });
            return;
        }

        if (kinetic >= 65.0 && kinetic < 76.0) {
            startBeamWindow(player, 90L);
            playFluxSound(player, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.0f);
            playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f);
            runLater(4L, () -> playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f));
            runLater(8L, () -> playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f));
            runLater(34L, () -> {
                playFluxSound(player, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.2f, 1.2f);
                beamParticles.energyboom314dmg(beam1, player, kinetic, dontDmgAgain);
                beamParticles.energyboom314(beam1, player, kinetic, dontDmgAgain);
                beamParticles.energyboom315(beam1);
                beamParticles.energyboom1002(beam1, player, beamActive(player));
            });
            runLater(44L, () -> {
                playPrimaryReleaseStack(player);
                playFluxSound(player, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.8f);
                beamParticles.energyboom314(beam1, player, kinetic, dontDmgAgain);
                beamParticles.energyboom315(beam1);
                beamParticles.energyboom1002(beam1, player, beamActive(player));
            });
            runLater(60L, () -> {
                beamParticles.energyboom314(beam1, player, kinetic, dontDmgAgain);
                beamParticles.energyboom315(beam1);
                beamParticles.energyboom1002(beam1, player, beamActive(player));
            });
            runLater(64L, () -> playFluxSound(player, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 1.6f));
            runLater(78L, () -> {
                beamParticles.energyboom314(beam1, player, kinetic, dontDmgAgain);
                beamParticles.energyboom315(beam1);
                beamParticles.energyboom500(beam1, player);
                beamParticles.energyboom1002(beam1, player, beamActive(player));
            });
            return;
        }

        if (kinetic >= 76.0 && kinetic < 85.0) {
            startBeamWindow(player, 100L);
            playFluxSound(player, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.0f);
            playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f);
            runLater(4L, () -> playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f));
            runLater(8L, () -> playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f));
            runLater(34L, () -> {
                playFluxSound(player, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.2f, 1.2f);
                beamParticles.energyboom3144dmg(beam1, player, kinetic, dontDmgAgain);
                beamParticles.energyboom3144(beam1, player);
                beamParticles.energyboom3155(beam1);
                beamParticles.energyboom1002(beam1, player, beamActive(player));
            });
            runLater(44L, () -> {
                playPrimaryReleaseStack(player);
                playFluxSound(player, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.8f);
                beamParticles.energyboom3144(beam1, player);
                beamParticles.energyboom3155(beam1);
                beamParticles.energyboom500(beam1, player);
                beamParticles.energyboom503(beam1, player);
                beamParticles.energyboom504(beam1, player);
                beamParticles.energyboom1002(beam1, player, beamActive(player));
            });
            runLater(60L, () -> {
                beamParticles.energyboom3144(beam1, player);
                beamParticles.energyboom3155(beam1);
                beamParticles.energyboom501(beam1, player);
                beamParticles.energyboom502(beam1, player);
                beamParticles.energyboom1002(beam1, player, beamActive(player));
            });
            runLater(64L, () -> playFluxSound(player, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 1.6f));
            runLater(78L, () -> {
                beamParticles.energyboom3144(beam1, player);
                beamParticles.energyboom3155(beam1);
                beamParticles.energyboom1002(beam1, player, beamActive(player));
            });
            return;
        }

        if (kinetic >= 85.0 && kinetic <= 100.1) {
            startBeamWindow(player, 110L);
            playFluxSound(player, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.0f);
            playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f);
            runLater(4L, () -> playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f));
            runLater(8L, () -> playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f));
            runLater(34L, () -> {
                playFluxSound(player, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.2f, 1.2f);
                beamParticles.energyboom31444dmg(beam2, player, kinetic, dontDmgAgain);
                beamParticles.energyboom31444(beam2, player);
                beamParticles.energyboom3155(beam13);
                beamParticles.energyboom1002(beam2, player, beamActive(player));
                beamParticles.energyboom505(beam2, player);
            });
            runLater(44L, () -> {
                playPrimaryReleaseStack(player);
                playFluxSound(player, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.8f);
                beamParticles.energyboom31444(beam2, player);
                beamParticles.energyboom3155(beam13);
                beamParticles.energyboom1002(beam2, player, beamActive(player));
            });
            runLater(60L, () -> {
                beamParticles.energyboom31444(beam2, player);
                beamParticles.energyboom3155(beam13);
                beamParticles.energyboom505(beam2, player);
                beamParticles.energyboom506(beam2, player);
                beamParticles.energyboom507(beam2, player);
                beamParticles.energyboom508(beam2, player);
                beamParticles.energyboom509(beam2, player);
                beamParticles.energyboom1002(beam2, player, beamActive(player));
            });
            runLater(78L, () -> {
                beamParticles.energyboom31444(beam2, player);
                beamParticles.energyboom3155(beam13);
                beamParticles.energyboom1002(beam2, player, beamActive(player));
                beamParticles.energyboom505(beam2, player);
                beamParticles.energyboom506(beam2, player);
                beamParticles.energyboom507(beam2, player);
            });
            runLater(86L, () -> playFluxSound(player, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 1.6f));
            runLater(94L, () -> {
                beamParticles.energyboom31444(beam2, player);
                beamParticles.energyboom3155(beam13);
                beamParticles.energyboom1002(beam2, player, beamActive(player));
                beamParticles.energyboom508(beam2, player);
                beamParticles.energyboom509(beam2, player);
            });
            return;
        }

        if (kinetic >= 100.1 && kinetic < 199.99) {
            consumeHeldFluxEnergy(player);
            for (int i = 1; i <= 20; i++) {
                int step = i;
                runLater(i, () -> {
                    double radius = 0.5 + (step * 0.5);
                    Location center = player.getLocation();
                    for (int angle = 0; angle < 360; angle += 10) {
                        double rad = Math.toRadians(angle);
                        Location point = center.clone().add(Math.cos(rad) * radius, 0.15, Math.sin(rad) * radius);
                        player.getWorld().spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, FLUX_DUST);
                    }
                });
            }
            return;
        }

        if (kinetic < 199.99) {
            return;
        }

        fluxBeamActive.add(player.getUniqueId());
        playFluxSound(player, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.0f);
        playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f);
        runLater(4L, () -> playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.5f));
        runLater(8L, () -> playFluxSound(player, Sound.ITEM_AXE_SCRAPE, 1.0f, 0.75f));
        runLater(36L, () -> {
            player.damage(0.001);
            player.setHealth(Math.max(0.1, Math.min(player.getHealth(), 0.1)));
            playFluxSound(player, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.2f, 1.2f);
            beamParticles.energyboom313(beam2, player, kinetic, dontDmgAgain);
            beamParticles.energyboom312(beam3, player);
            player.setVelocity(player.getLocation().getDirection().normalize().multiply(-4.0).setY(2.0));
        });
        runLater(46L, () -> {
            beamParticles.energyboom313(beam2, player, kinetic, dontDmgAgain);
            beamParticles.energyboom312(beam3, player);
            beamParticles.energyboom1001(beam2, player, () -> fluxBeamActive.contains(player.getUniqueId()));
            beamParticles.energyboom513(beam2, player);
            beamParticles.energyboom5132(beam2, player);
            playPrimaryReleaseStack(player);
        });
        runLater(60L, () -> {
            beamParticles.energyboom313(beam2, player, kinetic, dontDmgAgain);
            beamParticles.energyboom312(beam3, player);
            beamParticles.energyboom1001(beam2, player, () -> fluxBeamActive.contains(player.getUniqueId()));
            beamParticles.energyboom510(beam2, player);
            beamParticles.energyboom5102(beam2, player);
            beamParticles.energyboom513(beam2, player);
            beamParticles.energyboom5132(beam2, player);
        });
        runLater(68L, () -> {
            playFluxSound(player, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.6f);
            playFluxSound(player, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 2.0f);
            playFluxSound(player, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 1.6f);
        });
        runLater(72L, () -> {
            beamParticles.energyboom313(beam2, player, kinetic, dontDmgAgain);
            beamParticles.energyboom312(beam3, player);
            beamParticles.energyboom1001(beam2, player, () -> fluxBeamActive.contains(player.getUniqueId()));
            beamParticles.energyboom510(beam2, player);
            beamParticles.energyboom5102(beam2, player);
            beamParticles.energyboom511(beam2, player);
            beamParticles.energyboom5112(beam2, player);
            beamParticles.energyboom513(beam2, player);
            beamParticles.energyboom5132(beam2, player);
        });
        runLater(86L, () -> {
            beamParticles.energyboom313(beam2, player, kinetic, dontDmgAgain);
            beamParticles.energyboom312(beam3, player);
            beamParticles.energyboom1001(beam2, player, () -> fluxBeamActive.contains(player.getUniqueId()));
            beamParticles.energyboom510(beam2, player);
            beamParticles.energyboom5102(beam2, player);
            beamParticles.energyboom511(beam2, player);
            beamParticles.energyboom5112(beam2, player);
            beamParticles.energyboom513(beam2, player);
            beamParticles.energyboom5132(beam2, player);
            playFluxSound(player, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.6f);
            playFluxSound(player, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 2.0f);
            playFluxSound(player, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 1.6f);
        });
        runLater(90L, () -> {
            beamParticles.energyboom313(beam2, player, kinetic, dontDmgAgain);
            beamParticles.energyboom312(beam3, player);
            beamParticles.energyboom1001(beam2, player, () -> fluxBeamActive.contains(player.getUniqueId()));
            beamParticles.energyboom510(beam2, player);
            beamParticles.energyboom5102(beam2, player);
            beamParticles.energyboom511(beam2, player);
            beamParticles.energyboom5112(beam2, player);
            beamParticles.energyboom513(beam2, player);
            beamParticles.energyboom5132(beam2, player);
        });
        runLater(104L, () -> {
            beamParticles.energyboom313(beam2, player, kinetic, dontDmgAgain);
            beamParticles.energyboom312(beam3, player);
            beamParticles.energyboom1001(beam2, player, () -> fluxBeamActive.contains(player.getUniqueId()));
            beamParticles.energyboom510(beam2, player);
            beamParticles.energyboom5102(beam2, player);
            beamParticles.energyboom511(beam2, player);
            beamParticles.energyboom5112(beam2, player);
            beamParticles.energyboom513(beam2, player);
            beamParticles.energyboom5132(beam2, player);
            playFluxSound(player, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.6f);
            playFluxSound(player, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 2.0f);
            playFluxSound(player, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 1.6f);
            fluxBeamActive.remove(player.getUniqueId());
        });
    }

    private long energyBeamCooldown(Player player) {
        int energy = GemUtil.getEnergyLevel(player.getInventory().getItemInMainHand());
        return switch (energy) {
            case 1 -> FormatUtil.toMilliseconds(0, 50);
            case 2 -> FormatUtil.toMilliseconds(0, 45);
            case 3 -> FormatUtil.toMilliseconds(0, 40);
            case 4 -> FormatUtil.toMilliseconds(0, 35);
            default -> FormatUtil.toMilliseconds(0, 30);
        };
    }

    private void playFluxSound(Player player, Sound sound, float volume, float pitch) {
        if (!player.isOnline()) {
            return;
        }
        player.getWorld().playSound(player.getLocation(), sound, volume, pitch);
    }

    private void playPrimaryReleaseStack(Player player) {
        playFluxSound(player, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 1.6f);
        playFluxSound(player, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.1f, 1.0f);
        playFluxSound(player, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 0.75f);
    }

    private Runnable playPrimaryReleaseStackSafe(Player player) {
        return () -> {
            if (!player.isOnline()) {
                return;
            }
            playPrimaryReleaseStack(player);
        };
    }

    private void runLater(long ticks, Runnable runnable) {
        Bukkit.getScheduler().runTaskLater(plugin, runnable, ticks);
    }

    private boolean isFluxTier2WithEnergy(ItemStack item) {
        return GemUtil.isGem(item)
            && GemUtil.getGem(item) == GemType.FLUX
            && GemUtil.getTier(item) == GemTier.TIER_2
            && GemUtil.getEnergyLevel(item) > 0;
    }

    private boolean isFluxGemWithEnergy(ItemStack item) {
        return GemUtil.isGem(item)
            && GemUtil.getGem(item) == GemType.FLUX
            && GemUtil.getEnergyLevel(item) > 0;
    }

    private boolean isSwordAxeOrAir(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return true;
        }
        String type = item.getType().name();
        return type.endsWith("_SWORD") || type.endsWith("_AXE");
    }

    private void consumeHeldFluxEnergy(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isFluxTier2WithEnergy(held)) {
            return;
        }

        int energy = GemUtil.getEnergyLevel(held);
        if (energy <= 0) {
            return;
        }

        ItemStack downgraded = plugin.getGemItemManager().getGem(GemType.FLUX, 2, Math.max(0, energy - 1));
        if (downgraded != null) {
            player.getInventory().setItemInMainHand(downgraded);
        }
    }

    private void startBeamWindow(Player player, long durationTicks) {
        UUID uuid = player.getUniqueId();
        fluxBeamActive.add(uuid);
        runLater(durationTicks, () -> fluxBeamActive.remove(uuid));
    }

    private BooleanSupplier beamActive(Player player) {
        UUID uuid = player.getUniqueId();
        return () -> fluxBeamActive.contains(uuid);
    }

    private boolean isTrusted(Player source, LivingEntity target) {
        return plugin.getTrustManager().isTrusted(source, target);
    }

    private boolean isDisabled(Player player) {
        return false;
    }

    private interface TickConsumer {
        void run(int index);
    }

    private void scheduleRepeat(int times, long periodTicks, TickConsumer consumer) {
        for (int i = 0; i < times; i++) {
            int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> consumer.run(index), i * periodTicks);
        }
    }

    public double getKinetic(Player player) {
        if (player == null) {
            return 0.0;
        }
        return fluxKinetic.getOrDefault(player.getUniqueId(), 0.0);
    }

    public void setKinetic(Player player, double value) {
        if (player == null) {
            return;
        }
        fluxKinetic.put(player.getUniqueId(), Math.max(0.0, Math.min(MAX_KINETIC, value)));
    }
}
