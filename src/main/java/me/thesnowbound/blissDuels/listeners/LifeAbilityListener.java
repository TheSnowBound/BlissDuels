package me.thesnowbound.blissDuels.listeners;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.gem.GemTier;
import me.thesnowbound.blissDuels.gem.GemType;
import me.thesnowbound.blissDuels.util.AbilityParticleUtil;
import me.thesnowbound.blissDuels.util.ColorUtil;
import me.thesnowbound.blissDuels.util.FormatUtil;
import me.thesnowbound.blissDuels.util.GemUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Life gem conversion slice based on reference/Gems/life.sk.
 */
public class LifeAbilityListener implements Listener {
    private static final Particle.DustOptions LIFE_DUST = new Particle.DustOptions(Color.fromRGB(254, 4, 180), 1.0f);

    private final BlissDuels plugin;

    private final Map<UUID, Long> lifeDoubleClickWindow = new HashMap<>();
    private final Set<UUID> heartLocked = new HashSet<>();
    private final Map<UUID, Double> lockedMaxHealth = new HashMap<>();

    private final Set<UUID> lifeBlessed = new HashSet<>();
    private final Map<UUID, Double> blessedOriginalHealth = new HashMap<>();

    public LifeAbilityListener(BlissDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler()
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();

        Player player = event.getPlayer();

        // Green thumb passive (offhand life, sneaking, energy > 1).
        if (action.isRightClick()
            && player.isSneaking()
            && hasLifeWithEnergy(player.getInventory().getItemInOffHand(), 2)) {
            Block targetBlock = event.getClickedBlock();
            if (targetBlock == null) {
                targetBlock = player.getTargetBlockExact(5);
            }
            if (targetBlock != null && !isGroundBlockExcluded(targetBlock.getType())) {
                targetBlock.applyBoneMeal(org.bukkit.block.BlockFace.UP);
            }
        }

        if (action.isLeftClick()) {
            tryHeartDrainer(player);
            return;
        }

        if (!action.isRightClick()) {
            return;
        }

        // Life Circle (targeted if entity in sight, otherwise self radial).
        tryLifeCircle(player);

        // Vitality Vortex remains a block-click exception.
        if (action == Action.RIGHT_CLICK_BLOCK) {
            tryVitalityVortex(player);
        }
    }

    @EventHandler( priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        // Radiant fist passive bonus damage to undead from offhand life.
        applyRadiantFistBonus(attacker, victim, event);

        // HeartLock single-target on hit (held life gem tier2).
        ItemStack held = attacker.getInventory().getItemInMainHand();
        if (!isLifeTier2WithEnergy(held)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(attacker, "HeartLock")) {
            return;
        }
        if (isTrusted(attacker, victim)) {
            return;
        }

        if (victim.getAttribute(Attribute.MAX_HEALTH) == null) {
            return;
        }
        if (victim.getAttribute(Attribute.MAX_HEALTH).getBaseValue() <= 3.0) {
            return;
        }

        int energy = GemUtil.getEnergyLevel(held);
        plugin.getCooldownManager().setCooldown(attacker, "HeartLock", heartLockCooldown(energy));

        UUID victimId = victim.getUniqueId();
        heartLocked.add(victimId);
        AttributeInstance max = victim.getAttribute(Attribute.MAX_HEALTH);
        lockedMaxHealth.put(victimId, max.getBaseValue());

        max.setBaseValue(Math.max(2.0, victim.getHealth()));
        AbilityParticleUtil.drawAbilityLine(attacker, victim, org.bukkit.Color.fromRGB(254, 4, 180), true);
        attacker.sendMessage(ColorUtil.color("<##FE04B4> Heart Lock used on " + safeName(victim)));
        if (victim instanceof Player vp) {
            vp.sendMessage(ColorUtil.color("<##FE04B4> Your heart count has been locked for 20 seconds"));
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> restoreHeartLock(victim), 20L * 20L);
    }

    @EventHandler()
    public void onGenericDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        // Wither immunity passive if held/offhand life and energy > 3.
        if ((event.getCause() == EntityDamageEvent.DamageCause.WITHER || event.getCause() == EntityDamageEvent.DamageCause.MAGIC)
            && (hasLifeWithEnergy(victim.getInventory().getItemInMainHand(), 4)
            || hasLifeWithEnergy(victim.getInventory().getItemInOffHand(), 4))) {
            event.setCancelled(true);
        }
    }

    @EventHandler()
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack consumed = event.getItem();
        ItemStack offhand = player.getInventory().getItemInOffHand();

        if (!hasLifeWithEnergy(offhand, 3)) {
            return;
        }

        Material type = consumed.getType();
        if (type == Material.GOLDEN_APPLE) {
            player.removePotionEffect(PotionEffectType.ABSORPTION);
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 60, 1, false, false));
            player.setSaturation(20f);
            return;
        }

        if (type == Material.ENCHANTED_GOLDEN_APPLE) {
            player.removePotionEffect(PotionEffectType.ABSORPTION);
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 60, 4, false, false));
            player.setSaturation(20f);
            return;
        }

        // Non-gapples gain extra saturation.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            GemTier tier = GemUtil.getTier(offhand);
            float add = tier == GemTier.TIER_2 ? player.getSaturation() : player.getSaturation() / 2f;
            player.setSaturation(Math.min(20f, player.getSaturation() + add));
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        restoreHeartLock(player);
        restoreBlessing(player);
    }

    private void tryHeartDrainer(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isLifeTier2WithEnergy(held)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(player, "HeartLock")) {
            return;
        }

        int energy = GemUtil.getEnergyLevel(held);
        double radius = switch (energy) {
            case 1, 2 -> 1.5;
            case 3, 4 -> 2.5;
            default -> 3.0;
        };

        plugin.getCooldownManager().setCooldown(player, "HeartLock", heartLockCooldown(energy));
        player.sendMessage(ColorUtil.color("<##FE04B4> Heart Drainer activated"));
        drawLifeRing(player.getLocation(), radius);

        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || living.equals(player)) {
                continue;
            }
            if (isTrusted(player, living)) {
                continue;
            }

            AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
            if (max == null) {
                continue;
            }
            double original = max.getBaseValue();
            double reduced = Math.max(2.0, original - 2.0);
            max.setBaseValue(reduced);
            heartLocked.add(living.getUniqueId());
            lockedMaxHealth.put(living.getUniqueId(), original);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> restoreHeartLock(living), 20L * 60L);
        }
    }

    private void tryLifeCircle(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isLifeTier2WithEnergy(held)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(player, "LifeCircle")) {
            return;
        }

        LivingEntity target = getTargetLiving(player, 5.0);
        int energy = GemUtil.getEnergyLevel(held);
        plugin.getCooldownManager().setCooldown(player, "LifeCircle", lifeCircleCooldown(energy));

        if (target != null && target != player) {
            castTargetedLifeCircle(player, target, energy);
        } else {
            castSelfLifeCircle(player, energy);
        }
    }

    private void castTargetedLifeCircle(Player player, LivingEntity target, int energy) {
        double radius = energy >= 2 ? 2.0 : 1.0;
        player.sendMessage(ColorUtil.color("<##FE04B4> Privilege Precinct activated"));

        blessEntity(player, player, 5.0, 90L * 20L);
        drawLifeRing(target.getLocation(), radius + 1.0);

        for (Entity nearby : target.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity ally) || ally.equals(player)) {
                continue;
            }
            if (!isTrusted(player, ally)) {
                continue;
            }
            blessEntity(player, ally, 4.0, 90L * 20L);
        }
    }

    private void castSelfLifeCircle(Player player, int energy) {
        double radius = switch (energy) {
            case 1 -> 1.5;
            case 2, 3 -> 2.5;
            default -> 3.5;
        };

        player.sendMessage(ColorUtil.color("<##FE04B4> Life Circle activated"));
        drawLifeRing(player.getLocation(), radius);

        // Armour refresh + periodic pulse.
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 12 * 5, 0, false, false));
        blessEntity(player, player, 4.0, 12L * 5L);

        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || living.equals(player)) {
                continue;
            }

            if (isTrusted(player, living)) {
                blessEntity(player, living, 4.0, 10L * 20L);
            } else {
                debuffEntityHealth(living, 4.0, 10L * 20L);
            }
        }
    }

    private void tryVitalityVortex(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack held = player.getInventory().getItemInMainHand();

        if (!isLifeGem(offhand) || GemUtil.getEnergyLevel(offhand) <= 0) {
            return;
        }
        if (!isSwordAxeOrAir(held)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long expires = lifeDoubleClickWindow.getOrDefault(uuid, 0L);
        if (now > expires) {
            lifeDoubleClickWindow.put(uuid, now + 200L);
            return;
        }
        lifeDoubleClickWindow.remove(uuid);

        if (!plugin.getCooldownManager().isCooldownReady(player, "VitalityVortex")) {
            return;
        }

        plugin.getCooldownManager().setCooldown(player, "VitalityVortex", FormatUtil.toMilliseconds(0, 35));
        player.sendMessage(ColorUtil.color("<##FE04B4> Vitality Vortex activated"));

        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 60, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 15 * 20, 0, false, false));

        Location center = player.getLocation().clone();
        new BukkitRunnable() {
            int loops = 0;

            @Override
            public void run() {
                if (!player.isOnline() || loops >= 120) {
                    cancel();
                    return;
                }

                drawLifeRing(center, 5.0);
                for (int i = 0; i < 4; i++) {
                    Location point = center.clone().add(
                        (Math.random() - 0.5) * 8,
                        0.5,
                        (Math.random() - 0.5) * 8
                    );
                    point.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, point, 2, 0.1, 0.1, 0.1, 0.01);
                }

                loops++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void applyRadiantFistBonus(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        ItemStack offhand = attacker.getInventory().getItemInOffHand();
        if (!isLifeGem(offhand) || GemUtil.getEnergyLevel(offhand) <= 4) {
            return;
        }

        if (!isUndead(victim)) {
            return;
        }

        event.getEntity().getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.01, LIFE_DUST);

        if (GemUtil.getTier(offhand) == GemTier.TIER_2) {
            event.setDamage(event.getDamage() * 2.0);
        } else {
            event.setDamage(event.getDamage() * 1.5);
        }
    }

    private boolean isUndead(LivingEntity entity) {
        String type = entity.getType().name();
        return type.contains("WITHER")
            || type.contains("ZOMBIE")
            || type.contains("SKELETON")
            || type.contains("STRAY")
            || type.contains("HUSK")
            || type.contains("DROWNED")
            || type.contains("PHANTOM")
            || type.contains("ZOGLIN")
            || type.contains("PIGLIN");
    }

    private void blessEntity(Player source, LivingEntity target, double hearts, long ticks) {
        AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) {
            return;
        }

        UUID id = target.getUniqueId();
        if (!lifeBlessed.contains(id)) {
            blessedOriginalHealth.put(id, max.getBaseValue());
        }
        lifeBlessed.add(id);

        max.setBaseValue(max.getBaseValue() + hearts);
        if (target instanceof Player player) {
            player.sendMessage(ColorUtil.color("<##FE04B4> You received additional hearts from " + source.getName()));
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> restoreBlessing(target), ticks);
    }

    private void debuffEntityHealth(LivingEntity target, double hearts, long ticks) {
        AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) {
            return;
        }

        UUID id = target.getUniqueId();
        if (!lifeBlessed.contains(id)) {
            blessedOriginalHealth.put(id, max.getBaseValue());
        }
        lifeBlessed.add(id);

        max.setBaseValue(Math.max(2.0, max.getBaseValue() - hearts));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> restoreBlessing(target), ticks);
    }

    private void restoreHeartLock(LivingEntity entity) {
        UUID id = entity.getUniqueId();
        if (!heartLocked.remove(id)) {
            return;
        }
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) {
            return;
        }

        Double original = lockedMaxHealth.remove(id);
        if (original != null) {
            max.setBaseValue(original);
        }
    }

    private void restoreBlessing(LivingEntity entity) {
        UUID id = entity.getUniqueId();
        if (!lifeBlessed.remove(id)) {
            return;
        }
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) {
            return;
        }

        Double original = blessedOriginalHealth.remove(id);
        if (original != null) {
            max.setBaseValue(original);
        }
    }

    private LivingEntity getTargetLiving(Player player, double range) {
        Entity target = player.getTargetEntity((int) Math.ceil(range));
        if (target instanceof LivingEntity living && !living.equals(player)) {
            return living;
        }
        return null;
    }

    private void drawLifeRing(Location center, double radius) {
        for (int i = 0; i < 72; i++) {
            double angle = Math.toRadians(i * 5);
            Location point = center.clone().add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius);
            point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0.01, LIFE_DUST);
            point.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, point, 1, 0, 0, 0, 0.01);
        }
        center.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9f, 1.4f);
    }

    private long heartLockCooldown(int energy) {
        return switch (energy) {
            case 1 -> FormatUtil.toMilliseconds(2, 35);
            case 2 -> FormatUtil.toMilliseconds(2, 30);
            case 3 -> FormatUtil.toMilliseconds(2, 20);
            case 4 -> FormatUtil.toMilliseconds(2, 10);
            default -> FormatUtil.toMilliseconds(2, 0);
        };
    }

    private long lifeCircleCooldown(int energy) {
        return switch (energy) {
            case 1 -> FormatUtil.toMilliseconds(3, 35);
            case 2 -> FormatUtil.toMilliseconds(3, 30);
            case 3 -> FormatUtil.toMilliseconds(3, 20);
            case 4 -> FormatUtil.toMilliseconds(3, 10);
            default -> FormatUtil.toMilliseconds(3, 0);
        };
    }

    private boolean hasLifeWithEnergy(ItemStack item, int minEnergy) {
        return isLifeGem(item) && GemUtil.getEnergyLevel(item) >= minEnergy;
    }

    private boolean isLifeTier2WithEnergy(ItemStack item) {
        return isLifeGem(item) && GemUtil.getTier(item) == GemTier.TIER_2 && GemUtil.getEnergyLevel(item) > 0;
    }

    private boolean isLifeGem(ItemStack item) {
        return GemUtil.isGem(item) && GemUtil.getGem(item) == GemType.LIFE;
    }

    private boolean isGroundBlockExcluded(Material material) {
        return material == Material.GRASS_BLOCK
            || material == Material.SAND
            || material == Material.GRAVEL
            || material == Material.MOSS_BLOCK;
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
}
