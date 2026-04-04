package me.thesnowbound.blissDuels.listeners;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.gem.GemType;
import me.thesnowbound.blissDuels.managers.GemItemManager;
import me.thesnowbound.blissDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class GemSelectorListener implements Listener {
    private static final String MENU_TITLE_PREFIX = "&d ɢᴇᴍ sᴇʟᴇᴄᴛᴏʀ &7(T";

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

    private final BlissDuels plugin;

    public GemSelectorListener(BlissDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        GemItemManager manager = plugin.getGemItemManager();
        if (!manager.isGemSelectorItem(hand)) {
            return;
        }

        event.setCancelled(true);
        int tier = manager.getGemSelectorTier(hand);
        openSelectorMenu(player, tier);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (!isSelectorMenuTitle(title)) {
            return;
        }

        event.setCancelled(true);

        GemType selected = SLOT_TO_GEM.get(event.getRawSlot());
        if (selected == null) {
            return;
        }

        int tier = parseTierFromTitle(title);
        if (tier != 1 && tier != 2) {
            tier = 2;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        GemItemManager manager = plugin.getGemItemManager();
        if (!manager.isGemSelectorItem(hand)) {
            player.closeInventory();
            return;
        }

        int selectorTier = manager.getGemSelectorTier(hand);
        if (selectorTier != tier) {
            tier = selectorTier;
        }

        ItemStack gem = manager.getGem(selected, tier, 5);
        if (gem == null) {
            player.sendMessage(ColorUtil.color("&cFailed to create selected gem."));
            player.closeInventory();
            return;
        }

        consumeMainHand(player);
        player.getInventory().addItem(gem);
        player.sendMessage(ColorUtil.color("<##FFD773>Selected: <##B8FFFB>" + selected.getIdentifier() + " &7(T" + tier + ")"));
        player.closeInventory();
    }

    private void openSelectorMenu(Player player, int tier) {
        Inventory inv = Bukkit.createInventory(null, 27, ColorUtil.color(MENU_TITLE_PREFIX + tier + ")"));
        initializeBackdrop(inv);

        for (Map.Entry<Integer, GemType> entry : SLOT_TO_GEM.entrySet()) {
            ItemStack icon = plugin.getGemItemManager().getGem(entry.getValue(), tier, 5);
            if (icon != null) {
                inv.setItem(entry.getKey(), icon);
            }
        }

        player.openInventory(inv);
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

    private boolean isSelectorMenuTitle(String title) {
        if (title == null) {
            return false;
        }
        return title.startsWith(ColorUtil.color("&d ɢᴇᴍ sᴇʟᴇᴄᴛᴏʀ"));
    }

    private int parseTierFromTitle(String title) {
        String plain = title == null ? "" : org.bukkit.ChatColor.stripColor(title);
        if (plain.contains("(T1)")) {
            return 1;
        }
        if (plain.contains("(T2)")) {
            return 2;
        }
        return 2;
    }

    private void consumeMainHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            return;
        }

        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
            return;
        }

        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(hand);
    }
}
