package com.jellypudding.battleLock;

import com.gmail.pitrex111.MessageTimer;
import com.jellypudding.battleLock.listeners.CombatListener;
import com.jellypudding.battleLock.listeners.PlayerListener;
import com.jellypudding.battleLock.managers.CombatManager;
import com.jellypudding.battleLock.managers.CombatLogManager;
import com.jellypudding.battleLock.managers.DataManager;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class BattleLock extends JavaPlugin {
    
    private CombatManager combatManager;
    private CombatLogManager combatLogManager;
    private DataManager dataManager;
    private MessageTimer messageTimer;
    private CombatListener combatListener;
    private PlayerListener playerListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.dataManager = new DataManager(this);
        this.combatManager = new CombatManager(this);
        this.combatLogManager = new CombatLogManager(this, combatManager, dataManager);
        this.messageTimer = new MessageTimer(this, combatManager);

        combatListener = new CombatListener(this, combatManager);
        getServer().getPluginManager().registerEvents(combatListener, this);
        playerListener = new PlayerListener(this, combatManager, combatLogManager);
        getServer().getPluginManager().registerEvents(playerListener, this);
        messageTimer.runTaskTimer(this, 0, 10);

        getLogger().info("BattleLock has been enabled! Combat logging protection is now active.");
    }

    @Override
    public void onDisable() {
        if (combatLogManager != null) {
            combatLogManager.removeAllCombatLogs();
        }
        
        HandlerList.unregisterAll(combatListener);
        HandlerList.unregisterAll(playerListener);
        messageTimer.cancel();
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
}
