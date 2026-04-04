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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wealth gem conversion slice based on reference/Gems/wealth.sk.
 */
public class WealthAbilityListener implements Listener {
    private static final String POCKETS_TITLE = "Pockets";
    private static final Particle.DustOptions WEALTH_DUST = new Particle.DustOptions(Color.fromRGB(14, 201, 18), 1.0f);

    private final BlissDuels plugin;

    private final Map<UUID, Long> pocketsDoubleClickWindow = new HashMap<>();
    private final Map<UUID, ItemStack[]> pocketsData = new HashMap<>();
    private final Set<UUID> pocketsOpen = new HashSet<>();

    private final Map<UUID, Long> unfortunateUntil = new HashMap<>();
    private final Set<UUID> richRushActive = new HashSet<>();

    // Rich Rush timed inventory snapshots (+1 trusted / -1 untrusted enchant aura).
    private final Map<UUID, RichRushSnapshot> richRushUpgradeSnapshots = new HashMap<>();
    private final Map<UUID, Integer> richRushUpgradeTasks = new HashMap<>();
    private final Map<UUID, RichRushSnapshot> richRushDebuffSnapshots = new HashMap<>();
    private final Map<UUID, Integer> richRushDebuffTasks = new HashMap<>();

    public WealthAbilityListener(BlissDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onExp(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        if (!hasWealthWithEnergy(player.getInventory().getItemInOffHand(), 1)) {
            return;
        }

        // Skript doubles spawned experience for offhand wealth holders.
        event.setAmount(event.getAmount() * 2);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack held = player.getInventory().getItemInMainHand();
        Block block = event.getBlock();

        // Double ancient debris passive for tier2 offhand wealth.
        if (hasWealthWithEnergy(offhand, 1)
            && GemUtil.getTier(offhand) == GemTier.TIER_2
            && block.getType() == Material.ANCIENT_DEBRIS) {
            event.setDropItems(false);
            block.setType(Material.AIR);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.NETHERITE_SCRAP, 2));
            return;
        }

        // Bonus ore drop passive (25%) from offhand wealth; excludes ancient debris.
        if (hasWealthWithEnergy(offhand, 1)
            && isOre(block.getType())
            && block.getType() != Material.ANCIENT_DEBRIS
            && ThreadLocalRandom.current().nextInt(100) < 25) {
            for (ItemStack drop : block.getDrops(held, player)) {
                block.getWorld().dropItemNaturally(block.getLocation(), drop.clone());
            }
        }

        // Rich Rush ore bonus mode.
        if (richRushActive.contains(player.getUniqueId())
            && isOre(block.getType())
            && block.getType() != Material.ANCIENT_DEBRIS) {
            block.getWorld().spawnParticle(Particle.DUST, block.getLocation().add(0.5, 0.5, 0.5), 30, 0.15, 0.15, 0.15, 0.01, WEALTH_DUST);

            if (held.containsEnchantment(Enchantment.SILK_TOUCH)) {
                event.setDropItems(false);
                return;
            }

            for (ItemStack drop : block.getDrops(held, player)) {
                block.getWorld().dropItemNaturally(block.getLocation(), drop.clone());
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        // Unfortunate curse causes 33% failure on attacks.
        if (isUnfortunate(attacker) && ThreadLocalRandom.current().nextInt(100) < 33) {
            event.setCancelled(true);
            attacker.playSound(attacker.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            return;
        }

        // Durability chip passive from offhand wealth if sprinting or critical.
        ItemStack offhand = attacker.getInventory().getItemInOffHand();
        if (hasWealthWithEnergy(offhand, 1) && (attacker.isSprinting() || isCritical(attacker))) {
            applyDurabilityChip(event.getEntity());
        }

        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        // Single-target Unfortunate on melee with held wealth tier2.
        ItemStack held = attacker.getInventory().getItemInMainHand();
        if (!isWealthTier2WithEnergy(held)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(attacker, "Unfortunate")) {
            return;
        }

        int energy = GemUtil.getEnergyLevel(held);
        plugin.getCooldownManager().setCooldown(attacker, "Unfortunate", unfortunateCooldown(energy));
        applyUnfortunate(victim, 40L * 20L);

        attacker.sendMessage(ColorUtil.color("<##0EC912> Unfortunate used on " + safeName(victim)));
        if (victim instanceof Player victimPlayer) {
            victimPlayer.sendMessage(ColorUtil.color("<##0EC912> You have been affected with Unfortunate"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) {
            return;
        }
        if (isUnfortunate(shooter) && ThreadLocalRandom.current().nextInt(100) < 33) {
            event.setCancelled(true);
            shooter.playSound(shooter.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        Player player = event.getPlayer();

        // Unfortunate curse causes 33% failure on clicks.
        if (isUnfortunate(player) && ThreadLocalRandom.current().nextInt(100) < 33) {
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            return;
        }

        if (action.isLeftClick()) {
            tryGroupItemLock(player);
            return;
        }

        // Rich Rush must be right-click air/block. Entity right-click is handled in onInteractEntity (Amplification).
        if (!action.isRightClick()) {
            return;
        }

        // Pockets: sneaking double-click while offhand wealth and held air/sword/axe.
        tryOpenPockets(player);

        // Rich Rush self mode on right-click air/block.
        tryRichRush(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof LivingEntity target)) {
            return;
        }

        // Amplification targeted mode uses RichRush cooldown key.
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isWealthTier2WithEnergy(held)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(player, "RichRush")) {
            return;
        }

        int energy = GemUtil.getEnergyLevel(held);
        plugin.getCooldownManager().setCooldown(player, "RichRush", amplificationCooldown(energy));

        player.sendMessage(ColorUtil.color("<##0EC912> Amplification cast on " + safeName(target)));
        applyAmplification(player, target.getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        if (!richRushActive.contains(killer.getUniqueId())) {
            return;
        }

        String type = event.getEntityType().name();
        if (type.equals("PLAYER") || type.equals("MULE") || type.equals("DONKEY") || type.equals("LLAMA") || type.equals("TRADER_LLAMA") || type.equals("FOX")) {
            return;
        }

        // RichRush extra mob drops.
        event.getDrops().addAll(event.getDrops().stream().map(ItemStack::clone).toList());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!POCKETS_TITLE.equals(event.getView().getTitle())) {
            return;
        }

        UUID uuid = player.getUniqueId();
        pocketsOpen.remove(uuid);
        Inventory inv = event.getInventory();
        ItemStack[] snapshot = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            snapshot[i] = item == null ? null : item.clone();
        }
        pocketsData.put(uuid, snapshot);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        restoreRichRushUpgrade(uuid);
        restoreRichRushDebuff(uuid);
        pocketsOpen.remove(uuid);
        pocketsDoubleClickWindow.remove(uuid);
    }

    private void tryGroupItemLock(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isWealthTier2WithEnergy(held)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(player, "Unfortunate")) {
            return;
        }

        int energy = GemUtil.getEnergyLevel(held);
        plugin.getCooldownManager().setCooldown(player, "Unfortunate", unfortunateCooldown(energy));

        player.sendMessage(ColorUtil.color("<##0EC912> Item Lock activated"));
        for (Entity nearby : player.getNearbyEntities(2.0, 2.0, 2.0)) {
            if (!(nearby instanceof Player target) || target.equals(player)) {
                continue;
            }

            ItemStack targetHeld = target.getInventory().getItemInMainHand();
            if (targetHeld == null || targetHeld.getType().isAir()) {
                continue;
            }
            if (targetHeld.getType() == Material.PRISMARINE_SHARD || targetHeld.getType() == Material.AMETHYST_SHARD) {
                continue;
            }

            target.setCooldown(targetHeld.getType(), 30 * 20);
            target.sendMessage(ColorUtil.color("<##0EC912> Your held item has been locked for 30 seconds"));
        }
    }

    private void tryOpenPockets(Player player) {
        if (!player.isSneaking()) {
            return;
        }
        if (!isWealthGem(player.getInventory().getItemInOffHand())) {
            return;
        }
        if (GemUtil.getEnergyLevel(player.getInventory().getItemInOffHand()) <= 0) {
            return;
        }
        if (!isSwordAxeOrAir(player.getInventory().getItemInMainHand())) {
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long expires = pocketsDoubleClickWindow.getOrDefault(uuid, 0L);

        if (now > expires) {
            pocketsDoubleClickWindow.put(uuid, now + 700L);
            return;
        }

        pocketsDoubleClickWindow.remove(uuid);
        if (pocketsOpen.contains(uuid)) {
            return;
        }

        Inventory pockets = Bukkit.createInventory(player, 9, POCKETS_TITLE);
        ItemStack[] saved = pocketsData.get(uuid);
        if (saved != null) {
            for (int i = 0; i < Math.min(saved.length, 9); i++) {
                if (saved[i] != null) {
                    pockets.setItem(i, saved[i].clone());
                }
            }
        }

        pocketsOpen.add(uuid);
        player.openInventory(pockets);
    }

    private void tryRichRush(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isWealthTier2WithEnergy(held)) {
            return;
        }
        if (!plugin.getCooldownManager().isCooldownReady(player, "RichRush")) {
            return;
        }

        int energy = GemUtil.getEnergyLevel(held);
        plugin.getCooldownManager().setCooldown(player, "RichRush", richRushCooldown(energy));

        Set<Player> trustedTargets = new HashSet<>();
        Set<Player> untrustedTargets = new HashSet<>();
        trustedTargets.add(player);

        for (Entity nearby : player.getNearbyEntities(5.0, 5.0, 5.0)) {
            if (!(nearby instanceof Player other)) {
                continue;
            }
            if (other.equals(player) || isTrusted(player, other)) {
                trustedTargets.add(other);
            } else {
                untrustedTargets.add(other);
            }
        }

        for (Player trusted : trustedTargets) {
            applyRichRushUpgrade(trusted);
        }
        for (Player untrusted : untrustedTargets) {
            applyRichRushDebuff(untrusted);
        }

        player.sendMessage(ColorUtil.color("<##0EC912> Rich Rush activated: trusted +1 enchants, untrusted -1 enchants for 50s"));
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0.01);
    }

    private void applyRichRushUpgrade(Player target) {
        UUID uuid = target.getUniqueId();
        restoreRichRushUpgrade(uuid);

        richRushUpgradeSnapshots.put(uuid, RichRushSnapshot.capture(target.getInventory()));
        modifyAllEnchantments(target.getInventory(), 1);
        target.addScoreboardTag("wealthupgrade");

        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> restoreRichRushUpgrade(uuid), 50L * 20L);
        richRushUpgradeTasks.put(uuid, taskId);
    }

    private void applyRichRushDebuff(Player target) {
        UUID uuid = target.getUniqueId();
        restoreRichRushDebuff(uuid);

        richRushDebuffSnapshots.put(uuid, RichRushSnapshot.capture(target.getInventory()));
        modifyAllEnchantments(target.getInventory(), -1);

        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> restoreRichRushDebuff(uuid), 50L * 20L);
        richRushDebuffTasks.put(uuid, taskId);
    }

    private void restoreRichRushUpgrade(UUID uuid) {
        Integer task = richRushUpgradeTasks.remove(uuid);
        if (task != null) {
            Bukkit.getScheduler().cancelTask(task);
        }

        RichRushSnapshot snapshot = richRushUpgradeSnapshots.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || snapshot == null) {
            return;
        }

        snapshot.restore(player.getInventory());
        player.removeScoreboardTag("wealthupgrade");
    }

    private void restoreRichRushDebuff(UUID uuid) {
        Integer task = richRushDebuffTasks.remove(uuid);
        if (task != null) {
            Bukkit.getScheduler().cancelTask(task);
        }

        RichRushSnapshot snapshot = richRushDebuffSnapshots.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || snapshot == null) {
            return;
        }

        snapshot.restore(player.getInventory());
    }

    private void modifyAllEnchantments(PlayerInventory inventory, int delta) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir() || item.getEnchantments().isEmpty()) {
                continue;
            }

            Map<Enchantment, Integer> enchants = new HashMap<>(item.getEnchantments());
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                Enchantment enchantment = entry.getKey();
                int newLevel = entry.getValue() + delta;

                item.removeEnchantment(enchantment);
                if (newLevel > 0) {
                    item.addUnsafeEnchantment(enchantment, newLevel);
                }
            }
            inventory.setItem(slot, item);
        }
    }

    private record RichRushSnapshot(ItemStack[] contents) {
        private static RichRushSnapshot capture(PlayerInventory inventory) {
            ItemStack[] source = inventory.getContents();
            ItemStack[] clone = new ItemStack[source.length];
            for (int i = 0; i < source.length; i++) {
                clone[i] = source[i] == null ? null : source[i].clone();
            }
            return new RichRushSnapshot(clone);
        }

        private void restore(PlayerInventory inventory) {
            for (int i = 0; i < contents.length && i < inventory.getSize(); i++) {
                ItemStack item = contents[i];
                inventory.setItem(i, item == null ? null : item.clone());
            }
        }
    }

    private void applyAmplification(Player caster, Location center) {
        drawWealthRing(center, 2.5);

        for (Entity nearby : center.getWorld().getNearbyEntities(center, 2.5, 2.5, 2.5)) {
            if (!(nearby instanceof Player target)) {
                continue;
            }
            if (!isTrusted(caster, target) && !target.equals(caster)) {
                continue;
            }

            target.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH, 50 * 20, 0, false, false));
            target.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 50 * 20, 0, false, false));
            target.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.HASTE, 50 * 20, 0, false, false));
            target.sendMessage(ColorUtil.color("<##0EC912> You have been affected with Amplification"));
        }
    }

    private void drawWealthRing(Location center, double radius) {
        for (int i = 0; i < 72; i++) {
            double angle = Math.toRadians(i * 5);
            Location point = center.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
            point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0.01, WEALTH_DUST);
            point.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, point, 1, 0, 0, 0, 0.01);
        }
    }

    private void applyUnfortunate(LivingEntity target, long durationTicks) {
        unfortunateUntil.put(target.getUniqueId(), System.currentTimeMillis() + (durationTicks * 50L));
    }

    private boolean isUnfortunate(Player player) {
        long until = unfortunateUntil.getOrDefault(player.getUniqueId(), 0L);
        if (until <= System.currentTimeMillis()) {
            unfortunateUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private void applyDurabilityChip(Entity victim) {
        if (!(victim instanceof Player target)) {
            return;
        }

        PlayerInventory inv = target.getInventory();
        chipArmorPiece(inv.getHelmet());
        chipArmorPiece(inv.getChestplate());
        chipArmorPiece(inv.getLeggings());
        chipArmorPiece(inv.getBoots());
    }

    private void chipArmorPiece(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof Damageable damageable)) {
            return;
        }
        damageable.setDamage(damageable.getDamage() + 2);
        item.setItemMeta(damageable);
    }

    private boolean isCritical(Player attacker) {
        return attacker.getFallDistance() > 0.0f
            && !attacker.isOnGround()
            && !attacker.isInsideVehicle()
            && !attacker.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS)
            && !attacker.isSprinting();
    }

    private boolean isOre(Material type) {
        return switch (type) {
            case COAL_ORE, DEEPSLATE_COAL_ORE,
                IRON_ORE, DEEPSLATE_IRON_ORE,
                COPPER_ORE, DEEPSLATE_COPPER_ORE,
                GOLD_ORE, DEEPSLATE_GOLD_ORE,
                DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE,
                LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                NETHER_QUARTZ_ORE, NETHER_GOLD_ORE,
                ANCIENT_DEBRIS -> true;
            default -> false;
        };
    }

    private long unfortunateCooldown(int energy) {
        return switch (energy) {
            case 1 -> FormatUtil.toMilliseconds(1, 30);
            case 2 -> FormatUtil.toMilliseconds(1, 25);
            case 3 -> FormatUtil.toMilliseconds(1, 20);
            case 4 -> FormatUtil.toMilliseconds(1, 15);
            default -> FormatUtil.toMilliseconds(1, 10);
        };
    }

    private long richRushCooldown(int energy) {
        return switch (energy) {
            case 1 -> FormatUtil.toMilliseconds(10, 40);
            case 2 -> FormatUtil.toMilliseconds(10, 30);
            case 3 -> FormatUtil.toMilliseconds(10, 20);
            case 4 -> FormatUtil.toMilliseconds(10, 10);
            default -> FormatUtil.toMilliseconds(10, 0);
        };
    }

    private long amplificationCooldown(int energy) {
        if (energy <= 2) {
            return FormatUtil.toMilliseconds(3, 40);
        }
        if (energy <= 4) {
            return FormatUtil.toMilliseconds(3, 30);
        }
        if (energy <= 7) {
            return FormatUtil.toMilliseconds(3, 20);
        }
        if (energy == 8) {
            return FormatUtil.toMilliseconds(3, 10);
        }
        return FormatUtil.toMilliseconds(3, 0);
    }

    private boolean hasWealthWithEnergy(ItemStack item, int minEnergy) {
        return isWealthGem(item) && GemUtil.getEnergyLevel(item) >= minEnergy;
    }

    private boolean isWealthTier2WithEnergy(ItemStack item) {
        return isWealthGem(item) && GemUtil.getTier(item) == GemTier.TIER_2 && GemUtil.getEnergyLevel(item) > 0;
    }

    private boolean isWealthGem(ItemStack item) {
        return GemUtil.isGem(item) && GemUtil.getGem(item) == GemType.WEALTH;
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









