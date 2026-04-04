package me.thesnowbound.blissDuels.commands;

import me.thesnowbound.blissDuels.listeners.FluxParityListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FluxChargeCommand implements CommandExecutor {
    private final FluxParityListener fluxParityListener;

    public FluxChargeCommand(FluxParityListener fluxParityListener) {
        this.fluxParityListener = fluxParityListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();

        if ("reset-charge".equals(name)) {
            fluxParityListener.resetAllCharges();
            sender.sendMessage("Reset kinetic and charge values for online players.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        switch (name) {
            case "watts" -> player.sendMessage(fluxParityListener.getWattsMessage(player));
            case "hiddenwatts" -> player.sendMessage(fluxParityListener.getHiddenWattsMessage(player));
            case "startcharging" -> fluxParityListener.startCharging(player);
            case "stopcharging" -> fluxParityListener.stopCharging(player);
            case "chargemenu" -> fluxParityListener.openChargeMenu(player);
            case "maxwatts" -> fluxParityListener.maxWatts(player);
            case "resetwatts" -> fluxParityListener.resetWatts(player);
            default -> {
                return false;
            }
        }
        return true;
    }
}
