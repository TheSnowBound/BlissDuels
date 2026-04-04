package me.thesnowbound.blissDuels.listeners;

import me.thesnowbound.blissDuels.BlissDuels;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

/**
 * Direct particle/damage port for Flux beam sections from reference/main/beams.sk.
 * Method names intentionally mirror Skript functions for 1:1 traceability.
 */
public class FluxBeamParticles {
    private static final Color FLUX_BLUE = Color.fromRGB(94, 215, 255);
    private static final double[] PHASE_SEQ_500_STYLE = {
        -0.06, -0.07, -0.08, -0.09, -0.10, -0.11, -0.12, -0.13, -0.10, -0.08,
        -0.06, -0.04, -0.03, +0.05, +0.06, +0.07, +0.08, +0.09, +0.10, +0.11,
        +0.12, +0.10, +0.08, +0.06, +0.04, +0.02, -0.06, -0.07, -0.08, -0.09,
        -0.10, -0.11, -0.12, -0.13, -0.10, -0.08, -0.06, -0.04, -0.03, +0.02
    };
    private static final double[] PHASE_SEQ_501_STYLE = {
        -0.05, -0.06, -0.07, -0.08, -0.09, -0.10, -0.11, -0.14, -0.10, -0.08,
        -0.06, -0.04, -0.03, +0.05, +0.06, +0.07, +0.08, +0.09, +0.10, +0.11,
        +0.12, +0.10, +0.08, +0.06, +0.04, +0.02, -0.05, -0.06, -0.07, -0.08,
        -0.09, -0.10, -0.11, -0.14, -0.10, -0.08, -0.06, -0.04, -0.03, +0.02
    };
    private static final double[] PHASE_SEQ_503_STYLE = {
        +0.05, +0.06, +0.07, +0.08, +0.09, +0.10, +0.11, +0.12, +0.10, +0.08,
        +0.06, +0.04, +0.02, -0.06, -0.07, -0.08, -0.09, -0.10, -0.11, -0.12,
        -0.13, -0.10, -0.08, -0.06, -0.04, -0.03, +0.05, +0.06, +0.07, +0.08,
        +0.09, +0.10, +0.11, +0.12, +0.10, +0.08, +0.06, +0.04, +0.02, -0.06
    };

    private final BlissDuels plugin;
    private final BiPredicate<Player, LivingEntity> trustedCheck;

    public FluxBeamParticles(BlissDuels plugin, BiPredicate<Player, LivingEntity> trustedCheck) {
        this.plugin = plugin;
        this.trustedCheck = trustedCheck;
    }

    // ---- 0-56% bands ----

    public void energyboom651(Location loc, Player p, double kineticPercent, Set<UUID> dontDmgAgain) {
        drawSpiralEndRod(loc, 12, 0.4, 8, 45.0, 0.3, 0.22, 0.1);
        runBeamDamage(loc, p, 12, 0.4, kineticPercent, dontDmgAgain);
    }

    public void energyboom659(Location loc, Player p, double kineticPercent, Set<UUID> dontDmgAgain) {
        drawSpiralEndRod(loc, 20, 0.4, 8, 45.0, 0.3, 0.07, 0.1);
        runBeamDamage(loc, p, 20, 0.4, kineticPercent, dontDmgAgain);
    }

    public void energyboom65444(Location loc, Player p, double kineticPercent, Set<UUID> dontDmgAgain) {
        drawSpiralEndRod(loc, 20, 0.4, 4, 90.0, 0.3, 0.07, 0.1);
        runBeamDamage(loc, p, 20, 0.4, kineticPercent, dontDmgAgain);
    }

    public void energyboom654(Location loc, Player p, double kineticPercent, Set<UUID> dontDmgAgain) {
        drawSpiralEndRod(loc, 25, 0.4, 12, 30.0, 0.3, 0.07, 0.0);
        runBeamDamage(loc, p, 25, 0.4, kineticPercent, dontDmgAgain);
    }

    public void energyboom607(Location loc, Player p, double kineticPercent, Set<UUID> dontDmgAgain) {
        drawSpiralEndRod(loc, 80, 0.2, 6, 60.0, 0.3, 0.03, 0.1);
        runBeamDamage(loc, p, 80, 0.2, kineticPercent, dontDmgAgain);
    }

    public void energyboom609999(Location loc, Player p, double kineticPercent, Set<UUID> dontDmgAgain) {
        // Skript alternates radius growth/decay in blocks of 6 loops.
        drawSpiralPatternEndRod(
            loc,
            0.8,
            8,
            45.0,
            0.3,
            new double[]{+0.08, -0.05, +0.08, -0.05, +0.08, -0.05, +0.08, -0.05, +0.08},
            new int[]{6, 6, 6, 6, 6, 6, 6, 6, 6},
            0.0
        );
        runBeamDamage(loc, p, 54, 0.8, kineticPercent, dontDmgAgain);
    }

    public void energyboom609(Location loc, Player p, double kineticPercent, Set<UUID> dontDmgAgain) {
        drawSpiralEndRod(loc, 50, 0.8, 6, 60.0, 0.3, 0.01, 0.0);
        runBeamDamage(loc, p, 50, 0.8, kineticPercent, dontDmgAgain);
    }

    public void energyboom6544844(Location loc) {
        drawSpiralPatternDust(
            loc,
            0.8,
            24,
            15.0,
            0.5,
            new double[]{+0.2, +0.7, +0.5},
            new int[]{2, 3, 3},
            new int[]{2, 4, 2},
            new float[]{1.0f, 1.0f, 1.0f},
            0.3
        );
    }

    public void energyboom654444(Location loc) {
        drawSpiralPatternDust(
            loc,
            0.8,
            24,
            15.0,
            0.3,
            new double[]{+0.1, +0.4, +0.15},
            new int[]{2, 3, 6},
            new int[]{2, 2, 2},
            new float[]{1.0f, 1.0f, 1.0f},
            0.3
        );
    }

    public void energyboom6544(Location loc) {
        drawSpiralPatternDust(
            loc,
            0.8,
            24,
            15.0,
            0.3,
            new double[]{+0.1, +0.5, +0.15},
            new int[]{2, 5, 7},
            new int[]{5, 5, 5},
            new float[]{1.5f, 1.5f, 1.5f},
            0.3
        );
    }

    public void energyboom6077(Location loc) {
        drawSpiralPatternDust(
            loc,
            0.2,
            60,
            6.0,
            0.3,
            new double[]{+0.04, +0.04, +0.04, +0.04},
            new int[]{20, 20, 20, 20},
            new int[]{1, 1, 1, 1},
            new float[]{1.0f, 1.0f, 1.0f, 1.0f},
            0.3
        );
    }

    public void energyboom60999(Location loc) {
        drawSpiralPatternDust(
            loc,
            0.8,
            8,
            45.0,
            0.3,
            new double[]{+0.1, -0.03, +0.1, -0.1, +0.1, -0.03, +0.15, -0.01},
            new int[]{6, 6, 6, 6, 6, 6, 6, 6},
            new int[]{5, 5, 5, 5, 5, 5, 5, 5},
            new float[]{1.5f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f},
            0.3
        );
    }

    public void energyboom6099(Location loc) {
        drawBeamDustSpread(loc, 49, 0.8, 4, 0.15, 0.01, 1.5f);
    }

    // ---- 56%+ bands ----

    public void energyboom314dmg(Location loc, Player p, double kineticPercent, Set<UUID> dontDmgAgain) {
        runBeamDamage(loc, p, 70, 1.0, kineticPercent, dontDmgAgain);
    }

    public void energyboom314(Location loc, Player p, double kineticPercent, Set<UUID> dontDmgAgain) {
        runBeamDust(loc, 70, 1.0, 40, 0.5, 2.5f);
        runBeamDamage(loc, p, 70, 1.0, kineticPercent, dontDmgAgain);
    }

    public void energyboom3144dmg(Location loc, Player p, double kineticPercent, Set<UUID> dontDmgAgain) {
        runBeamDamage(loc, p, 80, 1.0, kineticPercent, dontDmgAgain);
    }

    public void energyboom3144(Location loc, @SuppressWarnings("unused") Player p) {
        runBeamDust(loc, 80, 1.0, 40, 0.7, 2.5f);
    }

    public void energyboom31444dmg(Location loc, Player p, double kineticPercent, Set<UUID> dontDmgAgain) {
        runBeamDamage(loc, p, 80, 1.0, kineticPercent, dontDmgAgain);
    }

    public void energyboom31444(Location loc, @SuppressWarnings("unused") Player p) {
        runBeamDust(loc, 80, 1.0, 40, 0.7, 3.5f);
    }

    public void energyboom315(Location loc) {
        runEndRodLine(loc, 230, 0.3, 3);
    }

    public void energyboom3155(Location loc) {
        runEndRodLine(loc, 250, 0.3, 3);
    }

    @SuppressWarnings("unused")
    public void energyboom1001(Location loc, Player p) {
        energyboom1001(loc, p, () -> true);
    }

    public void energyboom1001(Location loc, Player p, BooleanSupplier beamActive) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        final Vector dir = loc.getDirection().normalize();
        final float yaw = loc.getYaw();

        new BukkitRunnable() {
            int outer = 0;
            int phase = 0;
            double fluxN = 1.0;
            double superFlux = 5.0;

            @Override
            public void run() {
                if (!p.isOnline() || !beamActive.getAsBoolean() || outer >= 25) {
                    cancel();
                    return;
                }

                switch (phase) {
                    case 0 -> runSegment(world, loc, dir, yaw, 3, -0.1);
                    case 1 -> {
                        runSegment(world, loc, dir, yaw, 3, -0.2);
                        runSegment(world, loc, dir, yaw, 3, -0.3);
                        runSegment(world, loc, dir, yaw, 2, -0.2);
                    }
                    case 2 -> {
                        runSegment(world, loc, dir, yaw, 3, +0.1);
                        runSegment(world, loc, dir, yaw, 3, +0.2);
                    }
                    case 3 -> {
                        runSegment(world, loc, dir, yaw, 3, +0.3);
                        runSegment(world, loc, dir, yaw, 2, +0.2);
                        outer++;
                    }
                    default -> {
                    }
                }

                phase = (phase + 1) % 4;
            }

            private void runSegment(World world, Location base, Vector direction, float baseYaw, int loops, double fluxDelta) {
                for (int l = 0; l < loops; l++) {
                    Location center = base.clone().add(direction.clone().multiply(fluxN));
                    for (int i = 0; i < 8; i++) {
                        double angle = i * 45.0 + 30.0;
                        Vector offset = spherical(superFlux, baseYaw - 90.0, angle);
                        world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(offset), 3, 0.0, 0.0, 0.0, 0.0);
                    }
                    fluxN += 0.3;
                    superFlux += fluxDelta;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @SuppressWarnings("unused")
    public void energyboom1002(Location loc, Player p) {
        energyboom1002(loc, p, () -> true);
    }

    public void energyboom1002(Location loc, Player p, BooleanSupplier beamActive) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        final Vector dir = loc.getDirection().normalize();
        final double baseX = loc.getX();
        final double baseY = loc.getY();
        final double baseZ = loc.getZ();

        // Skript: 35 outer loops, each does 4 placements, then waits 1 tick.
        new BukkitRunnable() {
            int outer = 0;
            double fluxN = 1.0;

            @Override
            public void run() {
                if (!p.isOnline() || !beamActive.getAsBoolean() || outer >= 35) {
                    cancel();
                    return;
                }

                for (int i = 0; i < 4; i++) {
                    Location point = new Location(
                        world,
                        baseX + (dir.getX() * fluxN),
                        baseY + (dir.getY() * fluxN),
                        baseZ + (dir.getZ() * fluxN)
                    );
                    world.spawnParticle(Particle.SONIC_BOOM, point, 1, 0.0, 0.0, 0.0, 0.000000001D);
                    fluxN += 0.5;
                }

                outer++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void energyboom316(Location loc, Player p) {
        runDualPhaseOrbitalTimed(loc, p, 40, 4, 2, 0.3, 2, 220.0, 120.0, 1.5, -0.11, +0.21);
    }

    public void energyboom317(Location loc, Player p) {
        runDualPhaseOrbitalTimed(loc, p, 60, 4, 2, 0.3, 2, 220.0, 120.0, 2.0, -0.10, +0.22);
    }

    public void energyboom318(Location loc, Player p) {
        runDualPhaseOrbitalTimed(loc, p, 60, 4, 2, 0.3, 2, 220.0, 120.0, 2.5, -0.10, +0.22);
    }

    public void energyboom500(Location loc, Player p) {
        drawOrbitalEndRodScriptedTimed(loc, p, 2, 220.0, 120.0, 1.7, PHASE_SEQ_500_STYLE);
    }

    public void energyboom501(Location loc, Player p) {
        drawOrbitalEndRodScriptedTimed(loc, p, 2, 20.0, 240.0, 1.5, PHASE_SEQ_501_STYLE);
    }

    public void energyboom502(Location loc, Player p) {
        drawOrbitalEndRodScriptedTimed(loc, p, 2, 20.0, 60.0, 1.5, PHASE_SEQ_501_STYLE);
    }

    public void energyboom503(Location loc, Player p) {
        drawOrbitalEndRodScriptedTimed(loc, p, 2, 220.0, 120.0, 1.5, PHASE_SEQ_503_STYLE);
    }

    public void energyboom504(Location loc, Player p) {
        drawOrbitalEndRodScriptedTimed(loc, p, 2, 220.0, 300.0, 1.7, PHASE_SEQ_500_STYLE);
    }

    public void energyboom505(Location loc, Player p) {
        drawOrbitalEndRodScriptedTimed(loc, p, 2, 220.0, 120.0, 2.5, PHASE_SEQ_500_STYLE);
    }

    public void energyboom506(Location loc, Player p) {
        drawOrbitalEndRodScriptedTimed(loc, p, 2, 20.0, 240.0, 2.5, PHASE_SEQ_501_STYLE);
    }

    public void energyboom507(Location loc, Player p) {
        drawOrbitalEndRodScriptedTimed(loc, p, 2, 20.0, 60.0, 2.5, PHASE_SEQ_501_STYLE);
    }

    public void energyboom508(Location loc, Player p) {
        drawOrbitalEndRodScriptedTimed(loc, p, 2, 220.0, 120.0, 2.5, PHASE_SEQ_503_STYLE);
    }

    public void energyboom509(Location loc, Player p) {
        drawOrbitalEndRodScriptedTimed(loc, p, 2, 220.0, 300.0, 2.5, PHASE_SEQ_500_STYLE);
    }

    public void energyboom510(Location loc, Player p) {
        drawOrbitalEndRodScriptedTimed(loc, p, 6, 60.0, 30.0, 8.0, PHASE_SEQ_500_STYLE);
    }

    public void energyboom5102(Location loc, Player p) {
        drawOrbitalEndRodScriptedTimed(loc, p, 6, 60.0, 30.0, 9.0, PHASE_SEQ_503_STYLE);
    }

    public void energyboom511(Location loc, Player p) {
        drawOrbitalEndRodScriptedTimed(loc, p, 8, 45.0, 30.0, 11.0, PHASE_SEQ_500_STYLE);
    }

    public void energyboom5112(Location loc, Player p) {
        drawOrbitalEndRodScriptedTimed(loc, p, 8, 45.0, 30.0, 11.0, PHASE_SEQ_503_STYLE);
    }

    public void energyboom513(Location loc, Player p) {
        drawOrbitalEndRodScriptedTimed(loc, p, 6, 60.0, 30.0, 5.0, PHASE_SEQ_500_STYLE);
    }

    public void energyboom5132(Location loc, Player p) {
        drawOrbitalEndRodScriptedTimed(loc, p, 8, 45.0, 30.0, 5.0, PHASE_SEQ_503_STYLE);
    }

    public void energyboom312(Location loc, @SuppressWarnings("unused") Player p) {
        runBeamDust(loc, 120, 1.1, 40, 2.0, 4.0f);
    }

    public void energyboom313(Location loc, Player p, double kineticPercent, Set<UUID> dontDmgAgain) {
        runEndRodLine(loc, 440, 0.3, 3);
        runBeamDamage(loc, p, 440, 0.3, kineticPercent, dontDmgAgain);
    }


    // Mirrors the script pattern used by energyboom316/317/318:
    // phase A loops, wait 1 tick, phase B loops, then repeats.
    private void runDualPhaseOrbitalTimed(
        Location loc,
        Player p,
        int outerLoops,
        int phaseALoops,
        int phaseBLoops,
        double distanceStep,
        int iterations,
        double angleStep,
        double angleBase,
        double startRadius,
        double phaseADelta,
        double phaseBDelta
    ) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        new BukkitRunnable() {
            int outer = 0;
            boolean phaseA = true;
            double fluxN = 1.0;
            double radius = startRadius;

            @Override
            public void run() {
                if (!p.isOnline() || outer >= outerLoops) {
                    cancel();
                    return;
                }

                int loops = phaseA ? phaseALoops : phaseBLoops;
                double delta = phaseA ? phaseADelta : phaseBDelta;

                for (int l = 0; l < loops; l++) {
                    Location center = pointInFront(loc, fluxN);
                    for (int i = 0; i < iterations; i++) {
                        double angle = (i * angleStep) + angleBase;
                        Vector off = spherical(radius, center.getYaw() - 90.0, angle);
                        world.spawnParticle(Particle.END_ROD, center.clone().add(off), 1, 0.0, 0.0, 0.0, 0.000000001D, null, true);
                    }
                    fluxN += distanceStep;
                    radius += delta;
                }

                if (!phaseA) {
                    outer++;
                }
                phaseA = !phaseA;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drawSpiralEndRod(Location loc, int loops, double distStep, int iterations, double angleStep, double startRadius, double radiusDelta, double particleSpread) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        double fluxN = 1.0;
        double radius = startRadius;
        double phase = 20.0;

        for (int i = 0; i < loops; i++) {
            Location center = pointInFront(loc, fluxN);
            phase += 5.0;
            for (int j = 0; j < iterations; j++) {
                double angle = (j * angleStep) + phase;
                Vector off = spherical(radius, center.getYaw() - 90.0, angle);
                world.spawnParticle(Particle.END_ROD, center.clone().add(off), 1, particleSpread, particleSpread, particleSpread, 0.000000001D, null, true);
            }
            fluxN += distStep;
            radius += radiusDelta;
        }
    }

    private void drawSpiralPatternEndRod(Location loc, double distStep, int iterations, double angleStep, double startRadius, double[] radiusDeltas, int[] loopsPerPhase, double particleSpread) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        double fluxN = 1.0;
        double radius = startRadius;
        double phase = 20.0;

        for (int p = 0; p < loopsPerPhase.length; p++) {
            for (int l = 0; l < loopsPerPhase[p]; l++) {
                Location center = pointInFront(loc, fluxN);
                phase += 5.0;
                for (int j = 0; j < iterations; j++) {
                    double angle = (j * angleStep) + phase;
                    Vector off = spherical(radius, center.getYaw() - 90.0, angle);
                    world.spawnParticle(Particle.END_ROD, center.clone().add(off), 1, particleSpread, particleSpread, particleSpread, 0.000000001D, null, true);
                }
                fluxN += distStep;
                radius += radiusDeltas[p];
            }
        }
    }

    private void drawSpiralPatternDust(Location loc, double distStep, int iterations, double angleStep, double startRadius, double[] radiusDeltas, int[] loopsPerPhase, int[] counts, float[] sizes, double spread) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        double fluxN = 1.0;
        double radius = startRadius;
        double phase = 20.0;

        for (int p = 0; p < loopsPerPhase.length; p++) {
            Particle.DustTransition transition = new Particle.DustTransition(FLUX_BLUE, Color.WHITE, sizes[p]);
            for (int l = 0; l < loopsPerPhase[p]; l++) {
                Location center = pointInFront(loc, fluxN);
                phase += 5.0;
                for (int j = 0; j < iterations; j++) {
                    double angle = (j * angleStep) + phase;
                    Vector off = spherical(radius, center.getYaw() - 90.0, angle);
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, center.clone().add(off), counts[p], spread, spread, spread, 0.000000001D, transition, true);
                }
                fluxN += distStep;
                radius += radiusDeltas[p];
            }
        }
    }

    private void drawBeamDustSpread(Location loc, int samples, double step, int count, double startSpread, double spreadDelta, float size) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        Vector direction = loc.getDirection().normalize();
        Particle.DustTransition transition = new Particle.DustTransition(FLUX_BLUE, Color.WHITE, size);
        double spread = startSpread;

        for (int i = 1; i <= samples; i++) {
            Location point = loc.clone().add(direction.clone().multiply(i * step));
            spread += spreadDelta;
            for (int burst = 0; burst < 10; burst++) {
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, point, count, spread, spread, spread, 0.000000001D, transition, true);
            }
        }
    }

    private Location pointInFront(Location loc, double distance) {
        return loc.clone().add(loc.getDirection().normalize().multiply(distance));
    }

    private void runBeamDust(Location loc, int samples, double step, int count, double offset, float size) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        Vector direction = loc.getDirection().normalize();
        Particle.DustTransition transition = new Particle.DustTransition(FLUX_BLUE, Color.WHITE, size);
        double baseX = loc.getX();
        double baseY = loc.getY();
        double baseZ = loc.getZ();

        for (int i = 1; i <= samples; i++) {
            double dist = i * step;
            Location point = new Location(
                world,
                baseX + (direction.getX() * dist),
                baseY + (direction.getY() * dist),
                baseZ + (direction.getZ() * dist)
            );
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, point, count, offset, offset, offset, 0.000000001D, transition, true);
        }
    }

    private void runBeamDamage(Location loc, Player p, int samples, double step, double kineticPercent, Set<UUID> dontDmgAgain) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        Vector dir = loc.getDirection().normalize();
        Vector start = loc.toVector();
        Vector end = start.clone().add(dir.clone().multiply(samples * step));
        Vector mid = start.clone().add(end).multiply(0.5);

        int duraPenalty = (int) Math.round(kineticPercent + 44.0);
        double halfLen = start.distance(end) / 2.0;

        for (Entity entity : world.getNearbyEntities(mid.toLocation(world), halfLen + 5.0, halfLen + 5.0, halfLen + 5.0)) {
            if (!(entity instanceof LivingEntity living) || living.equals(p)) {
                continue;
            }
            UUID id = living.getUniqueId();
            if (dontDmgAgain.contains(id)) {
                continue;
            }
            if (trustedCheck.test(p, living)) {
                continue;
            }

            double distSq = distanceSquaredPointToSegment(living.getLocation().toVector(), start, end);
            if (distSq > 25.0) {
                continue;
            }

            dontDmgAgain.add(id);
            double damage = living.getHealth() * (kineticPercent / 100.0);
            living.damage(Math.max(0.001, damage), p);
            damageArmor(living, duraPenalty);
        }
    }

    private void runEndRodLine(Location loc, int samples, double step, int count) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        Vector direction = loc.getDirection().normalize();
        double baseX = loc.getX();
        double baseY = loc.getY();
        double baseZ = loc.getZ();

        for (int i = 1; i <= samples; i++) {
            double dist = i * step;
            Location point = new Location(
                world,
                baseX + (direction.getX() * dist),
                baseY + (direction.getY() * dist),
                baseZ + (direction.getZ() * dist)
            );
            world.spawnParticle(Particle.END_ROD, point, count, 0.0, 0.0, 0.0, 0.03, null, true);
        }
    }

    private void damageArmor(LivingEntity living, int amount) {
        if (amount <= 0) {
            return;
        }

        EntityEquipment equipment = living.getEquipment();
        if (equipment == null) {
            return;
        }

        ItemStack[] armor = equipment.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null || piece.getType().isAir()) {
                continue;
            }

            ItemMeta meta = piece.getItemMeta();
            if (!(meta instanceof Damageable damageable)) {
                continue;
            }

            damageable.setDamage(damageable.getDamage() + amount);
            piece.setItemMeta(damageable);
            armor[i] = piece;
        }
        equipment.setArmorContents(armor);
    }

    private static double distanceSquaredPointToSegment(Vector point, Vector segmentStart, Vector segmentEnd) {
        Vector segment = segmentEnd.clone().subtract(segmentStart);
        double segLenSq = segment.lengthSquared();
        if (segLenSq <= 1.0E-7) {
            return point.distanceSquared(segmentStart);
        }

        double t = point.clone().subtract(segmentStart).dot(segment) / segLenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        Vector projection = segmentStart.clone().add(segment.multiply(t));
        return point.distanceSquared(projection);
    }

    private static Vector spherical(double radius, double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);

        double x = radius * Math.cos(pitch) * Math.cos(yaw);
        double y = radius * Math.sin(pitch);
        double z = radius * Math.cos(pitch) * Math.sin(yaw);
        return new Vector(x, y, z);
    }

    // Timed long-form scripted cadence used by high-energy beam ornament methods.
    private void drawOrbitalEndRodScriptedTimed(
        Location loc,
        Player p,
        int iterations,
        double angleStep,
        double angleBase,
        double startRadius,
        double[] phaseDeltas
    ) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        new BukkitRunnable() {
            int stepIndex = 0;
            double fluxN = 1.0;
            double radius = startRadius;

            @Override
            public void run() {
                if (!p.isOnline() || stepIndex >= phaseDeltas.length) {
                    cancel();
                    return;
                }

                Location center = pointInFront(loc, fluxN);
                for (int i = 0; i < iterations; i++) {
                    double angle = (i * angleStep) + angleBase;
                    Vector off = spherical(radius, center.getYaw() - 90.0, angle);
                    world.spawnParticle(Particle.END_ROD, center.clone().add(off), 1, 0.0, 0.0, 0.0, 0.000000001D, null, true);
                }

                fluxN += 0.3;
                radius += phaseDeltas[stepIndex];
                stepIndex++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
