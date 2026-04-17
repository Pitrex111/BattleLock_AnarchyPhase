package com.jellypudding.battleLock.managers;

import com.gmail.pitrex111.CombatTagEvent;
import com.jellypudding.battleLock.BattleLock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CombatManager {

    private final Map<UUID, Long> taggedPlayers;
    private final int combatTagDuration;

    public CombatManager(BattleLock plugin) {
        this.taggedPlayers = new HashMap<>();
        this.combatTagDuration = plugin.getConfig().getInt("combat-tag-duration", 15) * 1000; // Convert to milliseconds
    }

    public List<UUID> getTaggedPlayers()
    {
        LinkedList<UUID> ret = new LinkedList<>();
        taggedPlayers.forEach((u, l) -> ret.add(u));
        return ret;
    }

    /**
     * Tag a player as being in combat
     *
     * @param player The player to tag
     */
    public void tagPlayer(Player player) {
        UUID playerId = player.getUniqueId();

        taggedPlayers.put(playerId, System.currentTimeMillis() + combatTagDuration);
        Bukkit.getPluginManager().callEvent(new CombatTagEvent(player, CombatTagEvent.TagStatus.TAGGED));
    }

    /**
     * Untag a player from combat
     *
     * @param player The player to untag
     */
    public void untagPlayer(Player player) {
        UUID playerId = player.getUniqueId();

        if (taggedPlayers.containsKey(playerId)) {
            taggedPlayers.remove(playerId);
            Bukkit.getPluginManager().callEvent(new CombatTagEvent(player, CombatTagEvent.TagStatus.UNTAGGED));
            player.sendActionBar(Component.text("No loneger in combat!").color(NamedTextColor.GREEN));
        }
    }

    /**
     * Check if a player is tagged as being in combat
     *
     * @param player The player to check
     * @return True if the player is in combat, false otherwise
     */
    public boolean isPlayerTagged(Player player) {
        UUID playerId = player.getUniqueId();

        if (!taggedPlayers.containsKey(playerId)) {
            return false;
        }

        long expireTime = taggedPlayers.get(playerId);

        if (System.currentTimeMillis() > expireTime) untagPlayer(player);

        return true;
    }

    /**
     * Get the time in seconds until a player's combat tag expires
     *
     * @param player The player to check
     * @return The time in seconds until the tag expires, or 0 if not tagged
     */
    public int getTimeUntilTagExpires(Player player) {
        UUID playerId = player.getUniqueId();

        if (!taggedPlayers.containsKey(playerId)) {
            return 0;
        }

        long expireTime = taggedPlayers.get(playerId);
        long currentTime = System.currentTimeMillis();

        if (currentTime > expireTime) {
            untagPlayer(player);
            return 0;
        }

        return (int) ((expireTime - currentTime) / 1000);
    }
}
