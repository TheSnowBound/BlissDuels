package me.thesnowbound.blissDuels.listeners;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.gem.GemTier;
import me.thesnowbound.blissDuels.gem.GemType;
import me.thesnowbound.blissDuels.util.ColorUtil;
import me.thesnowbound.blissDuels.util.FormatUtil;
import me.thesnowbound.blissDuels.util.GemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.ChatColor;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class FluxParityListener implements Listener {
    private static final Particle.DustOptions FLUX_DUST_TRAIL = new Particle.DustOptions(org.bukkit.Color.fromRGB(94, 215, 255), 2.0f);
    private static final int MAX_WATTS = 2_000_000;
    private static final int WATTS_PER_DIAMOND_BLOCK = 17_352;
    private static final String CHARGE_MENU_TITLE_PREFIX = "&9Watt Deposit (";
    private static final double CHARGE_STEP = 0.667;
    private static final double MAX_KINETIC = 100.0;

    private final BlissDuels plugin;
    private final FluxAbilityListener fluxAbilityListener;

    private final Map<UUID, UUID> fluxShotOwnerByProjectile = new HashMap<>();
    private final Set<UUID> fluxShotReadyShooters = new HashSet<>();

    private final Map<UUID, Integer> fluxBroken = new HashMap<>();
    private final Map<UUID, Integer> fluxBrokenTimer = new HashMap<>();
    private final Map<UUID, Double> fluxMoved = new HashMap<>();
    private final Map<UUID, Integer> fluxMovedTimer = new HashMap<>();
    private final Map<UUID, Integer> fluxDamaged = new HashMap<>();
    private final Map<UUID, Integer> fluxDamagedTimer = new HashMap<>();
    private final Map<UUID, Set<Integer>> fluxSpeedMilestones = new HashMap<>();
    private final Map<UUID, Double> staticBurstCharge = new HashMap<>();

    private final Map<UUID, Integer> fluxChargeWatts = new HashMap<>();
    private final Map<UUID, Integer> fluxChargeHidden = new HashMap<>();
    private final Map<UUID, Double> fluxChargePercent = new HashMap<>();
    private final Set<UUID> fluxCharging = new HashSet<>();
    private final Map<UUID, Integer> fluxOverchargeCountdown = new HashMap<>();
    private final Set<UUID> fluxTrueOvercharging = new HashSet<>();
    private final Map<UUID, Integer> activeStunTask = new HashMap<>();

    private final Map<UUID, UUID> kineticOverdriveOwnerByMarked = new HashMap<>();
    private final Map<UUID, Long> kineticOverdriveOwnerUntil = new HashMap<>();
    private final Map<UUID, Integer> kineticOverdriveOwnerCounter = new HashMap<>();
    private final Map<UUID, Integer> kineticOverdriveOwnerBroken = new HashMap<>();

    public FluxParityListener(BlissDuels plugin, FluxAbilityListener fluxAbilityListener) {
        this.plugin = plugin;
        this.fluxAbilityListener = fluxAbilityListener;
        startTickLoops();
    }

    @EventHandler()
    public void onJoin(PlayerJoinEvent event) {
        fluxAbilityListener.setKinetic(event.getPlayer(), fluxAbilityListener.getKinetic(event.getPlayer()));
        fluxChargeWatts.putIfAbsent(event.getPlayer().getUniqueId(), 0);
        fluxChargePercent.putIfAbsent(event.getPlayer().getUniqueId(), 0.0);
        fluxChargeHidden.putIfAbsent(event.getPlayer().getUniqueId(), 0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        fluxShotReadyShooters.remove(uuid);
        fluxBroken.remove(uuid);
        fluxBrokenTimer.remove(uuid);
        fluxMoved.remove(uuid);
        fluxMovedTimer.remove(uuid);
        fluxDamaged.remove(uuid);
        fluxDamagedTimer.remove(uuid);
        fluxSpeedMilestones.remove(uuid);
        fluxCharging.remove(uuid);
        fluxOverchargeCountdown.remove(uuid);
        fluxTrueOvercharging.remove(uuid);
        kineticOverdriveOwnerUntil.remove(uuid);
        kineticOverdriveOwnerCounter.remove(uuid);
        kineticOverdriveOwnerBroken.remove(uuid);
        kineticOverdriveOwnerByMarked.entrySet().removeIf(entry -> entry.getKey().equals(uuid) || entry.getValue().equals(uuid));
        Integer stunTask = activeStunTask.remove(uuid);
        if (stunTask != null) {
            plugin.getServer().getScheduler().cancelTask(stunTask);
        }
        staticBurstCharge.remove(uuid);
    }

    @EventHandler()
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID uuid = victim.getUniqueId();
        fluxAbilityListener.setKinetic(victim, 0.0);
        fluxCharging.remove(uuid);
        fluxTrueOvercharging.remove(uuid);
        fluxOverchargeCountdown.remove(uuid);
        Integer stunTask = activeStunTask.remove(uuid);
        if (stunTask != null) {
            plugin.getServer().getScheduler().cancelTask(stunTask);
        }
        staticBurstCharge.put(uuid, 0.0);
    }

    @EventHandler()
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter) || !(event.getProjectile() instanceof Arrow arrow)) {
            return;
        }
        if (isDisabled(shooter)) {
            return;
        }

        ItemStack offhand = shooter.getInventory().getItemInOffHand();
        if (GemUtil.getGem(offhand) != GemType.FLUX || GemUtil.getEnergyLevel(offhand) <= 2) {
            return;
        }

        if (GemUtil.getEnergyLevel(offhand) > 3) {
            GemTier tier = GemUtil.getTier(offhand);
            int chance = tier == GemTier.TIER_2 ? 20 : 10;
            if (ThreadLocalRandom.current().nextInt(100) < chance) {
                fluxShotReadyShooters.add(shooter.getUniqueId());
                fluxShotOwnerByProjectile.put(arrow.getUniqueId(), shooter.getUniqueId());
            }
        }

        // Flux arrow trail while the flagged projectile is active.
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 100 || !arrow.isValid() || arrow.isDead()) {
                    cancel();
                    return;
                }
                if (!fluxShotOwnerByProjectile.containsKey(arrow.getUniqueId())) {
                    cancel();
                    return;
                }
                arrow.getWorld().spawnParticle(Particle.DUST, arrow.getLocation(), 1, 0.0, 0.0, 0.0, 0.0, FLUX_DUST_TRAIL);
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler()
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        fluxShotOwnerByProjectile.remove(arrow.getUniqueId());
    }

    @EventHandler( priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Arrow arrow && event.getEntity() instanceof LivingEntity victim) {
            handleFluxArrowImpact(arrow, victim);
        }

        if (event.getEntity() instanceof Player victimPlayer
            && event.getDamager() instanceof Creeper creeper
            && creeper.isPowered()
            && !isDisabled(victimPlayer)
            && hasFluxTier2EnergyAbove4(victimPlayer)) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (isDisabled(attacker)) {
            return;
        }

        if (victim instanceof Player victimPlayer) {
            resetOverflowForVictim(victimPlayer);
        }

        handleKineticOverdriveCounterByMarkedAttacker(attacker);
        handleKineticOverdriveOwnerBonusHit(attacker, victim, event);
        applyFluxDamageStack(attacker, event);
        tryGround(attacker, victim);
    }

    @EventHandler()
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isDisabled(player)) {
            return;
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (GemUtil.getGem(offhand) == GemType.FLUX && GemUtil.getEnergyLevel(offhand) > 1) {
            UUID uuid = player.getUniqueId();
            fluxBrokenTimer.put(uuid, 3);
            fluxBroken.put(uuid, fluxBroken.getOrDefault(uuid, 0) + 1);
        }

        handleKineticOverdriveBreakContribution(player);
    }

    @EventHandler()
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (isDisabled(player)) {
            return;
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (GemUtil.getGem(offhand) == GemType.FLUX && GemUtil.getEnergyLevel(offhand) > 1) {
            UUID uuid = player.getUniqueId();
            fluxMovedTimer.put(uuid, 3);
            double moved = fluxMoved.getOrDefault(uuid, 0.0) + 0.5;
            fluxMoved.put(uuid, moved);
            applyOverflowSpeedStage(player, moved);
            return;
        }

        resetOverflowMovement(player);
    }

    @EventHandler()
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!event.getAction().isRightClick()) {
            return;
        }

        Player player = event.getPlayer();
        if (isDisabled(player)) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            tryCopperZip(player, event.getClickedBlock());
        }
        tryDepositDiamondBlockCharge(player);
    }

    @EventHandler()
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof LivingEntity target)) {
            return;
        }
        if (isDisabled(player)) {
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (GemUtil.getGem(held) != GemType.FLUX || GemUtil.getTier(held) != GemTier.TIER_2) {
            return;
        }

        int energy = GemUtil.getEnergyLevel(held);
        if (energy <= 0) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(player, "KineticOverdrive")) {
            return;
        }

        long cooldown = switch (energy) {
            case 1 -> FormatUtil.toMilliseconds(2, 40);
            case 2 -> FormatUtil.toMilliseconds(2, 30);
            case 3 -> FormatUtil.toMilliseconds(2, 20);
            case 4 -> FormatUtil.toMilliseconds(2, 10);
            default -> FormatUtil.toMilliseconds(2, 0);
        };
        plugin.getCooldownManager().setCooldown(player, "KineticOverdrive", cooldown);

        double base = switch (energy) {
            case 1 -> 0.0;
            case 2, 3 -> 0.5;
            default -> 1.5;
        };
        double radius = base + 2.5;

        UUID owner = player.getUniqueId();
        kineticOverdriveOwnerUntil.put(owner, System.currentTimeMillis() + 30_000L);
        kineticOverdriveOwnerCounter.put(owner, 0);
        kineticOverdriveOwnerBroken.put(owner, 0);

        markKineticOverdriveVictim(target, owner);
        if (target instanceof Player targetPlayer) {
            targetPlayer.sendMessage(ColorUtil.color("<##5ED7FF> You have been affected with " + player.getName() + "'s Kinetic Overdrive! &7Radius " + FormatUtil.formatDouble(radius)));
        }

        // Group overdrive: same owner mark applied to all nearby non-trusted living entities.
        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity nearbyLiving) || nearbyLiving.equals(player)) {
                continue;
            }
            if (isTrusted(player, nearbyLiving)) {
                continue;
            }
            markKineticOverdriveVictim(nearbyLiving, owner);
            if (nearbyLiving instanceof Player nearbyPlayer) {
                nearbyPlayer.sendMessage(ColorUtil.color("<##5ED7FF> You have been affected with " + player.getName() + "'s Kinetic Overdrive! &7Radius " + FormatUtil.formatDouble(radius)));
            }
        }

        player.sendMessage(ColorUtil.color("<##5ED7FF> You have activated Group Kinetic Overdrive!"));

        new BukkitRunnable() {
            @Override
            public void run() {
                kineticOverdriveOwnerUntil.remove(owner);
                kineticOverdriveOwnerCounter.remove(owner);
                kineticOverdriveOwnerBroken.remove(owner);
                kineticOverdriveOwnerByMarked.entrySet().removeIf(entry -> entry.getValue().equals(owner));
            }
        }.runTaskLater(plugin, 30L * 20L);
    }

    public String getWattsMessage(Player player) {
        UUID uuid = player.getUniqueId();
        int watts = fluxChargeWatts.getOrDefault(uuid, 0);
        double percent = fluxChargePercent.getOrDefault(uuid, 0.0);
        return ColorUtil.color("&f🔮 You now have " + watts + " watts, up to " + FormatUtil.formatDouble(percent) + "%% charge.");
    }

    public String getHiddenWattsMessage(Player player) {
        UUID uuid = player.getUniqueId();
        int hidden = fluxChargeHidden.getOrDefault(uuid, 0);
        double percent = fluxChargePercent.getOrDefault(uuid, 0.0);
        return ColorUtil.color("<#03eaff>🔮 You now have " + hidden + " hidden watts, up to " + FormatUtil.formatDouble(percent) + "%% charge.");
    }

    public boolean startCharging(Player player) {
        UUID uuid = player.getUniqueId();
        double percent = fluxChargePercent.getOrDefault(uuid, 0.0);
        int watts = fluxChargeWatts.getOrDefault(uuid, 0);
        if (percent <= 0.0 || watts <= 0) {
            syncChargeStores(uuid, 0.0);
            player.sendMessage(ColorUtil.color("&cYou don't have enough energy to charge!"));
            return false;
        }
        fluxCharging.add(uuid);
        fluxOverchargeCountdown.remove(uuid);
        player.sendMessage(ColorUtil.color("&aCharging started!"));
        return true;
    }

    public void stopCharging(Player player) {
        UUID uuid = player.getUniqueId();
        fluxCharging.remove(uuid);
        fluxOverchargeCountdown.remove(uuid);
        fluxTrueOvercharging.remove(uuid);
        player.sendMessage(ColorUtil.color("&cCharging Stopped!"));
    }

    public void resetAllCharges() {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            fluxAbilityListener.setKinetic(online, 0.0);
            UUID uuid = online.getUniqueId();
            syncChargeStores(uuid, 0.0);
            fluxCharging.remove(uuid);
            fluxOverchargeCountdown.remove(uuid);
            fluxTrueOvercharging.remove(uuid);
        }
    }

    public void maxWatts(Player player) {
        syncChargeStores(player.getUniqueId(), 100.0);
        player.sendMessage(getWattsMessage(player));
    }

    public void resetWatts(Player player) {
        syncChargeStores(player.getUniqueId(), 0.0);
        player.sendMessage(getWattsMessage(player));
    }

    public boolean openChargeMenu(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (GemUtil.getGem(offhand) != GemType.FLUX || GemUtil.getTier(offhand) != GemTier.TIER_2 || GemUtil.getEnergyLevel(offhand) <= 0) {
            player.sendMessage(ColorUtil.color("&cYou need a Flux Tier 2 gem with energy in offhand to open this menu."));
            return false;
        }

        String title = ColorUtil.color(CHARGE_MENU_TITLE_PREFIX + fluxChargeWatts.getOrDefault(player.getUniqueId(), 0) + " watts)");
        Inventory menu = plugin.getServer().createInventory(null, 27, title);
        player.openInventory(menu);
        return true;
    }

    @EventHandler()
    public void onChargeMenuOpenByDropOne(InventoryClickEvent event) {
        if (event.getAction() != InventoryAction.DROP_ONE_SLOT) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (GemUtil.getGem(clicked) != GemType.FLUX || GemUtil.getTier(clicked) != GemTier.TIER_2 || GemUtil.getEnergyLevel(clicked) <= 0) {
            return;
        }

        event.setCancelled(true);
        openChargeMenu(player);
    }

    @EventHandler()
    public void onChargeMenuClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!isChargeMenuTitle(event.getView().getTitle())) {
            return;
        }

        int depositedBlocks = 0;
        for (ItemStack item : event.getInventory().getContents()) {
            if (item == null || item.getType() != Material.DIAMOND_BLOCK) {
                continue;
            }
            depositedBlocks += item.getAmount();
        }

        if (depositedBlocks <= 0) {
            return;
        }

        UUID uuid = player.getUniqueId();
        int currentWatts = fluxChargeWatts.getOrDefault(uuid, 0);
        int addWatts = Math.min(depositedBlocks * WATTS_PER_DIAMOND_BLOCK, MAX_WATTS - currentWatts);
        int nextWatts = Math.max(0, currentWatts + addWatts);
        syncChargeStores(uuid, ((double) nextWatts / MAX_WATTS) * 100.0);
        player.sendMessage(getWattsMessage(player));
    }

    @EventHandler()
    public void onAnyDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (isDisabled(victim)) {
            return;
        }

        ItemStack offhand = victim.getInventory().getItemInOffHand();
        ItemStack held = victim.getInventory().getItemInMainHand();
        boolean hasFlux = (GemUtil.getGem(offhand) == GemType.FLUX && GemUtil.getEnergyLevel(offhand) > 0)
            || (GemUtil.getGem(held) == GemType.FLUX && GemUtil.getEnergyLevel(held) > 0);
        if (!hasFlux) {
            return;
        }

        double delta = Math.max(0.0, event.getFinalDamage() / 3.0);
        if (delta <= 0.0) {
            return;
        }

        UUID uuid = victim.getUniqueId();
        staticBurstCharge.put(uuid, staticBurstCharge.getOrDefault(uuid, 0.0) + delta);

        // After 2 minutes remove the same amount that was added by this hit.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            double current = staticBurstCharge.getOrDefault(uuid, 0.0);
            staticBurstCharge.put(uuid, Math.max(0.0, current - delta));
        }, 120L * 20L);
    }

    private void startTickLoops() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();

                    tickDown(fluxBrokenTimer, uuid);
                    if (fluxBrokenTimer.getOrDefault(uuid, 0) == 0) {
                        fluxBroken.put(uuid, 0);
                    }

                    tickDown(fluxDamagedTimer, uuid);
                    if (fluxDamagedTimer.getOrDefault(uuid, 0) == 0) {
                        fluxDamaged.put(uuid, 0);
                    }

                    tickDown(fluxMovedTimer, uuid);
                    if (fluxMovedTimer.getOrDefault(uuid, 0) == 0) {
                        resetOverflowMovement(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    removeDebuffsForHighEnergyFlux(player);
                    applyOverflowHaste(player);
                    tickCharging(player);
                    tickOverchargeCountdown(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    tickTrueOvercharge(player);
                }
            }
        }.runTaskTimer(plugin, 30L, 30L);
    }

    private void removeDebuffsForHighEnergyFlux(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack held = player.getInventory().getItemInMainHand();

        if (isFluxWithEnergy(offhand, 5) || isFluxWithEnergy(held, 5)) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.HUNGER);
            player.removePotionEffect(PotionEffectType.WEAKNESS);
        }
    }

    private void applyOverflowHaste(Player player) {
        if (isDisabled(player)) {
            return;
        }

        int broken = fluxBroken.getOrDefault(player.getUniqueId(), 0);
        if (broken > 50) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 9, false, false));
        } else if (broken > 45) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 8, false, false));
        } else if (broken > 40) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 7, false, false));
        } else if (broken > 35) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 6, false, false));
        } else if (broken > 30) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 5, false, false));
        } else if (broken > 25) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 4, false, false));
        } else if (broken > 20) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 3, false, false));
        } else if (broken > 15) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 2, false, false));
        } else if (broken > 10) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 1, false, false));
        } else if (broken > 5) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 0, false, false));
        }
    }

    private void tickCharging(Player player) {
        UUID uuid = player.getUniqueId();
        if (!fluxCharging.contains(uuid)) {
            return;
        }

        if (!hasFluxTier2EitherHand(player)) {
            fluxCharging.remove(uuid);
            player.sendMessage(ColorUtil.color("&cCharging paused!"));
            return;
        }

        double chargePercent = Math.max(0.0, fluxChargePercent.getOrDefault(uuid, 0.0));
        int watts = fluxChargeWatts.getOrDefault(uuid, 0);
        if (chargePercent <= 0.0 || watts <= 0) {
            syncChargeStores(uuid, 0.0);
            fluxCharging.remove(uuid);
            player.sendMessage(ColorUtil.color("&cYou ran out of watts. Charging stopped."));
            return;
        }

        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 16, 0.4, 0.4, 0.4, 0.0, new Particle.DustOptions(org.bukkit.Color.fromRGB(94, 215, 255), 1.0f));
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 4, 0.5, 0.5, 0.5, 0.0);

        double kinetic = fluxAbilityListener.getKinetic(player);
        if (chargePercent > CHARGE_STEP) {
            if (kinetic >= MAX_KINETIC) {
                fluxCharging.remove(uuid);
                return;
            } else {
                double nextPercent = Math.max(0.0, chargePercent - CHARGE_STEP);
                fluxAbilityListener.setKinetic(player, Math.min(MAX_KINETIC, kinetic + CHARGE_STEP));
                syncChargeStores(uuid, nextPercent);
            }
            return;
        }

        fluxAbilityListener.setKinetic(player, Math.min(MAX_KINETIC, kinetic + chargePercent));
        syncChargeStores(uuid, 0.0);
        fluxCharging.remove(uuid);
        player.sendMessage(ColorUtil.color("&f🔮 You ran out of watts, your gem is charged at "
            + FormatUtil.formatDouble(fluxAbilityListener.getKinetic(player)) + "%%"));
    }

    private void tickOverchargeCountdown(Player player) {
        UUID uuid = player.getUniqueId();
        Integer left = fluxOverchargeCountdown.get(uuid);
        if (left == null) {
            return;
        }

        if (left <= 0) {
            fluxOverchargeCountdown.remove(uuid);
            fluxTrueOvercharging.add(uuid);
            return;
        }

        player.sendTitle(ColorUtil.color("&c&lWARNING"), ColorUtil.color("&6Overcharging begins in... " + left), 0, 20, 0);
        fluxOverchargeCountdown.put(uuid, left - 1);
    }

    private void tickTrueOvercharge(Player player) {
        UUID uuid = player.getUniqueId();
        if (!fluxTrueOvercharging.contains(uuid)) {
            return;
        }

        double kinetic = fluxAbilityListener.getKinetic(player);
        if (kinetic < 115.0) {
            double next = kinetic + 0.666666666667;
            fluxAbilityListener.setKinetic(player, next);
            player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 16, 0.4, 0.4, 0.4, 0.0, new Particle.DustOptions(org.bukkit.Color.fromRGB(94, 215, 255), 1.0f));
            player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 4, 0.5, 0.5, 0.5, 0.0);

            if (next > 101 && next < 121) {
                player.damage(1.0);
            } else if (next > 121 && next < 140) {
                player.damage(2.0);
            } else if (next > 141 && next < 161) {
                player.damage(3.0);
            } else if (next > 161 && next < 181) {
                player.damage(4.0);
            } else if (next > 181 && next < 201) {
                player.damage(5.0);
            }
            return;
        }

        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 15, 0.4, 0.4, 0.4, 0.0, new Particle.DustOptions(org.bukkit.Color.fromRGB(94, 215, 255), 0.8f));
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 4, 0.5, 0.5, 0.5, 0.0);
        player.damage(5.0);
        fluxAbilityListener.setKinetic(player, MAX_KINETIC);
        fluxChargeWatts.put(uuid, 0);
        fluxChargePercent.put(uuid, 0.0);
        fluxChargeHidden.put(uuid, 0);
    }

    private void handleFluxArrowImpact(Arrow arrow, LivingEntity victim) {
        UUID shooterId = fluxShotOwnerByProjectile.remove(arrow.getUniqueId());
        if (shooterId == null || !fluxShotReadyShooters.contains(shooterId)) {
            return;
        }

        Player shooter = plugin.getServer().getPlayer(shooterId);
        if (shooter == null || isDisabled(shooter)) {
            return;
        }

        victim.getWorld().strikeLightningEffect(victim.getLocation());
        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 10.0f, 1.0f);

        double nextHealth = victim.getHealth() - 0.5;
        if (nextHealth <= 0.0) {
            victim.damage(9999.0, shooter);
        } else {
            victim.setHealth(nextHealth);
        }

        stunEntity(victim, 2);
        fluxShotReadyShooters.remove(shooterId);
    }

    private void applyFluxDamageStack(Player attacker, EntityDamageByEntityEvent event) {
        ItemStack offhand = attacker.getInventory().getItemInOffHand();
        if (GemUtil.getGem(offhand) != GemType.FLUX || GemUtil.getEnergyLevel(offhand) <= 1) {
            return;
        }

        UUID uuid = attacker.getUniqueId();
        fluxDamagedTimer.put(uuid, 3);
        int damaged = fluxDamaged.getOrDefault(uuid, 0) + 1;
        fluxDamaged.put(uuid, damaged);

        double bonus = 0.0;
        if (damaged >= 0 && damaged <= 3) {
            bonus += 0.8;
        }
        if (damaged >= 3 && damaged <= 6) {
            bonus += 0.9;
        }
        if (damaged >= 6 && damaged <= 9) {
            bonus += 1.0;
        }
        if (damaged >= 9 && damaged <= 12) {
            bonus += 1.1;
        }
        if (damaged >= 12 && damaged <= 15) {
            bonus += 1.2;
        }
        if (damaged == 15) {
            bonus += 1.5;
        }

        event.setDamage(event.getDamage() + bonus);
    }

    private void tryGround(Player attacker, LivingEntity victim) {
        ItemStack held = attacker.getInventory().getItemInMainHand();
        if (!isFluxTier2WithEnergy(held)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(attacker, "EnergyBeam")) {
            return;
        }
        if (isTrusted(attacker, victim)) {
            attacker.sendMessage(ColorUtil.color("<##FFD773> You cannot cast negative powers on allies!"));
            return;
        }

        plugin.getCooldownManager().setCooldown(attacker, "EnergyBeam", energyBeamCooldown(attacker));

        new BukkitRunnable() {
            @Override
            public void run() {
                double kinetic = fluxAbilityListener.getKinetic(attacker);
                if (kinetic >= 100.0 && victim instanceof Player victimPlayer) {
                    groundShuffle(victimPlayer);
                }

                stunEntity(victim, 4);
                double damage = (victim.getHealth() * kinetic / 100.0) / 2.0;
                if (damage > 0.0) {
                    victim.damage(Math.max(0.1, damage), attacker);
                }

                for (Entity nearby : victim.getNearbyEntities(3.0, 3.0, 3.0)) {
                    if (!(nearby instanceof LivingEntity areaVictim) || areaVictim.equals(victim) || areaVictim.equals(attacker)) {
                        continue;
                    }
                    if (isTrusted(attacker, areaVictim)) {
                        continue;
                    }
                    stunEntity(areaVictim, 3);
                    areaVictim.damage(0.001, attacker);
                }

                fluxAbilityListener.setKinetic(attacker, 0.0);
            }
        }.runTaskLater(plugin, 5L);
    }

    private void handleKineticOverdriveCounterByMarkedAttacker(Player attacker) {
        UUID markedId = attacker.getUniqueId();
        UUID owner = kineticOverdriveOwnerByMarked.get(markedId);
        if (owner == null || owner.equals(markedId)) {
            return;
        }

        long until = kineticOverdriveOwnerUntil.getOrDefault(owner, 0L);
        if (System.currentTimeMillis() > until) {
            kineticOverdriveOwnerByMarked.remove(markedId);
            return;
        }

        Player ownerPlayer = plugin.getServer().getPlayer(owner);
        if (ownerPlayer == null) {
            return;
        }

        int counter = kineticOverdriveOwnerCounter.getOrDefault(owner, 0) + 1;
        kineticOverdriveOwnerCounter.put(owner, counter);

        if (counter == 3) {
            ownerPlayer.sendMessage(ColorUtil.color("&7You have &b3 &cATTACK &7points. &7Your next hit will do &b1.1x damage."));
        } else if (counter == 6) {
            ownerPlayer.sendMessage(ColorUtil.color("&7You have &b6 &cATTACK &7points. &7Your next hit will do &b1.2x damage."));
        } else if (counter == 9) {
            ownerPlayer.sendMessage(ColorUtil.color("&7You have &b9 &cATTACK &7points. &7Your next hit will do &b1.3x damage."));
        } else if (counter == 12) {
            ownerPlayer.sendMessage(ColorUtil.color("&7You have &b12 &cATTACK &7points. &7Your next hit will do &b1.4x damage."));
        } else if (counter == 15) {
            ownerPlayer.sendMessage(ColorUtil.color("&7You have &b15 &cATTACK &7points. &7Your next hit will do &b1.5x damage."));
        }
    }

    private void handleKineticOverdriveOwnerBonusHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        UUID owner = attacker.getUniqueId();
        long until = kineticOverdriveOwnerUntil.getOrDefault(owner, 0L);
        if (System.currentTimeMillis() > until) {
            return;
        }

        int counter = kineticOverdriveOwnerCounter.getOrDefault(owner, 0);
        double multiplier = 0.0;
        if (counter >= 3 && counter < 6) {
            multiplier = 1.1;
        } else if (counter >= 6 && counter < 9) {
            multiplier = 1.2;
        } else if (counter >= 9 && counter < 12) {
            multiplier = 1.3;
        } else if (counter >= 12 && counter < 15) {
            multiplier = 1.4;
        } else if (counter >= 15) {
            multiplier = 1.5;
        }

        if (multiplier <= 0.0) {
            return;
        }

        event.setCancelled(true);
        victim.damage(event.getDamage() * multiplier, attacker);
        Vector push = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize().multiply(0.7);
        victim.setVelocity(push);
        kineticOverdriveOwnerCounter.put(owner, 0);
    }

    private void handleKineticOverdriveBreakContribution(Player player) {
        UUID marked = player.getUniqueId();
        UUID owner = kineticOverdriveOwnerByMarked.get(marked);
        if (owner == null || owner.equals(marked)) {
            return;
        }

        long until = kineticOverdriveOwnerUntil.getOrDefault(owner, 0L);
        if (System.currentTimeMillis() > until) {
            kineticOverdriveOwnerByMarked.remove(marked);
            return;
        }

        Player ownerPlayer = plugin.getServer().getPlayer(owner);
        if (ownerPlayer == null) {
            return;
        }

        int value = kineticOverdriveOwnerBroken.getOrDefault(owner, 0) + 1;
        kineticOverdriveOwnerBroken.put(owner, value);

        if (value == 5) {
            ownerPlayer.sendMessage(ColorUtil.color("&7You have &b1 &eMINING &7point, giving you Haste level &b1 &7for &b20s&7."));
            ownerPlayer.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 20, 0, false, false));
        } else if (value == 10) {
            ownerPlayer.sendMessage(ColorUtil.color("&7You have &b2 &eMINING &7points, giving you Haste level &b2 &7for &b40s&7."));
            ownerPlayer.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40 * 20, 1, false, false));
        } else if (value == 15) {
            ownerPlayer.sendMessage(ColorUtil.color("&7You have &b3 &eMINING &7points, giving you Haste level &b3 &7for &b60s&7."));
            ownerPlayer.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60 * 20, 2, false, false));
        } else if (value == 20) {
            ownerPlayer.sendMessage(ColorUtil.color("&7You have &b4 &eMINING &7points, giving you Haste level &b4 &7for &b80s&7."));
            ownerPlayer.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 80 * 20, 3, false, false));
        } else if (value == 25) {
            ownerPlayer.sendMessage(ColorUtil.color("&7You have &b5 &eMINING &7points, giving you Haste level &b5 &7for &b100s&7."));
            ownerPlayer.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100 * 20, 4, false, false));
        }
    }

    private void markKineticOverdriveVictim(LivingEntity target, UUID owner) {
        kineticOverdriveOwnerByMarked.put(target.getUniqueId(), owner);
    }

    private void applyOverflowSpeedStage(Player player, double moved) {
        UUID uuid = player.getUniqueId();
        player.setWalkSpeed(0.2f);
        if (moved < 100) {
            return;
        }

        int stage = (int) Math.floor(moved / 100.0);
        stage = Math.min(stage, 20);
        float walkSpeed = (float) (0.2 + (stage * 0.02));
        player.setWalkSpeed(Math.min(1.0f, walkSpeed));

        Set<Integer> reached = fluxSpeedMilestones.computeIfAbsent(uuid, ignored -> new HashSet<>());
        if (stage >= 1 && reached.add(stage)) {
            player.sendMessage(ColorUtil.color("&7Now moving at +" + (stage * 10) + "% speed!"));
        }
    }

    private void resetOverflowMovement(Player player) {
        UUID uuid = player.getUniqueId();
        fluxMoved.put(uuid, 0.0);
        fluxSpeedMilestones.remove(uuid);
        if (player.isOnline()) {
            player.setWalkSpeed(0.2f);
        }
    }

    private void resetOverflowForVictim(Player victim) {
        UUID uuid = victim.getUniqueId();
        fluxMoved.put(uuid, 0.0);
        fluxDamaged.put(uuid, 0);
        victim.setWalkSpeed(0.2f);
    }

    private void tryCopperZip(Player player, Block clickedBlock) {
        if (!player.isSneaking()) {
            return;
        }
        if (clickedBlock == null || !isCopperVariant(clickedBlock.getType())) {
            return;
        }
        if (!isSwordAxeOrAir(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (GemUtil.getGem(player.getInventory().getItemInOffHand()) != GemType.FLUX) {
            return;
        }
        if (GemUtil.getEnergyLevel(player.getInventory().getItemInOffHand()) <= 3) {
            return;
        }

        Block below = player.getLocation().subtract(0, 1, 0).getBlock();
        if (!isCopperVariant(below.getType())) {
            return;
        }

        Location destination = clickedBlock.getLocation().clone().add(0, 1, 0);
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());
        player.teleport(destination);
    }

    private void tryDepositDiamondBlockCharge(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (GemUtil.getGem(offhand) != GemType.FLUX || GemUtil.getTier(offhand) != GemTier.TIER_2) {
            return;
        }
        if (held.getType() != Material.DIAMOND_BLOCK) {
            return;
        }

        UUID uuid = player.getUniqueId();
        int watts = fluxChargeWatts.getOrDefault(uuid, 0);
        if (watts >= MAX_WATTS) {
            return;
        }

        int add = Math.min(WATTS_PER_DIAMOND_BLOCK, MAX_WATTS - watts);
        int nextWatts = watts + add;
        syncChargeStores(uuid, ((double) nextWatts / MAX_WATTS) * 100.0);

        if (held.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItemInMainHand(held);
        }

        player.sendMessage(getWattsMessage(player));
    }

    // Flux GroundShuffle: random 1-9 hotbar slots shuffled.
    private void groundShuffle(Player player) {
        int slots = ThreadLocalRandom.current().nextInt(1, 10);
        ArrayList<ItemStack> shuffled = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            ItemStack item = player.getInventory().getItem(i);
            shuffled.add(item == null ? null : item.clone());
        }
        Collections.shuffle(shuffled);
        for (int i = 0; i < slots; i++) {
            player.getInventory().setItem(i, shuffled.get(i));
        }
    }

    private void stunEntity(LivingEntity entity, int seconds) {
        if (seconds <= 0) {
            return;
        }

        UUID uuid = entity.getUniqueId();
        Integer previousTask = activeStunTask.remove(uuid);
        if (previousTask != null) {
            plugin.getServer().getScheduler().cancelTask(previousTask);
        }

        Location lock = entity.getLocation().clone();
        int taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            int ticks = seconds * 20;

            @Override
            public void run() {
                if (ticks-- <= 0 || !entity.isValid() || entity.isDead()) {
                    Integer current = activeStunTask.remove(uuid);
                    if (current != null) {
                        plugin.getServer().getScheduler().cancelTask(current);
                    }
                    return;
                }

                Location currentLoc = entity.getLocation();
                if (Math.abs(currentLoc.getX() - lock.getX()) > 0.005
                    || Math.abs(currentLoc.getY() - lock.getY()) > 0.005
                    || Math.abs(currentLoc.getZ() - lock.getZ()) > 0.005) {
                    lock.setYaw(currentLoc.getYaw());
                    lock.setPitch(currentLoc.getPitch());
                    entity.teleport(lock);
                }
            }
        }, 0L, 1L);

        activeStunTask.put(uuid, taskId);
    }

    private void tickDown(Map<UUID, Integer> timers, UUID uuid) {
        int value = timers.getOrDefault(uuid, 0);
        if (value > 0) {
            timers.put(uuid, value - 1);
        }
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

    private boolean isFluxWithEnergy(ItemStack item, int minEnergy) {
        return GemUtil.getGem(item) == GemType.FLUX && GemUtil.getEnergyLevel(item) >= minEnergy;
    }

    private boolean isFluxTier2WithEnergy(ItemStack item) {
        return GemUtil.getGem(item) == GemType.FLUX && GemUtil.getTier(item) == GemTier.TIER_2 && GemUtil.getEnergyLevel(item) > 0;
    }

    private boolean hasFluxTier2EitherHand(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        return isFluxTier2WithEnergy(held) || isFluxTier2WithEnergy(offhand);
    }

    private boolean hasFluxTier2EnergyAbove4(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        return (GemUtil.getGem(held) == GemType.FLUX && GemUtil.getTier(held) == GemTier.TIER_2 && GemUtil.getEnergyLevel(held) > 4)
            || (GemUtil.getGem(offhand) == GemType.FLUX && GemUtil.getTier(offhand) == GemTier.TIER_2 && GemUtil.getEnergyLevel(offhand) > 4);
    }

    private boolean isSwordAxeOrAir(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return true;
        }
        String name = item.getType().name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE");
    }

    private boolean isCopperVariant(Material material) {
        return material != null && material.name().contains("COPPER");
    }

    private boolean isTrusted(Player source, LivingEntity target) {
        return plugin.getTrustManager().isTrusted(source, target);
    }

    private boolean isDisabled(Player player) {
        return false;
    }

    private boolean isChargeMenuTitle(String title) {
        String stripped = ChatColor.stripColor(title == null ? "" : title);
        return stripped.startsWith("Watt Deposit (");
    }

    private void syncChargeStores(UUID uuid, double percent) {
        double normalizedPercent = Math.max(0.0, Math.min(100.0, percent));
        int watts = (int) Math.floor((normalizedPercent / 100.0) * MAX_WATTS);
        fluxChargePercent.put(uuid, normalizedPercent);
        fluxChargeWatts.put(uuid, watts);
        fluxChargeHidden.put(uuid, watts);
    }

    public double getStaticBurstCharge(Player player) {
        if (player == null) {
            return 0.0;
        }
        return Math.max(0.0, staticBurstCharge.getOrDefault(player.getUniqueId(), 0.0));
    }
}
