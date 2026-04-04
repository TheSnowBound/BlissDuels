package me.thesnowbound.blissDuels;

import me.thesnowbound.blissDuels.commands.AbilityDebugCommand;
import me.thesnowbound.blissDuels.commands.FluxChargeCommand;
import me.thesnowbound.blissDuels.commands.GetGemCommand;
import me.thesnowbound.blissDuels.commands.GemCommand;
import me.thesnowbound.blissDuels.commands.TrustCommand;
import me.thesnowbound.blissDuels.listeners.AstraAbilityListener;
import me.thesnowbound.blissDuels.listeners.FireAbilityListener;
import me.thesnowbound.blissDuels.listeners.InventoryClickListener;
import me.thesnowbound.blissDuels.listeners.LifeAbilityListener;
import me.thesnowbound.blissDuels.listeners.NoDupeListener;
import me.thesnowbound.blissDuels.listeners.GemAutoGrantListener;
import me.thesnowbound.blissDuels.listeners.GemSelectorListener;
import me.thesnowbound.blissDuels.listeners.PassiveEffectListener;
import me.thesnowbound.blissDuels.listeners.PlayerDeathListener;
import me.thesnowbound.blissDuels.listeners.SpeedAbilityListener;
import me.thesnowbound.blissDuels.listeners.TraderUpgradeListener;
import me.thesnowbound.blissDuels.listeners.PuffAbilityListener;
import me.thesnowbound.blissDuels.listeners.WealthAbilityListener;
import me.thesnowbound.blissDuels.listeners.StrengthAbilityListener;
import me.thesnowbound.blissDuels.listeners.FluxAbilityListener;
import me.thesnowbound.blissDuels.listeners.FluxParityListener;
import me.thesnowbound.blissDuels.listeners.TrustGuiListener;
import me.thesnowbound.blissDuels.managers.CooldownConfigManager;
import me.thesnowbound.blissDuels.managers.CooldownManager;
import me.thesnowbound.blissDuels.managers.GemItemManager;
import me.thesnowbound.blissDuels.managers.TrustManager;
import me.thesnowbound.blissDuels.systems.ActionBarUpdater;
import me.thesnowbound.blissDuels.systems.GemSystem;
import me.thesnowbound.blissDuels.systems.TradeSystem;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BlissDuels extends JavaPlugin {
    private static BlissDuels instance;
    private GemItemManager gemItemManager;
    private CooldownManager cooldownManager;
    private CooldownConfigManager cooldownConfigManager;
    private TrustManager trustManager;
    private GemSystem gemSystem;
    private TradeSystem tradeSystem;
    private ActionBarUpdater actionBarUpdater;
    private FluxAbilityListener fluxAbilityListener;
    private FluxParityListener fluxParityListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("cooldowns.yml", false);
        saveResource("features.yml", false);

        // Initialize managers/systems.
        this.gemItemManager = new GemItemManager();
        this.cooldownConfigManager = new CooldownConfigManager(this);
        this.cooldownManager = new CooldownManager(cooldownConfigManager);
        this.trustManager = new TrustManager(this);
        this.gemSystem = new GemSystem(this);
        this.tradeSystem = new TradeSystem(this);
        this.actionBarUpdater = new ActionBarUpdater(this);
        this.fluxAbilityListener = new FluxAbilityListener(this);
        this.fluxParityListener = new FluxParityListener(this, fluxAbilityListener);

        // Register event listeners.
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new NoDupeListener(this), this);
        getServer().getPluginManager().registerEvents(new GemAutoGrantListener(this), this);
        getServer().getPluginManager().registerEvents(new GemSelectorListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryClickListener(this), this);
        getServer().getPluginManager().registerEvents(new PassiveEffectListener(), this);
        getServer().getPluginManager().registerEvents(new AstraAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new FireAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new SpeedAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(fluxAbilityListener, this);
        getServer().getPluginManager().registerEvents(fluxParityListener, this);
        getServer().getPluginManager().registerEvents(new PuffAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new LifeAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new WealthAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new StrengthAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new TraderUpgradeListener(this, gemSystem, tradeSystem), this);
        getServer().getPluginManager().registerEvents(new TrustGuiListener(this), this);

        registerCommands();
        actionBarUpdater.start();

        getLogger().info("BlissGem plugin enabled!");
        getLogger().info("Gem systems loaded: tiers, trader/upgrader, action bar, astra conversion");
    }

    @Override
    public void onDisable() {
        if (actionBarUpdater != null) {
            actionBarUpdater.stop();
        }
        if (trustManager != null) {
            trustManager.save();
        }
        getLogger().info("BlissGem plugin disabled!");
    }

    private void registerCommands() {
        GetGemCommand getGem = new GetGemCommand(this);
        PluginCommand getGemCommand = getCommand("getgem");
        if (getGemCommand != null) {
            getGemCommand.setExecutor(getGem);
            getGemCommand.setTabCompleter(getGem);
        }

        GemCommand gem = new GemCommand(this);
        PluginCommand gemCommand = getCommand("gem");
        if (gemCommand != null) {
            gemCommand.setExecutor(gem);
            gemCommand.setTabCompleter(gem);
        }

        AbilityDebugCommand abilityDebug = new AbilityDebugCommand(this);
        PluginCommand abilityDebugCommand = getCommand("dbg");
        if (abilityDebugCommand != null) {
            abilityDebugCommand.setExecutor(abilityDebug);
            abilityDebugCommand.setTabCompleter(abilityDebug);
        }

        TrustCommand trust = new TrustCommand(this);
        PluginCommand trustCommand = getCommand("trust");
        if (trustCommand != null) {
            trustCommand.setExecutor(trust);
            trustCommand.setTabCompleter(trust);
        }

        FluxChargeCommand fluxChargeCommand = new FluxChargeCommand(fluxParityListener);

        PluginCommand watts = getCommand("watts");
        if (watts != null) {
            watts.setExecutor(fluxChargeCommand);
        }

        PluginCommand hiddenWatts = getCommand("hiddenwatts");
        if (hiddenWatts != null) {
            hiddenWatts.setExecutor(fluxChargeCommand);
        }

        PluginCommand startCharging = getCommand("startcharging");
        if (startCharging != null) {
            startCharging.setExecutor(fluxChargeCommand);
        }

        PluginCommand stopCharging = getCommand("stopcharging");
        if (stopCharging != null) {
            stopCharging.setExecutor(fluxChargeCommand);
        }

        PluginCommand chargeMenu = getCommand("chargemenu");
        if (chargeMenu != null) {
            chargeMenu.setExecutor(fluxChargeCommand);
        }

        PluginCommand maxWatts = getCommand("maxwatts");
        if (maxWatts != null) {
            maxWatts.setExecutor(fluxChargeCommand);
        }

        PluginCommand resetWatts = getCommand("resetwatts");
        if (resetWatts != null) {
            resetWatts.setExecutor(fluxChargeCommand);
        }

        PluginCommand resetCharge = getCommand("reset-charge");
        if (resetCharge != null) {
            resetCharge.setExecutor(fluxChargeCommand);
        }


    }

    public static BlissDuels getInstance() {
        return instance;
    }

    public GemItemManager getGemItemManager() {
        return gemItemManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public GemSystem getGemSystem() {
        return gemSystem;
    }

    public TradeSystem getTradeSystem() {
        return tradeSystem;
    }

    public TrustManager getTrustManager() {
        return trustManager;
    }

    public CooldownConfigManager getCooldownConfigManager() {
        return cooldownConfigManager;
    }

    public FluxAbilityListener getFluxAbilityListener() {
        return fluxAbilityListener;
    }

    public FluxParityListener getFluxParityListener() {
        return fluxParityListener;
    }
}
