package me.thesnowbound.blissDuels.listeners;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.gem.GemType;
import me.thesnowbound.blissDuels.systems.GemSystem;
import me.thesnowbound.blissDuels.systems.TradeSystem;
import me.thesnowbound.blissDuels.util.ColorUtil;
import me.thesnowbound.blissDuels.util.GemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles trader and upgrader item interactions from the Skript conversion.
 */
public class TraderUpgradeListener implements Listener {
    private static final String TRADER_MENU_TITLE = "&b ᴜʟᴛɪᴍᴀᴛᴇ ᴛʀᴀᴅɪɴɢ ᴍᴇɴᴜ";

    private static final Map<Integer, GemType> SLOT_TO_GEM = Map.of(
        9, GemType.LIFE,
        10, GemType.STRENGTH,
        11, GemType.FIRE,
        12, GemType.SPEED,
        14, GemType.WEALTH,
        15, GemType.FLUX,
        16, GemType.ASTRA,
        17, GemType.PUFF
    );

    private static final Map<GemType, Integer> GEM_TO_SLOT = Map.of(
        GemType.LIFE, 9,
        GemType.STRENGTH, 10,
        GemType.FIRE, 11,
        GemType.SPEED, 12,
        GemType.WEALTH, 14,
        GemType.FLUX, 15,
        GemType.ASTRA, 16,
        GemType.PUFF, 17
    );

    private final BlissDuels plugin;
    private final GemSystem gemSystem;
    private final TradeSystem tradeSystem;
    private final Map<UUID, TraderSession> sessions = new java.util.HashMap<>();

    public TraderUpgradeListener(BlissDuels plugin, GemSystem gemSystem, TradeSystem tradeSystem) {
        this.plugin = plugin;
        this.gemSystem = gemSystem;
        this.tradeSystem = tradeSystem;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() && !event.getAction().isLeftClick()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (plugin.getGemItemManager().isUpgraderItem(hand)
            && event.getAction().isRightClick()) {
            event.setCancelled(true);
            if (upgradeFirstTierOneGem(player)) {
                decrementHand(player);
            } else {
                player.sendMessage(ColorUtil.color("<##FFD773> You need a tier 1 gem to use this upgrader."));
            }
            return;
        }

        if (!plugin.getGemItemManager().isTraderItem(hand)) {
            return;
        }

        event.setCancelled(true);
        if (event.getAction().isRightClick()) {
            if (tradeSystem.getGemCount(player) <= 0) {
                player.sendMessage(ColorUtil.color("<##FFD773> You do not have a gem to trade."));
                return;
            }
            decrementHand(player);
            gemSystem.randomGem(player);
            return;
        }

        openTraderMenu(player);
    }

    @EventHandler()
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!isTraderMenuTitle(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);
        TraderSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }

        GemType clickedType = SLOT_TO_GEM.get(event.getRawSlot());
        if (clickedType == null) {
            return;
        }

        // Current gem is not removable in the Skript menu.
        if (clickedType == session.currentType()) {
            return;
        }

        // Already eliminated option.
        if (!session.available().contains(clickedType)) {
            return;
        }

        // Skript increments required energy twice per click branch while reducing atenergy once.
        session.energyRequired(session.energyRequired() + 2);
        session.remainingEnergy(Math.max(0, session.remainingEnergy() - 1));

        session.available().remove(clickedType);
        refreshTraderMenu(player, session);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!isTraderMenuTitle(event.getView().getTitle())) {
            return;
        }

        TraderSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (session.available().isEmpty()) {
            player.sendMessage(ColorUtil.color("<##FFD773> Ultimate trade cancelled."));
            return;
        }

        GemType[] options = session.available().toArray(new GemType[0]);
        GemType picked = options[ThreadLocalRandom.current().nextInt(options.length)];

        ItemStack replacement = plugin.getGemItemManager().getGem(picked, session.tier(), session.remainingEnergy());
        if (replacement == null) {
            player.sendMessage(ColorUtil.color("<##FFD773> Trade failed. Try again."));
            return;
        }

        replaceFirstGem(player, replacement);
        player.sendMessage(ColorUtil.color("<##FFD773> Ultimate trade result: <##B8FFFB>" + picked.getIdentifier()
            + " <##FFD773>(energy " + session.remainingEnergy() + ")"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private boolean upgradeFirstTierOneGem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (!GemUtil.isGem(item)) {
                continue;
            }
            return tradeSystem.upgradeGem(player, item);
        }
        return false;
    }

    private void openTraderMenu(Player player) {
        ItemStack currentGem = tradeSystem.getPlayerGem(player);
        if (currentGem == null) {
            player.sendMessage(ColorUtil.color("<##FFD773> You do not have a gem to trade."));
            return;
        }

        GemType currentType = GemUtil.getGem(currentGem);
        int tier = GemUtil.getTier(currentGem).getLevel();
        int energy = GemUtil.getEnergyLevel(currentGem);

        if (currentType == null || tier != 2) {
            player.sendMessage(ColorUtil.color("<##FFD773> You need a tier 2 gem to use ultimate trading."));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, ColorUtil.color(TRADER_MENU_TITLE));
        TraderSession session = new TraderSession(currentType, tier, energy, energy, 0, EnumSet.noneOf(GemType.class));

        for (GemType type : GEM_TO_SLOT.keySet()) {
            if (type != currentType) {
                session.available().add(type);
            }
        }

        sessions.put(player.getUniqueId(), session);
        initializeBackdrop(inv);
        refreshTraderMenu(inv, session);
        player.openInventory(inv);
    }

    private void refreshTraderMenu(Player player, TraderSession session) {
        Inventory top = player.getOpenInventory().getTopInventory();
        refreshTraderMenu(top, session);
    }

    private void refreshTraderMenu(Inventory inv, TraderSession session) {
        int availableCount = session.available().size();
        int chance = availableCount <= 0 ? 0 : (int) Math.floor(100.0 / availableCount);

        for (Map.Entry<GemType, Integer> entry : GEM_TO_SLOT.entrySet()) {
            GemType type = entry.getKey();
            int slot = entry.getValue();

            boolean isCurrent = type == session.currentType();
            boolean available = session.available().contains(type);

            int displayEnergy = isCurrent ? session.startEnergy() : session.remainingEnergy();
            ItemStack icon = plugin.getGemItemManager().getGem(type, session.tier(), displayEnergy);
            if (icon == null) {
                continue;
            }

            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                java.util.List<String> lore = meta.getLore();
                if (lore == null) {
                    lore = new java.util.ArrayList<>();
                }

                lore.add("&f");
                if (isCurrent) {
                    lore.add("&cCurrent Gem");
                    lore.add("&7Chance: &f2%");
                } else if (available) {
                    lore.add("&aClick to remove from pool");
                    lore.add("&7Chance: &f" + chance + "%");
                } else {
                    lore.add("&cRemoved from pool");
                    lore.add("&7Chance: &f0%");
                }
                meta.setLore(ColorUtil.color(lore));
                icon.setItemMeta(meta);
            }

            inv.setItem(slot, icon);
        }

        inv.setItem(13, buildEnergyStatus(session));
    }

    private ItemStack buildEnergyStatus(TraderSession session) {
        ItemStack status = new ItemStack(Material.AMETHYST_SHARD, Math.max(1, Math.min(64, session.energyRequired() == 0 ? 1 : session.energyRequired())));
        ItemMeta meta = status.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color("<##FFD773>Energy Status"));
            meta.setLore(ColorUtil.color(java.util.List.of(
                "&7Energy Required: &f" + session.energyRequired(),
                "&7Starting Energy: &f" + session.startEnergy(),
                "&7Resulting Energy: &f" + session.remainingEnergy()
            )));
            status.setItemMeta(meta);
        }
        return status;
    }

    private void initializeBackdrop(Inventory inv) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    private void replaceFirstGem(Player player, ItemStack replacement) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (GemUtil.isGem(item)) {
                player.getInventory().setItem(i, replacement);
                return;
            }
        }
    }

    private boolean isTraderMenuTitle(String title) {
        return title != null && title.equals(ColorUtil.color(TRADER_MENU_TITLE));
    }

    private void decrementHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        if (item.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
            return;
        }
        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItemInMainHand(item);
    }

    private record TraderSession(
        GemType currentType,
        int tier,
        int startEnergy,
        int[] mutable,
        Set<GemType> available
    ) {
        private TraderSession(GemType currentType, int tier, int startEnergy, int remainingEnergy, int energyRequired, Set<GemType> available) {
            this(currentType, tier, startEnergy, new int[] {remainingEnergy, energyRequired}, available);
        }

        int remainingEnergy() {
            return mutable[0];
        }

        void remainingEnergy(int value) {
            mutable[0] = value;
        }

        int energyRequired() {
            return mutable[1];
        }

        void energyRequired(int value) {
            mutable[1] = value;
        }
    }
}
