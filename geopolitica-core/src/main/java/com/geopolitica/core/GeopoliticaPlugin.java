package com.geopolitica.core;

import com.geopolitica.api.service.ClaimService;
import com.geopolitica.api.service.NationService;
import com.geopolitica.api.service.TownService;
import com.geopolitica.core.command.GAdminCommand;
import com.geopolitica.core.command.NationCommand;
import com.geopolitica.core.command.NationTabCompleter;
import com.geopolitica.core.command.TownCommand;
import com.geopolitica.core.command.TownTabCompleter;
import com.geopolitica.core.config.ConfigManager;
import com.geopolitica.core.economy.EconomyHook;
import com.geopolitica.core.gui.GuiListener;
import com.geopolitica.core.listener.ClaimProtectionListener;
import com.geopolitica.core.listener.PlayerJoinListener;
import com.geopolitica.core.service.ClaimServiceImpl;
import com.geopolitica.core.service.NationServiceImpl;
import com.geopolitica.core.service.TownServiceImpl;
import com.geopolitica.core.storage.DataStore;
import com.geopolitica.core.storage.LoadResult;
import com.geopolitica.core.storage.SQLiteDataStore;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class GeopoliticaPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private DataStore dataStore;
    private EconomyHook economyHook;
    private TownServiceImpl townService;
    private ClaimServiceImpl claimService;
    private NationServiceImpl nationService;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();

        economyHook = new EconomyHook(this);
        boolean vaultFound = economyHook.setup();
        getLogger().info(vaultFound ? "Hooked into Vault for economy support." : "Vault not found; costs/upkeep/bank deposits are disabled.");

        if (!configManager.getStorageType().equalsIgnoreCase("sqlite")) {
            getLogger().warning("storage.type '" + configManager.getStorageType() + "' is not implemented yet; falling back to sqlite.");
        }
        dataStore = new SQLiteDataStore(this);

        LoadResult loaded;
        try {
            dataStore.init();
            loaded = dataStore.loadAll();
        } catch (Exception e) {
            getLogger().severe("Failed to initialize storage; disabling.");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Loaded " + loaded.towns().size() + " towns, " + loaded.residents().size()
                + " residents and " + loaded.claims().size() + " claims.");

        townService = new TownServiceImpl(this, dataStore, configManager, loaded);
        claimService = new ClaimServiceImpl(this, dataStore, configManager, loaded);
        townService.setClaimService(claimService);
        nationService = new NationServiceImpl(this, dataStore, configManager, loaded);

        getServer().getServicesManager().register(TownService.class, townService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(ClaimService.class, claimService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(NationService.class, nationService, this, ServicePriority.Normal);

        GuiListener guiListener = new GuiListener();
        getServer().getPluginManager().registerEvents(guiListener, this);

        TownCommand townCommand = new TownCommand(townService, claimService, nationService, economyHook, guiListener);
        getCommand("town").setExecutor(townCommand);
        getCommand("town").setTabCompleter(new TownTabCompleter(townService));
        getCommand("gadmin").setExecutor(new GAdminCommand(this, townService, nationService));

        NationCommand nationCommand = new NationCommand(townService, claimService, nationService, economyHook, guiListener);
        getCommand("nation").setExecutor(nationCommand);
        getCommand("nation").setTabCompleter(new NationTabCompleter(nationService));

        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(claimService), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(townService), this);

        getLogger().info("Geopolitica enabled.");
    }

    @Override
    public void onDisable() {
        if (dataStore != null) {
            dataStore.close();
        }
        getLogger().info("Geopolitica disabled.");
    }

    public void reloadPluginConfig() {
        configManager.load();
    }
}
