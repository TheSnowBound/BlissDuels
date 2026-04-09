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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Speed gem conversion slice based on reference/Gems/speed.sk.
 */
public class SpeedAbilityListener implements Listener {
    private static final Particle.DustOptions SPEED_DUST = new Particle.DustOptions(Color.fromRGB(254, 253, 23), 1.0f);

    private final BlissDuels plugin;

    private final Map<UUID, Long> doubleClickWindow = new HashMap<>();

    private final Set<UUID> blurReady = new HashSet<>();
    private final Set<UUID> blurringVictims = new HashSet<>();
    private final Map<UUID, Integer> blurHitCounter = new HashMap<>();

    private final Set<UUID> speedStormSelfActive = new HashSet<>();
    private final Map<UUID, Location> speedStormCenter = new HashMap<>();

    private final Map<UUID, Double> savedAttackSpeed = new HashMap<>();

    public SpeedAbilityListener(BlissDuels plugin) {
        this.plugin = plugin;
        startSwimPassiveTick();
    }

    @EventHandler()
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();


        Player player = event.getPlayer();
        if (isDisabled(player)) {
            return;
        }

        if (action.isRightClick()) {
            if (tryThunderStep(player)) {
                return;
            }
            trySpeedStorm(player);
            return;
        }

        tryPrimeBlur(player);
    }

    @EventHandler( priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (isDisabled(attacker)) {
            return;
        }

        UUID victimId = victim.getUniqueId();
        if (blurringVictims.contains(victimId)) {
            return;
        }

        UUID attackerId = attacker.getUniqueId();
        if (!blurReady.contains(attackerId)) {
            return;
        }
        if (!isSpeedTier2WithEnergy(attacker.getInventory().getItemInMainHand())) {
            return;
        }
        if (isTrusted(attacker, victim)) {
            return;
        }

        executeBlurCombo(attacker, victim);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        blurReady.remove(uuid);
        blurHitCounter.remove(uuid);
        doubleClickWindow.remove(uuid);
        speedStormSelfActive.remove(uuid);
        speedStormCenter.remove(uuid);
        restoreAttackSpeed(event.getPlayer());
    }

    private boolean tryThunderStep(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack held = player.getInventory().getItemInMainHand();

        if (!isSpeedGem(offhand) || GemUtil.getEnergyLevel(offhand) <= 0) {
            return false;
        }
        if (!isSwordAxeOrAir(held)) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long expires = doubleClickWindow.getOrDefault(uuid, 0L);

        if (now > expires) {
            doubleClickWindow.put(uuid, now + 700L);
            return true;
        }

        doubleClickWindow.remove(uuid);
        if (!plugin.getCooldownManager().isCooldownReady(player, "Thunder")) {
            return true;
        }

        Vector launch = player.getLocation().getDirection().normalize().multiply(1.5);
        player.setVelocity(launch);
        drawThunderStep(player);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_DEATH, 1.0f, 1.2f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.0f, 1.4f);
        plugin.getCooldownManager().setCooldown(player, "Thunder", FormatUtil.toMilliseconds(0, 30));
        player.sendMessage(ColorUtil.color("<##FEFD17> Thunder Step activated"));
        return true;
    }

    private void drawThunderStep(Player player) {
        Location center = player.getLocation().clone();
        double yaw = center.getYaw() - 90.0;

        new BukkitRunnable() {
            int angle = 0;

            @Override
            public void run() {
                if (angle >= 360 || !player.isOnline()) {
                    cancel();
                    return;
                }

                angle += 8;
                double radians = Math.toRadians(angle);
                double x = Math.cos(radians) * 2.0;
                double z = Math.sin(radians) * 2.0;
                Location point = center.clone().add(x, 0.2, z);
                point.setYaw((float) yaw);
                player.getWorld().spawnParticle(Particle.CLOUD, point, 1, 0.1, 0.1, 0.1, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void tryPrimeBlur(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isSpeedTier2WithEnergy(held)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(player, "Blur")) {
            return;
        }

        UUID uuid = player.getUniqueId();
        blurReady.add(uuid);
        blurHitCounter.put(uuid, 0);

        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.00001);
        player.sendMessage(ColorUtil.color("<##FEFD17> Your next hit will inflict Blur"));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int hits = blurHitCounter.getOrDefault(uuid, 0);
            if (hits == 0 && blurReady.remove(uuid)) {
                blurHitCounter.remove(uuid);
                player.sendMessage(ColorUtil.color("<##FEFD17> Blur unreadied, you took too long"));
            }
        }, 100L);
    }

    private void executeBlurCombo(Player attacker, LivingEntity victim) {
        UUID attackerId = attacker.getUniqueId();
        UUID victimId = victim.getUniqueId();

        blurReady.remove(attackerId);
        blurHitCounter.merge(attackerId, 1, Integer::sum);
        blurringVictims.add(victimId);

        attacker.sendMessage(ColorUtil.color("<##FEFD17> Blur used on " + safeName(victim)));
        if (victim instanceof Player victimPlayer) {
            victimPlayer.sendMessage(ColorUtil.color("<##FEFD17> You have been affected by Blur"));
        }

        // Four-hit sequence approximating the Skript NPC strike cadence.
        runBlurStrike(attacker, victim, 0L, 2.0, 0.4, 0.6, -3.0);
        runBlurStrike(attacker, victim, 5L, 2.0, 0.4, 0.2, 3.0);
        runBlurStrike(attacker, victim, 10L, 3.0, 0.6, 0.3, 0.0);
        runBlurStrike(attacker, victim, 15L, 2.0, 0.4, 0.0, 1.5);

        long cooldown = blurCooldownForEnergy(GemUtil.getEnergyLevel(attacker.getInventory().getItemInMainHand()));
        plugin.getCooldownManager().setCooldown(attacker, "Blur", cooldown);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            blurringVictims.remove(victimId);
            blurHitCounter.put(attackerId, 0);
        }, 30L);
    }

    private void runBlurStrike(Player attacker, LivingEntity victim, long delay, double damage, double knock, double yBoost, double sideOffset) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!attacker.isOnline() || victim.isDead() || !victim.isValid()) {
                return;
            }

            Location victimLoc = victim.getLocation().clone();
            Vector side = attacker.getLocation().getDirection().normalize();
            Vector right = new Vector(-side.getZ(), 0, side.getX()).normalize();
            Location fx = victimLoc.clone().add(right.multiply(sideOffset)).add(0, 1.0, 0);

            victim.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, fx, 24, 0.4, 0.4, 0.4, 0.01);
            victim.getWorld().playSound(fx, Sound.ITEM_TRIDENT_THUNDER, 0.5f, 1.5f);
            victim.getWorld().playSound(fx, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.5f, 1.2f);

            victim.setNoDamageTicks(0);
            victim.damage(damage, attacker);
            Vector knockVector = victimLoc.toVector().subtract(attacker.getLocation().toVector()).normalize().multiply(knock);
            knockVector.setY(yBoost);
            victim.setVelocity(knockVector);
        }, delay);
    }

    private void trySpeedStorm(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isSpeedTier2WithEnergy(held)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(player, "SpeedStorm")) {
            return;
        }

        int energy = GemUtil.getEnergyLevel(held);
        plugin.getCooldownManager().setCooldown(player, "SpeedStorm", speedStormCooldownForEnergy(energy));

        Entity target = player.getTargetEntity(8);
        if (target instanceof LivingEntity targetLiving && target != player) {
            activateTargetedSpeedStorm(player, targetLiving);
            return;
        }

        activateSelfSpeedStorm(player);
    }

    private void activateSelfSpeedStorm(Player caster) {
        UUID uuid = caster.getUniqueId();
        speedStormSelfActive.add(uuid);
        Location center = caster.getLocation().clone();
        speedStormCenter.put(uuid, center);

        caster.sendMessage(ColorUtil.color("<##FEFD17> You summoned a speed storm"));
        drawSpeedStormRing(center);

        new BukkitRunnable() {
            int loops = 0;

            @Override
            public void run() {
                if (!caster.isOnline() || loops >= 20) {
                    speedStormSelfActive.remove(uuid);
                    speedStormCenter.remove(uuid);
                    cancel();
                    return;
                }

                Location strike = center.clone().add(
                    ThreadLocalRandom.current().nextDouble(-5.0, 5.0),
                    0.0,
                    ThreadLocalRandom.current().nextDouble(-5.0, 5.0)
                );
                strike.getWorld().strikeLightningEffect(strike);
                applySelfStormEffects(caster, center, strike);

                loops++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void applySelfStormEffects(Player caster, Location stormCenter, Location strikeLoc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(stormCenter.getWorld())) {
                continue;
            }
            if (player.getLocation().distanceSquared(strikeLoc) > 4.0) {
                continue;
            }

            if (player.equals(caster) && player.getLocation().distanceSquared(stormCenter) <= 12 * 12) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 12 * 20, 5, false, false));
            } else {
                stun(player, 3);
            }
        }
    }

    private void activateTargetedSpeedStorm(Player caster, LivingEntity target) {
        Location center = target.getLocation().clone();
        caster.sendMessage(ColorUtil.color("<##FEFD17> Group Speed Storm activated"));

        drawSpeedStormRing(center);

        Set<UUID> boosted = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(center.getWorld())) {
                continue;
            }
            if (player.getLocation().distanceSquared(center) > 4 * 4) {
                continue;
            }
            if (!isTrusted(caster, player) && !player.equals(caster)) {
                continue;
            }
            if (setAttackSpeed(player, 6.0)) {
                boosted.add(player.getUniqueId());
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID uuid : boosted) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    restoreAttackSpeed(player);
                }
            }
        }, 30L * 20L);
    }

    private void drawSpeedStormRing(Location center) {
        new BukkitRunnable() {
            int pass = 0;

            @Override
            public void run() {
                if (pass >= 6) {
                    cancel();
                    return;
                }

                double radius = 1.0 + (pass * 0.5);
                for (int i = 0; i < 72; i++) {
                    double angle = Math.toRadians(i * 5);
                    Location point = center.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
                    point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0.01, SPEED_DUST);
                    point.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, point, 1, 0, 0, 0, 0.01);
                }

                pass++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startSwimPassiveTick() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isDisabled(player)) {
                    continue;
                }
                if (!isSpeedGem(player.getInventory().getItemInOffHand())) {
                    continue;
                }
                if (!player.isSwimming()) {
                    continue;
                }

                ItemStack boots = player.getInventory().getBoots();
                if (boots != null && boots.containsEnchantment(org.bukkit.enchantments.Enchantment.DEPTH_STRIDER)) {
                    continue;
                }

                Location loc = player.getLocation().clone().add(0, 0.2, -0.5);
                player.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0.01, SPEED_DUST);
            }
        }, 40L, 40L);
    }

    private boolean setAttackSpeed(Player player, double value) {
        AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute == null) {
            return false;
        }
        savedAttackSpeed.putIfAbsent(player.getUniqueId(), attribute.getBaseValue());
        attribute.setBaseValue(value);
        return true;
    }

    private void restoreAttackSpeed(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Double previous = savedAttackSpeed.remove(uuid);
        attribute.setBaseValue(previous == null ? 4.0 : previous);
    }

    private long blurCooldownForEnergy(int energy) {
        return switch (energy) {
            case 1 -> FormatUtil.toMilliseconds(1, 50);
            case 2 -> FormatUtil.toMilliseconds(1, 45);
            case 3 -> FormatUtil.toMilliseconds(1, 40);
            case 4 -> FormatUtil.toMilliseconds(1, 35);
            default -> FormatUtil.toMilliseconds(1, 30);
        };
    }

    private long speedStormCooldownForEnergy(int energy) {
        return switch (energy) {
            case 1 -> FormatUtil.toMilliseconds(2, 50);
            case 2 -> FormatUtil.toMilliseconds(2, 45);
            case 3 -> FormatUtil.toMilliseconds(2, 40);
            case 4 -> FormatUtil.toMilliseconds(2, 35);
            default -> FormatUtil.toMilliseconds(2, 30);
        };
    }

    private void stun(LivingEntity entity, int seconds) {
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, seconds * 20, 4, false, false));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, seconds * 20, 2, false, false));
    }

    private boolean isSpeedGem(ItemStack item) {
        return GemUtil.isGem(item) && GemUtil.getGem(item) == GemType.SPEED;
    }

    private boolean isSpeedTier2WithEnergy(ItemStack item) {
        return isSpeedGem(item)
            && GemUtil.getTier(item) == GemTier.TIER_2
            && GemUtil.getEnergyLevel(item) > 0;
    }

    private boolean isSwordAxeOrAir(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return true;
        }
        String type = item.getType().name();
        return type.endsWith("_SWORD") || type.endsWith("_AXE");
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

    private boolean isDisabled(Player player) {
        // Cross-gem disable state is still being centralized.
        return false;
    }
}
