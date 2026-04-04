package me.thesnowbound.blissDuels.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;

/**
 * Shared particle helpers for line effects used by multiple abilities.
 */
public final class AbilityParticleUtil {
    private AbilityParticleUtil() {
    }

    public static void drawAbilityLine(LivingEntity from, LivingEntity to, Color color, boolean withSmoke) {
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) {
            return;
        }
        if (!from.getWorld().equals(to.getWorld())) {
            return;
        }

        Location start = from.getEyeLocation().clone().add(0.0, -0.7, 0.0);
        Location end = to.getEyeLocation().clone();
        double distance = start.distance(end);
        if (distance <= 0.01) {
            return;
        }

        double step = 0.15;
        int points = Math.max(1, (int) Math.ceil(distance / step));
        double dx = (end.getX() - start.getX()) / points;
        double dy = (end.getY() - start.getY()) / points;
        double dz = (end.getZ() - start.getZ()) / points;

        Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);
        for (int i = 0; i <= points; i++) {
            Location point = start.clone().add(dx * i, dy * i, dz * i);
            point.getWorld().spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, dust, true);
            if (withSmoke) {
                point.getWorld().spawnParticle(Particle.SMOKE, point, 1, 0.0, 0.0, 0.0, 0.0, null, true);
            }
        }
    }
}

