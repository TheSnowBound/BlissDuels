package me.thesnowbound.blissDuels.listeners;

import me.thesnowbound.blissDuels.BlissDuels;
import me.thesnowbound.blissDuels.gem.GemType;
import me.thesnowbound.blissDuels.util.ColorUtil;
import me.thesnowbound.blissDuels.util.GemUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class GemAutoGrantListener implements Listener {
    private final BlissDuels plugin;

    public GemAutoGrantListener(BlissDuels plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        tryGrantIfGemless(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // Delay one tick so inventory state is finalized after death/respawn cycle.
        plugin.getServer().getScheduler().runTask(plugin, () -> tryGrantIfGemless(event.getPlayer()));
    }

    private void tryGrantIfGemless(Player player) {
        if (!plugin.getConfig().getBoolean("auto-gem.enabled", true)) {
            return;
        }
        if (hasAnyGem(player)) {
            return;
        }

        int tier = resolveConfiguredTier();
        ItemStack gem = createRandomGem(tier);
        if (gem == null) {
            return;
        }

        player.getInventory().addItem(gem);
        player.sendMessage(ColorUtil.color("<##FFD773>You were gemless, so you received a tier " + tier + " gem."));
    }

    private boolean hasAnyGem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (GemUtil.isGem(item)) {
                return true;
            }
        }
        return false;
    }

    private int resolveConfiguredTier() {
        int tier = plugin.getConfig().getInt("auto-gem.tier", 2);
        return tier == 1 ? 1 : 2;
    }

    private ItemStack createRandomGem(int tier) {
        GemType[] types = GemType.values();
        GemType type = types[ThreadLocalRandom.current().nextInt(types.length)];
        return plugin.getGemItemManager().getGem(type, tier, 5);
    }
}

