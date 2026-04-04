package me.thesnowbound.blissDuels.listeners;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.gem.GemTier;
import me.thesnowbound.blissDuels.gem.GemType;
import me.thesnowbound.blissDuels.util.AbilityParticleUtil;
import me.thesnowbound.blissDuels.util.ColorUtil;
import me.thesnowbound.blissDuels.util.FormatUtil;
import me.thesnowbound.blissDuels.util.GemUtil;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Strength gem conversion slice based on reference/Gems/strength.sk.
 * Includes bounty hunter, frailer/nullify, and chad strength.
 */
public class StrengthAbilityListener implements Listener {
    private static final String BOUNTY_SELECT_TITLE = "&8Select Target";
    private static final String BOUNTY_ITEMS_TITLE = "&8Place Any Items";
    private static final Particle.DustOptions BOUNTY_DUST = new Particle.DustOptions(org.bukkit.Color.fromRGB(241, 3, 3), 1.5f);
    private static final Particle.DustOptions STRENGTH_RED_DUST = new Particle.DustOptions(org.bukkit.Color.fromRGB(241, 3, 3), 1.0f);
    private static final Particle.DustOptions STRENGTH_DARK_RED_DUST = new Particle.DustOptions(org.bukkit.Color.fromRGB(126, 2, 2), 1.0f);

    private final BlissDuels plugin;

    private final Map<UUID, Long> strengthDoubleClickWindow = new HashMap<>();
    private final Map<UUID, UUID> bountyTarget = new HashMap<>();
    private final Map<UUID, Integer> bountyCounter = new HashMap<>();

    private final Map<UUID, Long> nullifyUntil = new HashMap<>();

    private final Set<UUID> chadSelfActive = new HashSet<>();
    private final Set<UUID> chadGroupActive = new HashSet<>();
    private final Map<UUID, Integer> chadSelfCounter = new HashMap<>();
    private final Map<UUID, Integer> chadGroupCounter = new HashMap<>();

    public StrengthAbilityListener(BlissDuels plugin) {
        this.plugin = plugin;
        startChadParticlesTick();
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

        if (action.isRightClick()) {
            tryOpenBountyMenus(player);
            tryActivateChadStrength(player);
            tryCancelPotionUseIfNullified(player, event);
            return;
        }

        // Left click: tier 1 warning and Nullify trigger.
        ItemStack held = player.getInventory().getItemInMainHand();
        if (isStrengthGem(held) && GemUtil.getTier(held) == GemTier.TIER_1 && GemUtil.getEnergyLevel(held) > 0) {
            player.sendMessage(ColorUtil.color("<##F10303> You need tier 2 gem to cast this skill!"));
        }
        tryNullify(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (isViewTitle(title, BOUNTY_SELECT_TITLE)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.PLAYER_HEAD || clicked.getItemMeta() == null) {
                return;
            }

            String display = clicked.getItemMeta().getDisplayName();
            String name = display == null ? null : ChatColor.stripColor(display);
            if (name == null || name.isEmpty()) {
                return;
            }

            Player target = Bukkit.getPlayerExact(name);
            if (target == null || target.equals(player)) {
                return;
            }

            bountyTarget.put(player.getUniqueId(), target.getUniqueId());
            bountyCounter.put(player.getUniqueId(), 0);
            Inventory inv = Bukkit.createInventory(player, InventoryType.HOPPER, ColorUtil.color(BOUNTY_ITEMS_TITLE));
            player.openInventory(inv);
            return;
        }

        if (isViewTitle(title, BOUNTY_ITEMS_TITLE)) {
            // Let movement settle, then score only the top hopper inventory.
            Bukkit.getScheduler().runTask(plugin, () -> recalculateBountyCounter(player, event.getView().getTopInventory()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionConsume(PlayerItemConsumeEvent event) {
        if (!isNullified(event.getPlayer())) {
            return;
        }
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.POTION) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!isViewTitle(event.getView().getTitle(), BOUNTY_ITEMS_TITLE)) {
            return;
        }

        UUID hunterId = player.getUniqueId();
        int counter = bountyCounter.getOrDefault(hunterId, 0);
        UUID targetId = bountyTarget.get(hunterId);
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);

        if (counter <= 0 || target == null || !target.isOnline()) {
            player.sendMessage(ColorUtil.color("<##F10303> Insufficient amount of items."));
            bountyCounter.remove(hunterId);
            bountyTarget.remove(hunterId);
            return;
        }

        player.sendMessage(ColorUtil.color("<##F10303> Activated Bounty Hunter"));
        target.playSound(target.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 4.0f, 1.0f);
        target.playSound(target.getLocation(), Sound.BLOCK_BELL_USE, 10.0f, 0.8f);
        target.playSound(target.getLocation(), Sound.AMBIENT_CAVE, 8.0f, 0.8f);

        applyBountyCooldown(player);
        runHuntingActionbar(target);

        int pulses = counter;
        new BukkitRunnable() {
            int left = pulses;

            @Override
            public void run() {
                if (!player.isOnline() || !target.isOnline() || left <= 0) {
                    cancel();
                    return;
                }
                drawBountyLine(player, target);
                left--;
            }
        }.runTaskTimer(plugin, 0L, 6L);

        bountyCounter.put(hunterId, 0);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (isDisabled(attacker)) {
            return;
        }

        if (isNullified(attacker)) {
            event.setCancelled(true);
            return;
        }

        applyFrailer(attacker, victim);
        applyChadDamage(attacker, event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        bountyCounter.remove(uuid);
        bountyTarget.remove(uuid);
        chadSelfActive.remove(uuid);
        chadGroupActive.remove(uuid);
        chadSelfCounter.remove(uuid);
        chadGroupCounter.remove(uuid);
        nullifyUntil.remove(uuid);
    }

    private void tryOpenBountyMenus(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack held = player.getInventory().getItemInMainHand();

        if (!player.isSneaking() || !isStrengthGem(offhand) || GemUtil.getEnergyLevel(offhand) <= 0 || !isSwordAxeOrAir(held)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long expires = strengthDoubleClickWindow.getOrDefault(uuid, 0L);

        if (now > expires) {
            strengthDoubleClickWindow.put(uuid, now + 700L);
            return;
        }

        strengthDoubleClickWindow.remove(uuid);
        Inventory select = Bukkit.createInventory(player, 54, ColorUtil.color(BOUNTY_SELECT_TITLE));
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) {
                continue;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta baseMeta = head.getItemMeta();
            if (baseMeta instanceof SkullMeta meta) {
                meta.setOwningPlayer(online);
                meta.setDisplayName(ColorUtil.color("&f" + online.getName()));
                head.setItemMeta(meta);
            }
            if (slot < select.getSize()) {
                select.setItem(slot++, head);
            }
        }
        player.openInventory(select);
    }

    private void recalculateBountyCounter(Player player, Inventory inv) {
        UUID uuid = player.getUniqueId();
        if (!bountyTarget.containsKey(uuid)) {
            return;
        }

        int score = 0;
        for (ItemStack item : inv.getContents()) {
            // Skript parity for counter scaling: +60 per non-empty hopper slot entry.
            if (isTrackedItemForBounty(item)) {
                score += 60;
            }
        }
        bountyCounter.put(uuid, score);
    }

    private boolean isTrackedItemForBounty(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    private void applyBountyCooldown(Player hunter) {
        ItemStack offhand = hunter.getInventory().getItemInOffHand();
        int energy = GemUtil.getEnergyLevel(offhand);

        long cooldown;
        if (energy >= 7) {
            cooldown = FormatUtil.toMilliseconds(3, 0);
        } else if (energy >= 5) {
            cooldown = FormatUtil.toMilliseconds(4, 0);
        } else if (energy >= 3) {
            cooldown = FormatUtil.toMilliseconds(5, 0);
        } else {
            cooldown = FormatUtil.toMilliseconds(7, 0);
        }
        plugin.getCooldownManager().setCooldown(hunter, "Bounty", cooldown);
    }

    private void runHuntingActionbar(Player target) {
        new BukkitRunnable() {
            int ticks = 283;

            @Override
            public void run() {
                if (!target.isOnline() || ticks-- <= 0) {
                    cancel();
                    return;
                }
                target.sendActionBar(ColorUtil.color("<##F10303>You feel like you're being watched..."));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drawBountyLine(Player from, Player to) {
        Location start = from.getEyeLocation().clone().add(0, -0.7, 0);
        Vector vec = to.getEyeLocation().toVector().subtract(start.toVector()).normalize().multiply(0.4);

        for (int i = 0; i < 16; i++) {
            start.getWorld().spawnParticle(Particle.DUST, start, 3, 0.0, 0.0, 0.0, 0.0, BOUNTY_DUST, true);
            start.add(vec);
        }
    }

    private void applyFrailer(Player attacker, LivingEntity victim) {
        ItemStack held = attacker.getInventory().getItemInMainHand();
        if (!isStrengthTier2WithEnergy(held) || isTrusted(attacker, victim)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(attacker, "Frailer")) {
            return;
        }

        int energy = GemUtil.getEnergyLevel(held);
        plugin.getCooldownManager().setCooldown(attacker, "Frailer", frailerCooldown(energy));

        victim.getActivePotionEffects().forEach(effect -> victim.removePotionEffect(effect.getType()));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 20, 0, false, false));
        AbilityParticleUtil.drawAbilityLine(attacker, victim, org.bukkit.Color.fromRGB(241, 3, 3), true);

        attacker.sendMessage(ColorUtil.color("<##F10303> Frailer used on " + safeName(victim)));
        if (victim instanceof Player vp) {
            vp.sendMessage(ColorUtil.color("<##F10303> You have been affected with Frailer"));
        }
    }

    private void tryNullify(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isStrengthTier2WithEnergy(held)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(player, "Frailer")) {
            return;
        }

        int energy = GemUtil.getEnergyLevel(held);
        double radius;
        long cooldown;
        if (energy > 4) {
            radius = 5.0;
            cooldown = FormatUtil.toMilliseconds(2, 0);
        } else if (energy == 4) {
            radius = 4.5;
            cooldown = FormatUtil.toMilliseconds(2, 5);
        } else if (energy == 3) {
            radius = 3.5;
            cooldown = FormatUtil.toMilliseconds(2, 10);
        } else if (energy == 2) {
            radius = 2.5;
            cooldown = FormatUtil.toMilliseconds(2, 15);
        } else {
            radius = 1.5;
            cooldown = FormatUtil.toMilliseconds(2, 20);
        }

        plugin.getCooldownManager().setCooldown(player, "Frailer", cooldown);
        player.sendMessage(ColorUtil.color("<##F10303> Nullify activated"));
        playStrengthCircleExpansion(player.getLocation(), radius, false);

        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || living.equals(player) || isTrusted(player, living)) {
                continue;
            }
            if (living.getLocation().distanceSquared(player.getLocation()) > radius * radius) {
                continue;
            }

            living.getActivePotionEffects().forEach(effect -> living.removePotionEffect(effect.getType()));
            nullifyUntil.put(living.getUniqueId(), System.currentTimeMillis() + 30_000L);
        }
    }

    private void tryActivateChadStrength(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isStrengthTier2WithEnergy(held)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(player, "ChadStrength")) {
            return;
        }

        Entity target = player.getTargetEntity(8);
        int energy = GemUtil.getEnergyLevel(held);
        plugin.getCooldownManager().setCooldown(player, "ChadStrength", chadCooldown(energy));

        if (!(target instanceof LivingEntity targetLiving) || targetLiving.equals(player)) {
            chadSelfActive.add(player.getUniqueId());
            chadSelfCounter.put(player.getUniqueId(), 0);
            player.sendMessage(ColorUtil.color("<##F10303> Chad Strength activated"));
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 5, 0.3, 0.4, 0.3, 0.01);
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60 * 20, 0, false, false));

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                chadSelfActive.remove(player.getUniqueId());
                chadSelfCounter.remove(player.getUniqueId());
            }, 60L * 20L);
            return;
        }

        player.sendMessage(ColorUtil.color("<##F10303> Group Chad Strength activated"));
        playStrengthCircleExpansion(targetLiving.getLocation(), 4.0, true);

        chadGroupActive.add(player.getUniqueId());
        chadGroupCounter.put(player.getUniqueId(), 0);

        for (Entity nearby : targetLiving.getNearbyEntities(4, 4, 4)) {
            if (!(nearby instanceof Player ally)) {
                continue;
            }
            if (!isTrusted(player, ally) && !ally.equals(player)) {
                continue;
            }

            chadGroupActive.add(ally.getUniqueId());
            chadGroupCounter.put(ally.getUniqueId(), 0);
            ally.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60 * 20, 0, false, false));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID id : new HashSet<>(chadGroupActive)) {
                chadGroupActive.remove(id);
                chadGroupCounter.remove(id);
            }
        }, 60L * 20L);
    }

    private void applyChadDamage(Player attacker, EntityDamageByEntityEvent event) {
        UUID id = attacker.getUniqueId();
        boolean critical = isCritical(attacker);

        if (chadSelfActive.contains(id)) {
            int count = chadSelfCounter.getOrDefault(id, 0);
            if (count < 3 && critical) {
                chadSelfCounter.put(id, count + 1);
            } else if (count >= 3) {
                event.setDamage(event.getDamage() * 2.0);
                chadSelfCounter.put(id, 0);
            }
        }

        if (chadGroupActive.contains(id)) {
            int count = chadGroupCounter.getOrDefault(id, 0);
            if (count < 8 && critical) {
                chadGroupCounter.put(id, count + 1);
            } else if (count >= 8) {
                event.setDamage(event.getDamage() * 2.0);
                chadGroupCounter.put(id, 0);
            }
        }
    }

    private void startChadParticlesTick() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID id = player.getUniqueId();

                    if (chadSelfActive.contains(id)) {
                        spawnChadSelfCounterParticles(player, chadSelfCounter.getOrDefault(id, 0));
                    }

                    if (chadGroupActive.contains(id)) {
                        int count = chadGroupCounter.getOrDefault(id, 0);
                        Location loc = player.getLocation().clone().add(0, 2.5, 0);
                        if (count >= 8) {
                            player.getWorld().spawnParticle(Particle.DUST, loc, 1, 0.0, 0.0, 0.0, 0.0, STRENGTH_DARK_RED_DUST, true);
                        } else {
                            player.getWorld().spawnParticle(Particle.SMOKE, loc, 1, 0.0, 0.0, 0.0, 0.01);
                        }
                    }

                    if (isNullified(player)) {
                        // Nullified players cannot use splash/lingering throw by right click handlers elsewhere.
                        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 10, 0, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void tryCancelPotionUseIfNullified(Player player, PlayerInteractEvent event) {
        if (!isNullified(player)) {
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isSplashOrLingering(held) || isSplashOrLingering(offhand)) {
            event.setCancelled(true);
        }
    }

    private boolean isSplashOrLingering(ItemStack item) {
        if (item == null) {
            return false;
        }
        return item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION;
    }

    private boolean isNullified(LivingEntity entity) {
        long until = nullifyUntil.getOrDefault(entity.getUniqueId(), 0L);
        if (until <= System.currentTimeMillis()) {
            nullifyUntil.remove(entity.getUniqueId());
            return false;
        }
        return true;
    }

    private void drawStrengthRing(Location center, double radius, boolean expStyle) {
        double spacing = expStyle ? 0.1 : 0.15;
        int particles = Math.max(12, (int) Math.floor((2.0 * Math.PI * radius) / spacing));

        for (int i = 0; i < particles; i++) {
            double angle = (i * 2.0 * Math.PI) / particles;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
            point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0.0, STRENGTH_RED_DUST, true);
            if (expStyle) {
                point.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, point, 1, 0, 0, 0, 0.0);
            } else {
                point.getWorld().spawnParticle(Particle.SMOKE, point, 1, 0, 0, 0, 0.0);
            }
        }
    }

    private void playStrengthCircleExpansion(Location center, double maxRadius, boolean expStyle) {
        Location stableCenter = center.clone();
        int steps = (int) Math.floor(maxRadius + maxRadius);

        new BukkitRunnable() {
            double radius = 0.5;
            int step = 0;

            @Override
            public void run() {
                if (step >= steps) {
                    cancel();
                    playStrengthFinalCircle(stableCenter, radius, expStyle);
                    return;
                }

                drawStrengthRing(stableCenter, radius, expStyle);
                radius += 0.5;
                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void playStrengthFinalCircle(Location center, double radius, boolean expStyle) {
        new BukkitRunnable() {
            int loops = 7;

            @Override
            public void run() {
                if (loops-- <= 0) {
                    cancel();
                    return;
                }
                drawStrengthRing(center, radius, expStyle);
            }
        }.runTaskTimer(plugin, 0L, 6L);
    }

    private void spawnChadSelfCounterParticles(Player player, int count) {
        Location origin = player.getLocation().clone().add(0, 1.1, 0);
        Vector forward = origin.getDirection().setY(0).normalize();
        if (forward.lengthSquared() == 0.0) {
            forward = new Vector(0, 0, 1);
        }
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();

        Location front = origin.clone().add(forward.clone().multiply(0.6));
        Location frontRight = origin.clone().add(forward.clone().multiply(0.4)).add(right.clone().multiply(0.5));
        Location frontLeft = origin.clone().add(forward.clone().multiply(0.4)).subtract(right.clone().multiply(0.5));

        spawnCounterPoint(player, front, count >= 2);
        spawnCounterPoint(player, frontRight, count >= 3);
        spawnCounterPoint(player, frontLeft, count >= 1);
    }

    private void spawnCounterPoint(Player player, Location loc, boolean red) {
        if (red) {
            player.getWorld().spawnParticle(Particle.DUST, loc, 1, 0.0, 0.0, 0.0, 0.0, STRENGTH_RED_DUST, true);
            return;
        }
        player.getWorld().spawnParticle(Particle.SMOKE, loc, 1, 0.0, 0.0, 0.0, 0.01);
    }

    private boolean isViewTitle(String current, String rawTitle) {
        String colored = ColorUtil.color(rawTitle);
        if (colored.equals(current)) {
            return true;
        }
        String strippedCurrent = ChatColor.stripColor(current);
        String strippedTarget = ChatColor.stripColor(colored);
        return strippedCurrent != null && strippedCurrent.equals(strippedTarget);
    }

    private long frailerCooldown(int energy) {
        return switch (energy) {
            case 1 -> FormatUtil.toMilliseconds(1, 50);
            case 2 -> FormatUtil.toMilliseconds(1, 45);
            case 3 -> FormatUtil.toMilliseconds(1, 40);
            case 4 -> FormatUtil.toMilliseconds(1, 35);
            default -> FormatUtil.toMilliseconds(1, 30);
        };
    }

    private long chadCooldown(int energy) {
        return switch (energy) {
            case 1 -> FormatUtil.toMilliseconds(2, 50);
            case 2 -> FormatUtil.toMilliseconds(2, 45);
            case 3 -> FormatUtil.toMilliseconds(2, 40);
            case 4 -> FormatUtil.toMilliseconds(2, 35);
            default -> FormatUtil.toMilliseconds(2, 30);
        };
    }

    private boolean isCritical(Player attacker) {
        return attacker.getFallDistance() > 0.0f
            && !attacker.isOnGround()
            && !attacker.isInsideVehicle()
            && !attacker.hasPotionEffect(PotionEffectType.BLINDNESS)
            && !attacker.isSprinting();
    }

    private boolean isStrengthGem(ItemStack item) {
        return GemUtil.isGem(item) && GemUtil.getGem(item) == GemType.STRENGTH;
    }

    private boolean isStrengthTier2WithEnergy(ItemStack item) {
        return isStrengthGem(item) && GemUtil.getTier(item) == GemTier.TIER_2 && GemUtil.getEnergyLevel(item) > 0;
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
        // Cross-gem disable state still pending centralization.
        return false;
    }
}
