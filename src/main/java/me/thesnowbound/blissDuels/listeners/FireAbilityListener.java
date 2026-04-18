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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fire gem conversion slice: passives + Fireball + Meteor Shower + Cozy Campfire.
 */
public class FireAbilityListener implements Listener {
    private static final String FIREBALL_CHARGE_META = "bliss_fireball_charge";
    private static final String FIREBALL_OWNER_META = "bliss_fireball_owner";
    private static final Particle.DustOptions FIRE_DUST = new Particle.DustOptions(Color.fromRGB(254, 129, 32), 1.1f);
    private static final Particle.DustOptions CAMPFIRE_DUST = new Particle.DustOptions(Color.fromRGB(255, 168, 60), 1.0f);

    private final BlissDuels plugin;
    private final NamespacedKey fireballChargeKey;
    private final NamespacedKey fireballOwnerKey;

    private final Map<UUID, Boolean> chargingFireball = new HashMap<>();
    private final Map<UUID, Double> fireballCharge = new HashMap<>();
    private final Map<UUID, BossBar> fireballBars = new HashMap<>();
    private final Map<UUID, BukkitTask> fireballTasks = new HashMap<>();

    private final List<CampfireZone> campfireZones = new ArrayList<>();
    private final Map<UUID, Long> lastRightClick = new HashMap<>();
    private final Map<UUID, Boolean> crispActive = new HashMap<>();
    private final Map<UUID, Map<Location, Material>> crispChangedBlocks = new HashMap<>();

    public FireAbilityListener(BlissDuels plugin) {
        this.plugin = plugin;
        this.fireballChargeKey = new NamespacedKey(plugin, FIREBALL_CHARGE_META);
        this.fireballOwnerKey = new NamespacedKey(plugin, FIREBALL_OWNER_META);
        startCampfireTick();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        // Passive: 15% ignite on hit when fire gem is in offhand and energy > 2.
        if (isFireGem(attacker.getInventory().getItemInOffHand())
            && GemUtil.getEnergyLevel(attacker.getInventory().getItemInOffHand()) > 2
            && event.getEntity() != attacker
            && ThreadLocalRandom.current().nextInt(100) < 15) {
            event.getEntity().setFireTicks(Math.max(event.getEntity().getFireTicks(), 80));
        }

        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        // Power: Meteor Shower trigger on hit with held fire gem tier 2.
        if (!isFireGem(attacker.getInventory().getItemInMainHand())) {
            return;
        }
        if (GemUtil.getTier(attacker.getInventory().getItemInMainHand()) != GemTier.TIER_2) {
            return;
        }
        if (GemUtil.getEnergyLevel(attacker.getInventory().getItemInMainHand()) <= 0) {
            return;
        }
        if (Boolean.TRUE.equals(chargingFireball.get(attacker.getUniqueId()))) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(attacker, "Fireball")) {
            return;
        }
        if (isTrusted(attacker, victim)) {
            return;
        }

        startMeteorShower(attacker, victim);
    }

    @EventHandler()
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) {
            return;
        }
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }

        // Passive: 15% chance flaming arrows when fire gem offhand and energy > 3.
        if (isFireGem(shooter.getInventory().getItemInOffHand())
            && GemUtil.getEnergyLevel(shooter.getInventory().getItemInOffHand()) > 3
            && ThreadLocalRandom.current().nextInt(100) < 15) {
            arrow.setFireTicks(120);
        }
    }

    @EventHandler()
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        Action action = event.getAction();

        if (action.isLeftClick()) {
            handleFireballLeftClick(player);
            return;
        }

        // Handle right-click (air or block): Crisp uses offhand fire gem on right-click (air), Campfire is block-only.
        if (action.isRightClick() && action != Action.RIGHT_CLICK_BLOCK) {
            // try Crisp activation from offhand
            if (tryCrisp(event)) {
                return;
            }
            // otherwise ignore other right-click air
            return;
        }

        // Cozy Campfire stays block-only by design.
        ItemStackAccessor held = new ItemStackAccessor(player.getInventory().getItemInMainHand());
        if (!held.isFireTier2WithEnergy()) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(player, "Campfire")) {
            return;
        }

        Block target = event.getClickedBlock();
        if (target == null) {
            return;
        }

        Block above = target.getLocation().add(0, 1, 0).getBlock();
        if (!above.getType().isAir()) {
            return;
        }

        event.setCancelled(true);
        above.setType(Material.SOUL_CAMPFIRE);

        int energy = held.energy();
        double radius = switch (energy) {
            case 1 -> 2.0;
            case 2 -> 3.0;
            case 3 -> 4.0;
            case 4 -> 5.0;
            default -> 5.5;
        };

        long cooldown = switch (energy) {
            case 1 -> FormatUtil.toMilliseconds(1, 20);
            case 2 -> FormatUtil.toMilliseconds(1, 15);
            case 3 -> FormatUtil.toMilliseconds(1, 10);
            case 4 -> FormatUtil.toMilliseconds(1, 5);
            default -> FormatUtil.toMilliseconds(1, 0);
        };

        plugin.getCooldownManager().setCooldown(player, "Campfire", cooldown);

        Location campfireLoc = above.getLocation().toCenterLocation();
        ArmorStand timer = spawnCampfireTimer(campfireLoc, System.currentTimeMillis() + FormatUtil.toMilliseconds(10, 0));
        campfireZones.add(new CampfireZone(player.getUniqueId(), campfireLoc, radius, System.currentTimeMillis() + FormatUtil.toMilliseconds(10, 0), timer.getUniqueId()));

        player.sendMessage(ColorUtil.color("<##FE8120> You used Cozy Campfire"));
        player.playSound(player.getLocation(), Sound.BLOCK_CAMPFIRE_CRACKLE, 1.0f, 1.2f);
    }

    @EventHandler()
    public void onFireballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball)) {
            return;
        }

        PersistentDataContainer data = fireball.getPersistentDataContainer();
        Double chargeValue = data.get(fireballChargeKey, PersistentDataType.DOUBLE);
        if (chargeValue == null) {
            return;
        }

        float charge = chargeValue.floatValue();
        float yield = mapExplosionYield(charge);
        fireball.getWorld().createExplosion(fireball.getLocation(), yield, false, true);

        // Minor scaled extra damage around impact for closer Skript feel.
        double damage = mapExplosionDamage(charge);
        for (Entity nearby : fireball.getNearbyEntities(4.0, 4.0, 4.0)) {
            if (!(nearby instanceof LivingEntity living)) {
                continue;
            }

            String ownerRaw = data.get(fireballOwnerKey, PersistentDataType.STRING);
            if (ownerRaw != null) {
                UUID ownerId = UUID.fromString(ownerRaw);
                Player owner = Bukkit.getPlayer(ownerId);
                if (owner != null && owner.equals(living)) {
                    continue;
                }
                if (owner != null && isTrusted(owner, living)) {
                    continue;
                }
            }
            living.damage(damage);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopFireballCharge(event.getPlayer());

        UUID uuid = event.getPlayer().getUniqueId();
        cleanupCampfireZones(zone -> zone.owner().equals(uuid));
    }

    @EventHandler
    public void onCampfireBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.SOUL_CAMPFIRE) {
            return;
        }
        Location broken = event.getBlock().getLocation().toCenterLocation();
        cleanupCampfireZones(zone -> zone.location().distanceSquared(broken) <= 0.05);
    }

    private void handleFireballLeftClick(Player player) {
        ItemStackAccessor held = new ItemStackAccessor(player.getInventory().getItemInMainHand());
        if (!held.isFireTier2WithEnergy()) {
            return;
        }

        UUID uuid = player.getUniqueId();

        // First click starts charging.
        if (!Boolean.TRUE.equals(chargingFireball.get(uuid))) {
            if (!plugin.getCooldownManager().isCooldownReady(player, "Fireball")) {
                return;
            }
            startFireballCharge(player);
            return;
        }

        // Second click releases fireball.
        releaseFireball(player, held.energy());
    }

    private void startFireballCharge(Player player) {
        UUID uuid = player.getUniqueId();
        chargingFireball.put(uuid, true);
        fireballCharge.put(uuid, 0.0);

        BossBar bar = Bukkit.createBossBar("Fireball Power", BarColor.RED, BarStyle.SOLID);
        bar.setProgress(0.0);
        bar.addPlayer(player);
        fireballBars.put(uuid, bar);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !Boolean.TRUE.equals(chargingFireball.get(uuid))) {
                return;
            }

            double add = isNetherLikeBlock(player.getLocation().subtract(0, 1, 0).getBlock().getType()) ? 4.66 : 2.77;
            double next = Math.min(100.0, fireballCharge.getOrDefault(uuid, 0.0) + add);
            fireballCharge.put(uuid, next);
            bar.setProgress(Math.min(1.0, next / 100.0));

            Location fx = player.getLocation().add(0, 1, 0);
            player.getWorld().spawnParticle(Particle.DUST, fx, 10, 0.4, 0.4, 0.4, 0.01, FIRE_DUST);
            player.getWorld().spawnParticle(Particle.SMOKE, fx, 4, 0.5, 0.5, 0.5, 0.01);
        }, 1L, 20L);

        fireballTasks.put(uuid, task);
        player.sendMessage(ColorUtil.color("<##FE8120> Charging fireball"));
    }

    private void releaseFireball(Player player, int energy) {
        UUID uuid = player.getUniqueId();
        double charge = fireballCharge.getOrDefault(uuid, 0.0);

        long cooldown = switch (Math.min(energy, 10)) {
            case 1, 2 -> FormatUtil.toMilliseconds(6, 30);
            case 3, 4 -> FormatUtil.toMilliseconds(5, 30);
            case 5, 6 -> FormatUtil.toMilliseconds(4, 30);
            case 7, 8 -> FormatUtil.toMilliseconds(3, 30);
            default -> FormatUtil.toMilliseconds(2, 30);
        };

        plugin.getCooldownManager().setCooldown(player, "Fireball", cooldown);

        Vector direction = player.getLocation().getDirection().normalize();
        Fireball projectile = player.getWorld().spawn(player.getEyeLocation().add(direction.multiply(1.5)), Fireball.class);
        projectile.setShooter(player);
        projectile.setIsIncendiary(false);
        projectile.setVelocity(direction.multiply(2.2));
        projectile.setYield(0f);
        projectile.getPersistentDataContainer().set(fireballChargeKey, PersistentDataType.DOUBLE, charge);
        projectile.getPersistentDataContainer().set(fireballOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());

        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);
        player.sendMessage(ColorUtil.color("<##FE8120> Fireball launched"));

        stopFireballCharge(player);
    }

    private void stopFireballCharge(Player player) {
        UUID uuid = player.getUniqueId();
        chargingFireball.remove(uuid);
        fireballCharge.remove(uuid);

        BukkitTask task = fireballTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        BossBar bar = fireballBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private void startMeteorShower(Player attacker, LivingEntity victim) {
        int energy = GemUtil.getEnergyLevel(attacker.getInventory().getItemInMainHand());
        long cooldown = switch (Math.min(energy, 10)) {
            case 1, 2 -> FormatUtil.toMilliseconds(5, 30);
            case 3, 4, 5 -> FormatUtil.toMilliseconds(4, 25);
            case 6, 7 -> FormatUtil.toMilliseconds(3, 45);
            case 8, 9 -> FormatUtil.toMilliseconds(2, 30);
            default -> FormatUtil.toMilliseconds(1, 45);
        };
        plugin.getCooldownManager().setCooldown(attacker, "Fireball", cooldown);

        attacker.sendMessage(ColorUtil.color("<##FE8120> Meteor Shower summoned"));
        if (victim instanceof Player victimPlayer) {
            victimPlayer.sendMessage(ColorUtil.color("<##FE8120> Meteor Shower is raining on you"));
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!victim.isValid() || victim.isDead() || ticks >= 60) {
                    cancel();
                    return;
                }

                Location base = victim.getLocation().add(
                    ThreadLocalRandom.current().nextDouble(-2.0, 2.0),
                    10.0,
                    ThreadLocalRandom.current().nextDouble(-2.0, 2.0)
                );

                Fireball meteor = victim.getWorld().spawn(base, Fireball.class);
                meteor.setIsIncendiary(false);
                meteor.setVelocity(new Vector(0, -1, 0));
                meteor.setYield(1.2f);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void startCampfireTick() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            Iterator<CampfireZone> iterator = campfireZones.iterator();

            while (iterator.hasNext()) {
                CampfireZone zone = iterator.next();
                Block block = zone.location().getBlock();
                ArmorStand timer = getCampfireTimer(zone.timerId());

                if (zone.expiresAt() <= now || block.getType() != Material.SOUL_CAMPFIRE) {
                    removeCampfireZone(zone, true);
                    iterator.remove();
                    continue;
                }

                if (timer != null) {
                    long remaining = Math.max(0L, zone.expiresAt() - now);
                    timer.setCustomName(ColorUtil.color("&a" + formatCampfireTimer(remaining)));
                }

                Location center = zone.location().clone().add(0.0, 0.7, 0.0);
                zone.location().getWorld().spawnParticle(Particle.DUST, center, 18, zone.radius(), 0.2, zone.radius(), 0.01, CAMPFIRE_DUST);
                zone.location().getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center, 6, zone.radius() * 0.3, 0.25, zone.radius() * 0.3, 0.01);

                Player owner = Bukkit.getPlayer(zone.owner());
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.getWorld().equals(zone.location().getWorld())) {
                        continue;
                    }
                    if (player.getLocation().distanceSquared(zone.location().toCenterLocation()) > zone.radius() * zone.radius()) {
                        continue;
                    }
                    if (!player.getUniqueId().equals(zone.owner()) && (owner == null || !isTrusted(owner, player))) {
                        continue;
                    }

                    double max = getMaxHealth(player);
                    // Skript parity: Cozy Campfire heals 2 hearts per pulse.
                    player.setHealth(Math.min(max, player.getHealth() + 4.0));
                    if (player.getFoodLevel() < 10) {
                        player.setFoodLevel(Math.min(20, player.getFoodLevel() + 4));
                    }

                    Location healFx = player.getLocation().add(0, 1, 0);
                    player.getWorld().spawnParticle(Particle.DUST, healFx, 5, 0.3, 0.4, 0.3, 0.0, FIRE_DUST);
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, healFx, 5, 0.3, 0.4, 0.3, 0.0);
                }
            }
        }, 5L, 5L);
    }

    private void cleanupCampfireZones(java.util.function.Predicate<CampfireZone> predicate) {
        Iterator<CampfireZone> iterator = campfireZones.iterator();
        while (iterator.hasNext()) {
            CampfireZone zone = iterator.next();
            if (!predicate.test(zone)) {
                continue;
            }
            removeCampfireZone(zone, false);
            iterator.remove();
        }
    }

    private void removeCampfireZone(CampfireZone zone, boolean destroyBlock) {
        if (destroyBlock && zone.location().getBlock().getType() == Material.SOUL_CAMPFIRE) {
            zone.location().getBlock().setType(Material.AIR);
        }
        ArmorStand timer = getCampfireTimer(zone.timerId());
        if (timer != null) {
            timer.remove();
        }
    }

    private ArmorStand spawnCampfireTimer(Location campfireLoc, long expiresAt) {
        ArmorStand stand = campfireLoc.getWorld().spawn(campfireLoc.clone().add(0.0, 0.25, 0.0), ArmorStand.class);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setInvulnerable(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName(ColorUtil.color("&a" + formatCampfireTimer(Math.max(0L, expiresAt - System.currentTimeMillis()))));
        return stand;
    }

    private ArmorStand getCampfireTimer(UUID timerId) {
        if (timerId == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(timerId);
        if (entity instanceof ArmorStand stand && stand.isValid()) {
            return stand;
        }
        return null;
    }

    private String formatCampfireTimer(long remainingMillis) {
        long totalSeconds = Math.max(0L, remainingMillis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes > 0L) {
            return seconds > 0L ? minutes + "m " + seconds + "s" : minutes + "m";
        }
        return seconds > 0L ? seconds + "s" : "Ready!";
    }

    private double getMaxHealth(Player player) {
        org.bukkit.attribute.AttributeInstance health = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return health == null ? 20.0 : health.getValue();
    }

    private float mapExplosionYield(double charge) {
        if (charge < 10) return 3.0f;
        if (charge < 20) return 3.5f;
        if (charge < 30) return 4.0f;
        if (charge < 40) return 4.5f;
        if (charge < 50) return 5.0f;
        if (charge < 60) return 5.5f;
        if (charge < 70) return 6.0f;
        if (charge < 80) return 7.5f;
        return 8.0f;
    }

    private double mapExplosionDamage(double charge) {
        if (charge < 10) return 1.5;
        if (charge < 20) return 2.0;
        if (charge < 30) return 2.5;
        if (charge < 40) return 3.0;
        if (charge < 50) return 3.5;
        if (charge < 70) return 4.0;
        if (charge < 80) return 4.5;
        if (charge < 90) return 5.0;
        return 5.5;
    }

    private boolean isNetherLikeBlock(Material block) {
        return block == Material.OBSIDIAN
            || block == Material.FIRE
            || block == Material.LAVA
            || block == Material.NETHERRACK
            || block == Material.MAGMA_BLOCK;
    }

    private boolean isFireGem(org.bukkit.inventory.ItemStack item) {
        return GemUtil.isGem(item) && GemUtil.getGem(item) == GemType.FIRE;
    }

    private boolean isTrusted(Player source, LivingEntity target) {
        return plugin.getTrustManager().isTrusted(source, target);
    }

    private record CampfireZone(UUID owner, Location location, double radius, long expiresAt, UUID timerId) {
    }

    private static final class ItemStackAccessor {
        private final org.bukkit.inventory.ItemStack item;

        private ItemStackAccessor(org.bukkit.inventory.ItemStack item) {
            this.item = item;
        }

        private boolean isFireTier2WithEnergy() {
            return GemUtil.isGem(item)
                && GemUtil.getGem(item) == GemType.FIRE
                && GemUtil.getTier(item) == GemTier.TIER_2
                && GemUtil.getEnergyLevel(item) > 0;
        }

        private int energy() {
            return GemUtil.getEnergyLevel(item);
        }
    }

    private boolean isSwordAxeOrAir(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return true;
        }
        String name = item.getType().name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE");
    }

    private boolean isDisabled(Player player) {
        return false;
    }

    private boolean tryCrisp(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isDisabled(player)) return false;

        org.bukkit.inventory.ItemStack off = player.getInventory().getItemInOffHand();
        if (!isFireGem(off)) return false;
        int energy = GemUtil.getEnergyLevel(off);
        if (energy <= 0) return false;

        org.bukkit.inventory.ItemStack held = player.getInventory().getItemInMainHand();
        if (!isSwordAxeOrAir(held)) return false;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastRightClick.getOrDefault(uuid, 0L);
        // update timestamp for next click
        lastRightClick.put(uuid, now);

        // require quick double-right-click within 200ms
        if (now - last > 200L) {
            return false;
        }

        // check cooldown
        if (!plugin.getCooldownManager().isCooldownReady(player, "Crisp")) {
            int secs = plugin.getCooldownManager().getRemainingSeconds(player, "Crisp");
            player.sendMessage(ColorUtil.color("<##FE8120>🔮 &f🥾<##FF686F>Crisp is on cooldown for " + secs + "s"));
            return true;
        }

        // small activation gate
        plugin.getCooldownManager().setCooldown(player, "Crisp", FormatUtil.toMilliseconds(0, 45));
        event.setCancelled(true);
        startCrispRoutine(player, energy);
        return true;
    }

    private void startCrispRoutine(Player player, int energy) {
        UUID uuid = player.getUniqueId();
        if (Boolean.TRUE.equals(crispActive.get(uuid))) return;
        crispActive.put(uuid, true);

        player.sendMessage(ColorUtil.color("<##FE8120>🔮<##FF686F> You used &f🥾<##FE8120>Crisp Ability"));
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);

        // Reduced and surface-focused block changes to limit terrain churn and CPU usage.
        int radius = 5;
        Map<Location, Material> original = new HashMap<>();
        Location center = player.getLocation();
        // only modify a thin vertical band around the player
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location loc = center.clone().add(x, y, z);
                    Block b = loc.getBlock();
                    Material t = b.getType();

                    boolean transformed = false;
                    if (t == Material.GRASS_BLOCK || t == Material.DIRT) {
                        original.put(b.getLocation(), t);
                        b.setType(Material.NETHERRACK);
                        transformed = true;
                    } else if (t.name().endsWith("_LOG")) {
                        original.put(b.getLocation(), t);
                        b.setType(Material.CRIMSON_STEM);
                        transformed = true;
                    } else if (t.name().endsWith("_LEAVES")) {
                        original.put(b.getLocation(), t);
                        b.setType(Material.WARPED_WART_BLOCK);
                        transformed = true;
                    } else if (t == Material.TALL_GRASS || t == Material.SHORT_GRASS) {
                        original.put(b.getLocation(), t);
                        b.setType(Material.TWISTING_VINES);
                        transformed = true;
                    }

                    if (transformed) {
                        b.getWorld().spawnParticle(Particle.SMOKE, b.getLocation().add(0.5, 0.5, 0.5), 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }
        }

        crispChangedBlocks.put(uuid, original);

        // run pulses (90 pulses, every 4 ticks)
        new BukkitRunnable() {
            int pulses = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    restoreCrispBlocks(uuid);
                    crispActive.remove(uuid);
                    cancel();
                    return;
                }
                if (pulses >= 90) {
                    restoreCrispBlocks(uuid);
                    plugin.getCooldownManager().setCooldown(player, "Crisp", FormatUtil.toMilliseconds(0, 30));
                    crispActive.remove(uuid);
                    cancel();
                    return;
                }

                for (Entity ent : player.getNearbyEntities(5.0, 5.0, 5.0)) {
                    if (!(ent instanceof LivingEntity living)) continue;
                    if (living.equals(player)) continue;
                    if (isTrusted(player, living)) continue;
                    if (!(living instanceof ArmorStand)) continue;
                    living.damage(3.0);
                }

                Location fx = player.getLocation().add(0, 1, 0);
                player.getWorld().spawnParticle(Particle.DUST, fx, 30, 5.0, 0.5, 5.0, 0.01, FIRE_DUST);
                pulses++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private void restoreCrispBlocks(UUID uuid) {
        Map<Location, Material> original = crispChangedBlocks.remove(uuid);
        if (original == null) return;
        for (Map.Entry<Location, Material> e : original.entrySet()) {
            Location loc = e.getKey();
            try {
                Block b = loc.getBlock();
                Material orig = e.getValue();
                if (orig != null && b.getType() != orig) {
                    b.setType(orig);
                }
            } catch (Exception ignored) {
                // Defensive: ignore worlds/chunk unloads or other unexpected errors.
            }
        }
    }
}
