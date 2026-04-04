package me.thesnowbound.blissDuels.listeners;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.gem.GemTier;
import me.thesnowbound.blissDuels.gem.GemType;
import me.thesnowbound.blissDuels.util.ColorUtil;
import me.thesnowbound.blissDuels.util.FormatUtil;
import me.thesnowbound.blissDuels.util.GemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Astra gem conversion from Skript to Java.
 *
 * This listener intentionally owns Astra state so the logic can be ported 1:1
 * without leaking temporary variables across unrelated systems.
 */
public class AstraAbilityListener implements Listener {
    private static final String DAGGER_META_OWNER = "bliss_astra_dagger_owner";
    private static final Particle.DustOptions ASTRA_DUST = new Particle.DustOptions(Color.fromRGB(198, 154, 255), 1.0f);

    private final BlissDuels plugin;

    // Shared disable timer (script used {-Disabled::uuid}).
    private final Map<UUID, Integer> disabledSeconds = new HashMap<>();

    // Tracks entities already hit per Astral Void cast window.
    private final Map<UUID, Set<UUID>> astralWheelVictims = new HashMap<>();

    // Astral projection state.
    private final Set<UUID> astralProjectionActive = new HashSet<>();
    private final Map<UUID, Location> astralBodyLocation = new HashMap<>();
    private final Map<UUID, UUID> lastSpectatorTarget = new HashMap<>();

    // Dimensional drift state.
    private final Map<UUID, Long> doubleClickUntil = new HashMap<>();
    private final Set<UUID> driftActive = new HashSet<>();
    private final Set<UUID> driftNoFall = new HashSet<>();
    private final Set<UUID> driftJumping = new HashSet<>();
    private final Map<UUID, AbstractHorse> driftHorse = new HashMap<>();

    // Unbounded state.
    private final Set<UUID> unboundedActive = new HashSet<>();
    private final Map<UUID, UUID> unboundedVictim = new HashMap<>();

    // Phasing state.
    private final Set<UUID> phasingActive = new HashSet<>();

    // Soul capture state.
    private final Map<Location, EntityType> soulPool = new HashMap<>();
    private final Map<UUID, Deque<EntityType>> capturedSouls = new HashMap<>();
    private final Map<UUID, Integer> astraSpawningSeconds = new HashMap<>();

    // Phantom daggers state.
    private final Set<UUID> daggersActive = new HashSet<>();
    private final Map<UUID, Integer> daggerAmount = new HashMap<>();
    private final Map<UUID, BossBar> daggerBossBar = new HashMap<>();
    private final Map<UUID, Boolean> daggerShotCooldown = new HashMap<>();
    private final Map<UUID, List<ArmorStand>> daggerVisuals = new HashMap<>();
    private final Map<UUID, Set<UUID>> daggerVictims = new HashMap<>();

    public AstraAbilityListener(BlissDuels plugin) {
        this.plugin = plugin;
        startTickLoops();
    }

    private void startTickLoops() {
        // Matches the script's "every 1 second" decrement for disabled players.
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    disabledSeconds.compute(player.getUniqueId(), (uuid, value) -> {
                        if (value == null || value <= 0) {
                            return 0;
                        }
                        return value - 1;
                    });

                    Integer spawnTicks = astraSpawningSeconds.get(player.getUniqueId());
                    if (spawnTicks != null && spawnTicks > 0) {
                        astraSpawningSeconds.put(player.getUniqueId(), spawnTicks - 1);
                        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 0.5, 0), 20, 0.4, 0.4, 0.4, 0.01, ASTRA_DUST);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // Astral projection particles + blind in blocks + dagger bar drain.
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();

                    if (astralProjectionActive.contains(uuid) && player.getGameMode() == GameMode.SPECTATOR) {
                        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.01, ASTRA_DUST);

                        Material blockAbove = player.getLocation().add(0, 1, 0).getBlock().getType();
                        if (blockAbove == Material.STONE || blockAbove == Material.GRASS_BLOCK || blockAbove == Material.DIRT || blockAbove == Material.COBBLESTONE) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 3, false, false));
                        }

                        handleAstralSpookIfTargetChanged(player);
                    }

                    // Astral body visual while owner is projected.
                    if (astralProjectionActive.contains(uuid) && astralBodyLocation.containsKey(uuid)) {
                        Location body = astralBodyLocation.get(uuid);
                        body.getWorld().spawnParticle(Particle.DUST, body.clone().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.01, ASTRA_DUST);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 2L);

        // Drift horse respawn after jump.
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    if (!driftActive.contains(uuid) || driftJumping.contains(uuid)) {
                        continue;
                    }

                    if (!player.isInsideVehicle() && player.getLocation().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                        spawnDriftHorse(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        disabledSeconds.putIfAbsent(player.getUniqueId(), 0);
        daggerShotCooldown.putIfAbsent(player.getUniqueId(), false);
        // Script has explicit reveal on join.
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, player);
            player.showPlayer(plugin, other);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player quitter = event.getPlayer();
        UUID quitterId = quitter.getUniqueId();

        if (astralProjectionActive.remove(quitterId)) {
            astralBodyLocation.remove(quitterId);
        }

        if (unboundedVictim.containsValue(quitterId)) {
            for (UUID hunterId : new ArrayList<>(unboundedVictim.keySet())) {
                if (!quitterId.equals(unboundedVictim.get(hunterId))) {
                    continue;
                }
                Player hunter = Bukkit.getPlayer(hunterId);
                if (hunter != null) {
                    hunter.setGameMode(GameMode.SURVIVAL);
                    hunter.teleport(quitter.getLocation());
                    plugin.getCooldownManager().setCooldown(hunter, "Daggers", FormatUtil.toMilliseconds(4, 30));
                }
                unboundedVictim.remove(hunterId);
                unboundedActive.remove(hunterId);
            }
        }

        cleanupDaggerState(quitterId);
        cleanupDrift(quitterId);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onRightClickEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity target = event.getRightClicked();
        UUID uuid = player.getUniqueId();

        // Tagging while astral projecting.
        if (astralProjectionActive.contains(uuid) && player.getGameMode() == GameMode.SPECTATOR) {
            target.setGlowing(true);
            if (target instanceof LivingEntity living) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 20, 0, false, false));
            }
            player.sendMessage(ColorUtil.color("<##A01FFF> Tagged " + safeName(target)));
            return;
        }

        // Astral void (right click entity with held astra, tier 2).
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isAstraGem(held) || GemUtil.getTier(held) != GemTier.TIER_2 || GemUtil.getEnergyLevel(held) <= 0) {
            return;
        }

        if (isDisabled(player) || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (!plugin.getCooldownManager().isCooldownReady(player, "Astral")) {
            return;
        }

        setAstralCooldownByEnergy(player, GemUtil.getEnergyLevel(held), false);
        player.sendMessage(ColorUtil.color("<##A01FFF> Astral Void activated"));

        Location center = target.getLocation();
        double radius = 3.5;
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 60 || !player.isOnline()) {
                    astralWheelVictims.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                for (Player nearby : Bukkit.getOnlinePlayers()) {
                    if (nearby.equals(player)) {
                        continue;
                    }
                    if (!nearby.getWorld().equals(center.getWorld())) {
                        continue;
                    }
                    if (nearby.getLocation().distanceSquared(center) <= 49 && !isTrusted(player, nearby)) {
                        disabledSeconds.put(nearby.getUniqueId(), 2);
                    }
                }
                // domeparticles placeholder from Skript utility.
                center.getWorld().spawnParticle(Particle.DUST, center.clone().add(0, 2.5, 0), 40, 2.0, 2.0, 2.0, 0.01, ASTRA_DUST);

                Set<UUID> affected = astralWheelVictims.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>());
                for (Entity nearby : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                    if (!(nearby instanceof LivingEntity living) || nearby.equals(player)) {
                        continue;
                    }
                    if (affected.contains(living.getUniqueId())) {
                        continue;
                    }
                    affected.add(living.getUniqueId());
                    disabledSeconds.put(living.getUniqueId(), 2);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 40L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        Action action = event.getAction();

        // Left click exits astral projection and also handles dagger fire.
        if (action.isLeftClick()) {
            if (tryStopAstralProjection(player)) {
                return;
            }
            if (tryHandleDaggersLeftClick(player)) {
                return;
            }
        }

        // Right click can trigger astral projection and dimensional drift.
        if (action.isRightClick()) {
            tryActivateAstralProjection(player);
            tryActivateDimensionalDrift(player);
            trySpawnCapturedSoul(player, event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Soul capture pickup.
        if (hasAstraInOffhand(player) && GemUtil.getEnergyLevel(player.getInventory().getItemInOffHand()) > 2) {
            List<Location> toRemove = new ArrayList<>();
            for (Map.Entry<Location, EntityType> entry : soulPool.entrySet()) {
                Location soulLoc = entry.getKey();
                if (!soulLoc.getWorld().equals(player.getWorld())) {
                    continue;
                }
                if (player.getLocation().distanceSquared(soulLoc) > 1.5) {
                    continue;
                }
                Deque<EntityType> souls = capturedSouls.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
                if (souls.size() >= 2) {
                    souls.removeLast();
                }
                souls.addFirst(entry.getValue());
                toRemove.add(soulLoc);

                double newHealth = Math.min(player.getAttribute(Attribute.MAX_HEALTH).getValue(), player.getHealth() + 4.0);
                player.setHealth(newHealth);
                player.setSaturation(Math.min(20f, player.getSaturation() + 3f));
                break;
            }
            for (Location removeLoc : toRemove) {
                soulPool.remove(removeLoc);
            }
        }

        // If unbounded hunters drift out of target range, force stop.
        UUID hunterId = player.getUniqueId();
        if (unboundedActive.contains(hunterId)) {
            UUID victimId = unboundedVictim.get(hunterId);
            Player victim = victimId == null ? null : Bukkit.getPlayer(victimId);
            if (victim == null || !victim.isOnline()) {
                stopUnbounded(player);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Unbounded activation.
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player victim) {
            if (!daggersActive.contains(attacker.getUniqueId())
                && !isDisabled(attacker)
                && isAstraGem(attacker.getInventory().getItemInMainHand())
                && GemUtil.getTier(attacker.getInventory().getItemInMainHand()) == GemTier.TIER_2
                && GemUtil.getEnergyLevel(attacker.getInventory().getItemInMainHand()) > 0
                && plugin.getCooldownManager().isCooldownReady(attacker, "Daggers")) {

                event.setCancelled(true);
                startUnbounded(attacker, victim);
                return;
            }
        }

        // Spectating unbounded hunters pop back to survival when victim gets hit.
        if (event.getEntity() instanceof Player damagedVictim) {
            for (UUID hunterId : new HashSet<>(unboundedActive)) {
                Player hunter = Bukkit.getPlayer(hunterId);
                if (hunter == null || hunter.getGameMode() != GameMode.SPECTATOR) {
                    continue;
                }
                if (hunter.getLocation().distanceSquared(damagedVictim.getLocation()) <= 0.09) {
                    stopUnbounded(hunter);
                }
            }
        }

        // Dagger projectile hit damage.
        if (event.getDamager() instanceof Snowball snowball && event.getEntity() instanceof LivingEntity victim) {
            if (!snowball.hasMetadata(DAGGER_META_OWNER)) {
                return;
            }
            UUID ownerId = UUID.fromString(snowball.getMetadata(DAGGER_META_OWNER).getFirst().asString());
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null) {
                return;
            }
            event.setCancelled(true);
            applyDaggerDamage(victim, 2.5, owner);
            victim.setVelocity(victim.getLocation().toVector().subtract(snowball.getLocation().toVector()).normalize().multiply(0.4));
        }

        // Phasing passive.
        if (event.getEntity() instanceof Player victimPlayer && event.getDamager() instanceof LivingEntity) {
            if (phasingActive.contains(victimPlayer.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            tryPhase(victimPlayer, event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) {
            return;
        }
        if (!snowball.hasMetadata(DAGGER_META_OWNER)) {
            return;
        }

        // Cleanly remove stray dagger projectile after impact.
        snowball.remove();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && driftNoFall.contains(victim.getUniqueId())) {
            event.setCancelled(true);
        }

        if (phasingActive.contains(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player attacker = victim.getKiller();

        // Soul capture particle source.
        if (attacker != null && hasAstraInOffhand(attacker) && GemUtil.getEnergyLevel(attacker.getInventory().getItemInOffHand()) > 2) {
            Location loc = victim.getLocation().clone();
            if (!(victim instanceof Player)) {
                soulPool.put(loc, victim.getType());
            }

            new BukkitRunnable() {
                int loops = 0;

                @Override
                public void run() {
                    if (!attacker.isOnline() || loops >= 60) {
                        cancel();
                        soulPool.remove(loc);
                        return;
                    }
                    if (!attacker.getWorld().equals(loc.getWorld())) {
                        loops++;
                        return;
                    }

                    if (attacker.getLocation().distanceSquared(loc) <= 1.5) {
                        double heal = victim instanceof Player ? 8.0 : 4.0;
                        attacker.setHealth(Math.min(attacker.getAttribute(Attribute.MAX_HEALTH).getValue(), attacker.getHealth() + heal));
                        attacker.setSaturation(Math.min(20f, attacker.getSaturation() + (victim instanceof Player ? 10f : 4f)));
                        cancel();
                        soulPool.remove(loc);
                        return;
                    }

                    attacker.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 180, 0.15, 0.15, 0.15, 0.01, ASTRA_DUST);
                    attacker.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.3f, 0.8f);
                }
            }.runTaskTimer(plugin, 0L, 10L);
        }

        // Script death flavor for dagger kills (no custom death message rewrite here; keep combat stable).
        if (victim instanceof Player victimPlayer) {
            UUID victimId = victimPlayer.getUniqueId();
            for (Set<UUID> victims : daggerVictims.values()) {
                victims.remove(victimId);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) {
            return;
        }
        if (!(event.getVehicle() instanceof AbstractHorse)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (driftHorse.containsKey(uuid)) {
            stopDrift(player, true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (driftHorse.containsKey(player.getUniqueId())) {
            stopDrift(player, true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHorseJump(GenericGameEvent event) {
        // Paper emits jump game events from vehicles; this keeps the double-jump behavior server-safe.
        if (event.getEntity() == null || !(event.getEntity() instanceof Player rider)) {
            return;
        }

        UUID uuid = rider.getUniqueId();
        AbstractHorse horse = driftHorse.get(uuid);
        if (horse == null || !horse.equals(rider.getVehicle())) {
            return;
        }

        driftJumping.add(uuid);
        horse.eject();
        rider.setVelocity(rider.getLocation().getDirection().normalize().multiply(2.5).setY(1.5));
        horse.remove();

        Bukkit.getScheduler().runTaskLater(plugin, () -> driftJumping.remove(uuid), 20L);
    }

    private boolean tryStopAstralProjection(Player player) {
        UUID uuid = player.getUniqueId();
        if (!astralProjectionActive.contains(uuid) || player.getGameMode() != GameMode.SPECTATOR) {
            return false;
        }

        Location body = astralBodyLocation.get(uuid);
        if (body != null) {
            player.teleport(body);
        }

        setAstralCooldownByEnergy(player, GemUtil.getEnergyLevel(player.getInventory().getItemInMainHand()), true);
        player.setGameMode(GameMode.SURVIVAL);
        astralProjectionActive.remove(uuid);
        astralBodyLocation.remove(uuid);
        lastSpectatorTarget.remove(uuid);
        return true;
    }

    private void tryActivateAstralProjection(Player player) {
        if (isDisabled(player) || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isAstraGem(held) || GemUtil.getTier(held) != GemTier.TIER_2 || GemUtil.getEnergyLevel(held) <= 0) {
            return;
        }

        if (!plugin.getCooldownManager().isCooldownReady(player, "Astral")) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (astralProjectionActive.contains(uuid)) {
            return;
        }

        int energy = GemUtil.getEnergyLevel(held);
        long cooldown = switch (energy) {
            case 1, 2 -> FormatUtil.toMilliseconds(7, 45);
            case 3, 4 -> FormatUtil.toMilliseconds(5, 45);
            case 5, 6 -> FormatUtil.toMilliseconds(4, 45);
            case 7 -> FormatUtil.toMilliseconds(4, 0);
            default -> FormatUtil.toMilliseconds(3, 45);
        };

        plugin.getCooldownManager().setCooldown(player, "Astral", cooldown);
        astralProjectionActive.add(uuid);
        astralBodyLocation.put(uuid, player.getLocation().clone());
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(ColorUtil.color("<##A01FFF> Astral Projection activated"));
    }

    private void handleAstralSpookIfTargetChanged(Player player) {
        UUID uuid = player.getUniqueId();
        Entity target = player.getSpectatorTarget();
        UUID currentTarget = target == null ? null : target.getUniqueId();
        UUID previousTarget = lastSpectatorTarget.get(uuid);

        if (currentTarget != null && !currentTarget.equals(previousTarget) && target instanceof LivingEntity livingTarget) {
            livingTarget.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 10 * 20, 0, false, false));
            livingTarget.getWorld().playSound(livingTarget.getLocation(), Sound.ENTITY_WARDEN_NEARBY_CLOSER, 2.0f, 0.5f);
            disabledSeconds.put(livingTarget.getUniqueId(), 10);

            Location body = astralBodyLocation.get(uuid);
            if (body != null) {
                player.teleport(body);
            }
            player.setGameMode(GameMode.SURVIVAL);
            astralProjectionActive.remove(uuid);
            setAstralCooldownByEnergy(player, GemUtil.getEnergyLevel(player.getInventory().getItemInMainHand()), false);
        }

        lastSpectatorTarget.put(uuid, currentTarget);
    }

    private void setAstralCooldownByEnergy(Player player, int energy, boolean leftClickExit) {
        long cooldown;
        if (leftClickExit) {
            cooldown = switch (energy) {
                case 1 -> FormatUtil.toMilliseconds(2, 40);
                case 2 -> FormatUtil.toMilliseconds(2, 30);
                case 3 -> FormatUtil.toMilliseconds(2, 20);
                case 4 -> FormatUtil.toMilliseconds(2, 10);
                default -> FormatUtil.toMilliseconds(2, 0);
            };
        } else {
            cooldown = switch (energy) {
                case 1 -> FormatUtil.toMilliseconds(3, 0);
                case 2 -> FormatUtil.toMilliseconds(2, 50);
                case 3 -> FormatUtil.toMilliseconds(2, 40);
                case 4 -> FormatUtil.toMilliseconds(2, 35);
                default -> FormatUtil.toMilliseconds(2, 30);
            };
        }
        plugin.getCooldownManager().setCooldown(player, "Astral", cooldown);
    }

    private void tryActivateDimensionalDrift(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR || isDisabled(player)) {
            return;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isAstraGem(offhand) || GemUtil.getEnergyLevel(offhand) <= 0) {
            return;
        }
        if (!isSwordAxeOrAir(held)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long expireAt = doubleClickUntil.getOrDefault(uuid, 0L);
        if (now > expireAt) {
            doubleClickUntil.put(uuid, now + 700L);
            return;
        }
        doubleClickUntil.remove(uuid);

        if (!plugin.getCooldownManager().isCooldownReady(player, "Drift")) {
            return;
        }

        plugin.getCooldownManager().setCooldown(player, "Drift", FormatUtil.toMilliseconds(0, 40));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0, false, false));
        driftActive.add(uuid);
        driftNoFall.add(uuid);
        spawnDriftHorse(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            driftActive.remove(uuid);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            AbstractHorse horse = driftHorse.remove(uuid);
            if (horse != null && !horse.isDead()) {
                horse.eject();
                horse.remove();
            }
        }, 100L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> driftNoFall.remove(uuid), 220L);
        player.sendMessage(ColorUtil.color("<##A01FFF> Dimensional Drift used"));
    }

    private void spawnDriftHorse(Player player) {
        UUID uuid = player.getUniqueId();
        AbstractHorse existing = driftHorse.get(uuid);
        if (existing != null && !existing.isDead()) {
            return;
        }

        AbstractHorse horse = (AbstractHorse) player.getWorld().spawnEntity(player.getLocation(), EntityType.HORSE);
        horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.243);
        horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(53.0);
        horse.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(1.2);
        horse.setInvulnerable(true);
        horse.setInvisible(true);
        horse.setTamed(true);
        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        horse.addPassenger(player);
        driftHorse.put(uuid, horse);
    }

    private void stopDrift(Player player, boolean applySneakCooldown) {
        UUID uuid = player.getUniqueId();
        driftActive.remove(uuid);
        driftJumping.remove(uuid);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);

        AbstractHorse horse = driftHorse.remove(uuid);
        if (horse != null && !horse.isDead()) {
            horse.eject();
            horse.remove();
        }

        if (applySneakCooldown) {
            plugin.getCooldownManager().setCooldown(player, "Drift", FormatUtil.toMilliseconds(0, 35));
        }
    }

    private boolean tryHandleDaggersLeftClick(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR || isDisabled(player)) {
            return false;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isAstraGem(held) || GemUtil.getTier(held) != GemTier.TIER_2 || GemUtil.getEnergyLevel(held) <= 0) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        if (!daggersActive.contains(uuid)) {
            if (!plugin.getCooldownManager().isCooldownReady(player, "Daggers") || unboundedActive.contains(uuid) || astralProjectionActive.contains(uuid)) {
                return false;
            }
            activateDaggers(player);
            return true;
        }

        if (Boolean.TRUE.equals(daggerShotCooldown.get(uuid))) {
            return true;
        }

        int current = daggerAmount.getOrDefault(uuid, 0);
        if (current <= 0) {
            cleanupDaggerState(uuid);
            return true;
        }

        fireDagger(player, current);
        daggerAmount.put(uuid, current - 1);
        daggerShotCooldown.put(uuid, true);

        int remaining = current - 1;
        if (remaining > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> daggerShotCooldown.put(uuid, false), 7L);
        } else {
            BossBar bar = daggerBossBar.get(uuid);
            if (bar != null) {
                bar.removePlayer(player);
            }
            setDaggersCooldownByEnergy(player, GemUtil.getEnergyLevel(held));
            Bukkit.getScheduler().runTaskLater(plugin, () -> cleanupDaggerState(uuid), 60L);
        }
        return true;
    }

    private void activateDaggers(Player player) {
        UUID uuid = player.getUniqueId();
        daggersActive.add(uuid);
        daggerAmount.put(uuid, 5);
        daggerShotCooldown.put(uuid, false);

        BossBar bar = Bukkit.createBossBar("Daggers", BarColor.BLUE, BarStyle.SOLID);
        bar.setProgress(1.0);
        bar.addPlayer(player);
        daggerBossBar.put(uuid, bar);

        List<ArmorStand> visuals = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(player.getLocation(), EntityType.ARMOR_STAND);
            stand.setInvisible(true);
            stand.setInvulnerable(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.getEquipment().setHelmet(createDaggerDisplayItem());
            visuals.add(stand);
        }
        daggerVisuals.put(uuid, visuals);
        player.sendMessage(ColorUtil.color("<##A01FFF> Phantom Daggers used"));
    }

    private ItemStack createDaggerDisplayItem() {
        ItemStack shell = new ItemStack(Material.NAUTILUS_SHELL);
        ItemMeta meta = shell.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(230);
            shell.setItemMeta(meta);
        }
        return shell;
    }

    private void drainDaggers(Player player) {
        UUID uuid = player.getUniqueId();
        BossBar bar = daggerBossBar.get(uuid);
        if (bar == null) {
            return;
        }

        double next = bar.getProgress() - 0.002;
        if (next > 0) {
            bar.setProgress(next);
            return;
        }

        plugin.getCooldownManager().setCooldown(player, "Daggers", FormatUtil.toMilliseconds(4, 35));
        cleanupDaggerState(uuid);
    }

    private void updateDaggerVisuals(Player player) {
        UUID uuid = player.getUniqueId();
        List<ArmorStand> visuals = daggerVisuals.get(uuid);
        if (visuals == null) {
            return;
        }

        int amount = daggerAmount.getOrDefault(uuid, 0);
        Vector dir = player.getLocation().getDirection().normalize();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
        Location base = player.getLocation().add(0, -0.1, 0);

        Location[] offsets = new Location[] {
            base.clone().add(dir.clone().multiply(2.0)),
            base.clone().add(dir.clone().multiply(1.5)).add(right.clone().multiply(-1.1)),
            base.clone().add(dir.clone().multiply(1.5)).add(right.clone().multiply(1.1)),
            base.clone().add(dir.clone().multiply(0.5)).add(right.clone().multiply(-1.8)),
            base.clone().add(dir.clone().multiply(0.5)).add(right.clone().multiply(1.8))
        };

        for (int i = 0; i < visuals.size(); i++) {
            ArmorStand stand = visuals.get(i);
            if (stand == null || stand.isDead()) {
                continue;
            }
            if (i < amount) {
                stand.teleport(offsets[i]);
            } else {
                stand.remove();
            }
        }
    }

    private void fireDagger(Player player, int currentAmount) {
        UUID owner = player.getUniqueId();

        // Map to Skript firing order: 5,3,1,2,4
        int[] order = {4, 2, 0, 1, 3};
        int index = order[Math.max(0, Math.min(order.length - 1, 5 - currentAmount))];

        List<ArmorStand> visuals = daggerVisuals.get(owner);
        if (visuals != null && index < visuals.size()) {
            ArmorStand stand = visuals.get(index);
            if (stand != null) {
                stand.remove();
            }
        }

        Snowball projectile = player.launchProjectile(Snowball.class);
        projectile.setVelocity(projectile.getVelocity().multiply(2.0));
        projectile.setItem(createDaggerDisplayItem());
        projectile.setMetadata(DAGGER_META_OWNER, new FixedMetadataValue(plugin, owner.toString()));

        new BukkitRunnable() {
            int ticksAlive = 0;

            @Override
            public void run() {
                if (!projectile.isValid() || projectile.isDead() || ticksAlive >= 200) {
                    projectile.remove();
                    cancel();
                    return;
                }
                projectile.getWorld().spawnParticle(Particle.DUST, projectile.getLocation(), 1, 0, 0, 0, 0.01, ASTRA_DUST);
                projectile.getWorld().spawnParticle(Particle.END_ROD, projectile.getLocation(), 1, 0, 0, 0, 0.0);
                ticksAlive++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void setDaggersCooldownByEnergy(Player player, int energy) {
        long cooldown = switch (energy) {
            case 1 -> FormatUtil.toMilliseconds(1, 50);
            case 2 -> FormatUtil.toMilliseconds(1, 45);
            case 3 -> FormatUtil.toMilliseconds(1, 40);
            case 4 -> FormatUtil.toMilliseconds(1, 35);
            default -> FormatUtil.toMilliseconds(1, 30);
        };
        plugin.getCooldownManager().setCooldown(player, "Daggers", cooldown);
    }

    private void cleanupDaggerState(UUID uuid) {
        daggersActive.remove(uuid);
        daggerAmount.remove(uuid);
        daggerShotCooldown.remove(uuid);

        BossBar bar = daggerBossBar.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }

        List<ArmorStand> visuals = daggerVisuals.remove(uuid);
        if (visuals != null) {
            for (ArmorStand stand : visuals) {
                if (stand != null && !stand.isDead()) {
                    stand.remove();
                }
            }
        }

        daggerVictims.remove(uuid);
    }

    private void applyDaggerDamage(LivingEntity victim, double amount, Player owner) {
        disabledSeconds.merge(victim.getUniqueId(), 10, Integer::sum);
        daggerVictims.computeIfAbsent(owner.getUniqueId(), ignored -> new HashSet<>()).add(victim.getUniqueId());

        double health = victim.getHealth();
        if (health <= amount) {
            if (hasTotem(victim)) {
                victim.damage(9999.0, owner);
            } else {
                victim.setHealth(0.0);
            }
        } else {
            victim.setHealth(health - amount);
        }
        victim.setNoDamageTicks(0);
    }

    private boolean hasTotem(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        return player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
            || player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    private void startUnbounded(Player attacker, Player victim) {
        UUID attackerId = attacker.getUniqueId();
        unboundedActive.add(attackerId);
        unboundedVictim.put(attackerId, victim.getUniqueId());
        attacker.setGameMode(GameMode.SPECTATOR);
        attacker.teleport(victim);
        Bukkit.getScheduler().runTaskLater(plugin, () -> attacker.setSpectatorTarget(victim), 1L);
        attacker.sendMessage(ColorUtil.color("<##A01FFF> Unbounded used on " + safeName(victim)));
    }

    private void stopUnbounded(Player player) {
        UUID hunterId = player.getUniqueId();
        UUID victimId = unboundedVictim.get(hunterId);
        Player victim = victimId == null ? null : Bukkit.getPlayer(victimId);

        player.setGameMode(GameMode.SURVIVAL);
        if (victim != null) {
            player.teleport(victim);
            disableNearbyGemOwner(player, victim.getLocation());
        }

        plugin.getCooldownManager().setCooldown(player, "Daggers", FormatUtil.toMilliseconds(4, 30));
        unboundedActive.remove(hunterId);
        unboundedVictim.remove(hunterId);
    }

    private void disableNearbyGemOwner(Player hunter, Location around) {
        for (Player nearby : Bukkit.getOnlinePlayers()) {
            if (nearby.equals(hunter) || !nearby.getWorld().equals(around.getWorld())) {
                continue;
            }
            if (nearby.getLocation().distanceSquared(around) > 1.0) {
                continue;
            }
            if (hasAnyGem(nearby)) {
                disabledSeconds.put(nearby.getUniqueId(), 25);
                hunter.sendMessage(ColorUtil.color("<##A01FFF> Haunted and disabled " + safeName(nearby) + " gem for 30s"));
                break;
            }
        }
    }

    private void tryPhase(Player victim, EntityDamageByEntityEvent event) {
        GemTier tier = getAstraTierFromInventory(victim);
        if (tier == null) {
            return;
        }

        int chance = tier == GemTier.TIER_2 ? 10 : 5;
        if (ThreadLocalRandom.current().nextInt(100) >= chance) {
            return;
        }

        event.setCancelled(true);
        UUID uuid = victim.getUniqueId();
        phasingActive.add(uuid);

        if (tier == GemTier.TIER_1) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 10, 0, false, false));
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.hidePlayer(plugin, victim);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(plugin, victim);
            }
            phasingActive.remove(uuid);
        }, 10L);
    }

    private void trySpawnCapturedSoul(Player player, PlayerInteractEvent event) {
        if (!player.isSneaking()) {
            return;
        }
        if (!hasAstraInOffhand(player) || GemUtil.getEnergyLevel(player.getInventory().getItemInOffHand()) <= 1) {
            return;
        }

        Block targetBlock = event.getClickedBlock();
        if (targetBlock == null) {
            targetBlock = player.getTargetBlockExact(5);
        }
        if (targetBlock == null) {
            return;
        }

        if (targetBlock.getLocation().distanceSquared(player.getLocation()) > 25.0) {
            return;
        }

        Deque<EntityType> souls = capturedSouls.get(player.getUniqueId());
        if (souls == null || souls.isEmpty()) {
            return;
        }

        EntityType spawnType = souls.pollFirst();
        if (spawnType == null || spawnType == EntityType.PLAYER || spawnType == EntityType.UNKNOWN) {
            return;
        }

        Location spawnLoc = targetBlock.getLocation().add(0.5, 1.0, 0.5);
        Entity spawned = spawnLoc.getWorld().spawnEntity(spawnLoc, spawnType);
        if (spawned instanceof Player spawnedPlayer) {
            astraSpawningSeconds.put(spawnedPlayer.getUniqueId(), 3);
        }
    }

    private boolean isDisabled(Player player) {
        return disabledSeconds.getOrDefault(player.getUniqueId(), 0) > 0;
    }

    private boolean isAstraGem(ItemStack item) {
        return GemUtil.isGem(item) && GemUtil.getGem(item) == GemType.ASTRA;
    }

    private boolean hasAstraInOffhand(Player player) {
        return isAstraGem(player.getInventory().getItemInOffHand());
    }

    private boolean hasAnyGem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (GemUtil.isGem(item)) {
                return true;
            }
        }
        return false;
    }

    private GemTier getAstraTierFromInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isAstraGem(item)) {
                return GemUtil.getTier(item);
            }
        }
        return null;
    }

    private boolean isSwordAxeOrAir(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return true;
        }
        String name = item.getType().name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE");
    }

    private boolean isTrusted(Player caster, Player target) {
        return plugin.getTrustManager().isTrusted(caster, target);
    }

    private String safeName(Entity entity) {
        if (entity instanceof Player player) {
            return player.isInvisible() ? "???" : player.getName();
        }
        return entity.getType().name().toLowerCase();
    }

    private void cleanupDrift(UUID uuid) {
        driftActive.remove(uuid);
        driftNoFall.remove(uuid);
        driftJumping.remove(uuid);
        doubleClickUntil.remove(uuid);
        AbstractHorse horse = driftHorse.remove(uuid);
        if (horse != null && !horse.isDead()) {
            horse.remove();
        }
    }
}

