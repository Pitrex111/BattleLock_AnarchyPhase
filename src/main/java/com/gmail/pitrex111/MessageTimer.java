package com.gmail.pitrex111;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.jellypudding.battleLock.managers.CombatManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class MessageTimer extends BukkitRunnable {

    JavaPlugin root;
    CombatManager combatManager;
    public MessageTimer(JavaPlugin root, CombatManager cm)
    {
        this.root = root;
        this.combatManager = cm;
    }

    @Override
    public void run() {
        for (UUID u : combatManager.getTaggedPlayers())
        {
            Player player = root.getServer().getPlayer(u);
            if (player != null && combatManager.isPlayerTagged(player))
            {
                player.sendActionBar(Component.text("Combat: " + combatManager.getTimeUntilTagExpires(player) + " s.").color(NamedTextColor.YELLOW));
            }
        }
    }

}
