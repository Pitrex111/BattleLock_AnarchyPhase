package com.jellypudding.battleLock;

import com.jellypudding.battleLock.listeners.CombatListener;
import com.jellypudding.battleLock.listeners.CommandListener;
import com.jellypudding.battleLock.listeners.PlayerListener;
import com.jellypudding.battleLock.managers.CombatManager;
import com.jellypudding.battleLock.managers.CombatLogManager;
import com.jellypudding.battleLock.managers.DataManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class BattleLock extends JavaPlugin {
    
    private CombatManager combatManager;
    private CombatLogManager combatLogManager;
    private DataManager dataManager;
    private boolean discordRelayAPIReady = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.dataManager = new DataManager(this);
        this.combatManager = new CombatManager(this);
        this.combatLogManager = new CombatLogManager(this, combatManager, dataManager);

        getServer().getPluginManager().registerEvents(new CombatListener(this, combatManager), this);
        getServer().getPluginManager().registerEvents(new CommandListener(this, combatManager), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this, combatManager, combatLogManager), this);

        // Check for DiscordRelay integration
        if (getServer().getPluginManager().isPluginEnabled("DiscordRelay")) {
            try {
                this.discordRelayAPIReady = com.jellypudding.discordRelay.DiscordRelayAPI.isReady();
                if (this.discordRelayAPIReady) {
                    getLogger().info("Successfully hooked into DiscordRelay.");
                } else {
                    getLogger().warning("DiscordRelay is loaded, but its API reported not ready (check DiscordRelay config/status).");
                }
            } catch (NoClassDefFoundError e) {
                getLogger().severe("DiscordRelay plugin found, but its API class (DiscordRelayAPI) is incompatible or missing.");
                this.discordRelayAPIReady = false;
            } catch (Exception e) {
                getLogger().log(java.util.logging.Level.SEVERE, "An unexpected error occurred while checking DiscordRelay API.", e);
                this.discordRelayAPIReady = false;
            }
        } else {
            getLogger().info("DiscordRelay plugin not found. Discord integration disabled.");
        }

        // Initialise bStats
        int pluginId = 27551;
        new Metrics(this, pluginId);

        getLogger().info("BattleLock has been enabled! Combat logging protection is now active.");
    }

    @Override
    public void onDisable() {
        if (combatLogManager != null) {
            combatLogManager.removeAllCombatLogs();
        }
        
        getLogger().info("BattleLock has been disabled.");
    }
    
    public CombatManager getCombatManager() {
        return combatManager;
    }
    
    public CombatLogManager getCombatLogManager() {
        return combatLogManager;
    }
    
    public DataManager getDataManager() {
        return dataManager;
    }

    public boolean isDiscordRelayAPIReady() {
        return discordRelayAPIReady;
    }
}
