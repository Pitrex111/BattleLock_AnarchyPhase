package com.jellypudding.battleLock.managers;

import com.jellypudding.battleLock.BattleLock;

import io.papermc.paper.datacomponent.item.ResolvableProfile;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatLogManager {

    private final BattleLock plugin;
    private final CombatManager combatManager;
    private final DataManager dataManager;
    private final Map<UUID, Integer> combatLogNPCs;
    private final Map<Integer, UUID> entityPlayerMap;
    private final Map<UUID, ItemStack[]> playerInventories;
    private final Map<UUID, Boolean> processingNPCDeath;
    private final Map<UUID, Integer> scheduledTasks; // Track scheduled despawn tasks
    private final Map<UUID, Location> npcLocations; // Store NPC locations for item dropping
    private final int logoutDespawnTime;
    private final NamespacedKey combatLogKey;

    public CombatLogManager(BattleLock plugin, CombatManager combatManager, DataManager dataManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        this.dataManager = dataManager;
        this.combatLogNPCs = new ConcurrentHashMap<>();
        this.entityPlayerMap = new ConcurrentHashMap<>();
        this.playerInventories = new ConcurrentHashMap<>();
        this.processingNPCDeath = new ConcurrentHashMap<>();
        this.scheduledTasks = new ConcurrentHashMap<>();
        this.npcLocations = new ConcurrentHashMap<>();
        this.logoutDespawnTime = plugin.getConfig().getInt("combat-log-despawn-time", 30) * 20; // Convert to ticks
        this.combatLogKey = new NamespacedKey(plugin, "combat_log_player_id");
    }

    private ItemStack addVanishing(ItemStack item)
    {
        if (item == null) return new ItemStack(Material.AIR);
        ItemStack ret = item.clone();
        if (ret.hasItemMeta())
        {
            ItemMeta meta = ret.getItemMeta();
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            ret.setItemMeta(meta);
        }
        return ret;
    }

    /**
     * Create a combat log NPC when a player logs out during combat
     *
     * @param player The player who logged out
     */
    public void createCombatLogNPC(Player player) {
        UUID playerId = player.getUniqueId();

        // Prevent double creation
        if (combatLogNPCs.containsKey(playerId)) {
            return;
        }

        // Cancel any existing scheduled task for this player
        if (scheduledTasks.containsKey(playerId)) {
            Bukkit.getScheduler().cancelTask(scheduledTasks.get(playerId));
            scheduledTasks.remove(playerId);
        }

        Location location = player.getLocation();

        // Store player inventory.
        ItemStack[] inventory = player.getInventory().getContents().clone();
        playerInventories.put(playerId, inventory);

        // Create an NPC at the player's location
        Mannequin npc = (Mannequin) location.getWorld().spawnEntity(location, EntityType.MANNEQUIN);

        // Setup the NPC with player data
        npc.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
        npc.customName(player.displayName());
        npc.setCustomNameVisible(true);
        npc.setInvulnerable(false);
        npc.setHealth(Math.min(player.getHealth(), 20.0));
        npc.getEquipment().setItemInMainHand(addVanishing(player.getEquipment().getItemInMainHand()));
        npc.getEquipment().setItemInOffHand(addVanishing(player.getEquipment().getItemInOffHand()));
        npc.getEquipment().setHelmet(addVanishing(player.getEquipment().getHelmet()));
        npc.getEquipment().setChestplate(addVanishing(player.getEquipment().getChestplate()));
        npc.getEquipment().setLeggings(addVanishing(player.getEquipment().getLeggings()));
        npc.getEquipment().setBoots(addVanishing(player.getEquipment().getBoots()));
        npc.getPersistentDataContainer().set(combatLogKey, PersistentDataType.STRING, playerId.toString());

        // Register the NPC
        combatLogNPCs.put(playerId, npc.getEntityId());
        entityPlayerMap.put(npc.getEntityId(), playerId);
        npcLocations.put(playerId, npc.getLocation());

        // Schedule NPC removal and store the task ID
        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> removeCombatLogNPC(playerId, false, null), logoutDespawnTime);
        scheduledTasks.put(playerId, taskId);

        plugin.getLogger().info(player.getName() + " logged out during combat! Created NPC at " +
                location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
    }

    /**
     * Remove a combat log NPC
     *
     * @param playerId The UUID of the player whose NPC should be removed
     * @param died Whether the NPC died (true) or despawned naturally (false)
     * @param killerName The name of the player who killed the NPC (null if environmental or despawned)
     */
    public void removeCombatLogNPC(UUID playerId, boolean died, String killerName) {
        if (!combatLogNPCs.containsKey(playerId)) {
            return;
        }

        // Cancel any scheduled task for this player
        if (scheduledTasks.containsKey(playerId)) {
            Bukkit.getScheduler().cancelTask(scheduledTasks.get(playerId));
            scheduledTasks.remove(playerId);
        }

        int entityId = combatLogNPCs.get(playerId);

        // Handle punishment FIRST (before entity cleanup) so it works for both live and dead entities
        if (died && playerInventories.containsKey(playerId)) {
            // Get drop location (prefer live entity, fallback to stored location)
            Location dropLocation = null;
            for (org.bukkit.entity.Entity entity : Bukkit.getWorlds().stream()
                    .flatMap(world -> world.getEntities().stream())
                    .filter(e -> e.getEntityId() == entityId)
                    .toList()) {
                dropLocation = entity.getLocation();
                break;
            }

            // Fallback to stored location if entity is gone (environmental deaths)
            if (dropLocation == null) {
                dropLocation = npcLocations.get(playerId);
            }

            // Drop items at the determined location
            if (dropLocation != null) {
                for (ItemStack item : playerInventories.get(playerId)) {
                    if (item != null) {
                        dropLocation.getWorld().dropItemNaturally(dropLocation, item);
                    }
                }
            }

            // Mark in persistent storage that this player's NPC was killed
            dataManager.markNpcKilled(playerId);
        }

        // Remove entity if it still exists
        for (org.bukkit.entity.Entity entity : Bukkit.getWorlds().stream()
                .flatMap(world -> world.getEntities().stream())
                .filter(e -> e.getEntityId() == entityId)
                .toList()) {
            entity.remove();
        }

        // Clean up all tracking data
        combatLogNPCs.remove(playerId);
        entityPlayerMap.remove(entityId);
        playerInventories.remove(playerId);
        processingNPCDeath.remove(playerId);
        npcLocations.remove(playerId);

        String playerName = Bukkit.getOfflinePlayer(playerId).getName();
        plugin.getLogger().info(playerName + "'s combat log NPC has been " + (died ? "killed" : "despawned"));
    }

    /**
     * Handle a player logging back in after combat logging
     *
     * @param player The player who logged back in
     */
    public void handlePlayerReturn(Player player) {
        UUID playerId = player.getUniqueId();

        // Check for active NPC in current session
        if (combatLogNPCs.containsKey(playerId)) {
            boolean wasKilled = !playerInventories.containsKey(playerId);
            removeCombatLogNPC(playerId, false, null);

            if (wasKilled) {
                // NPC was killed, ensure inventory is cleared and remove combat tag.
                player.getInventory().clear();
                player.setHealth(0.0);
                combatManager.untagPlayer(player);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    player.sendMessage(Component.text("Your combat log NPC was killed while you were offline. You have lost your items.", NamedTextColor.RED));
                }, 20);
                plugin.getLogger().info(player.getName() + " lost items due to a killed combat log NPC.");
            } else {
                // NPC survived, return inventory only if we have it stored.
                ItemStack[] storedInventory = playerInventories.get(playerId);
                if (storedInventory != null) {
                    player.getInventory().clear(); // Clear first to prevent any potential duplication.
                    player.getInventory().setContents(storedInventory);
                }

                if (combatManager.isPlayerTagged(player)) {
                    int timeRemaining = combatManager.getTimeUntilTagExpires(player);
                    player.sendMessage(Component.text("Your combat log NPC has been removed. You are still in combat for " + timeRemaining + " more seconds.", NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("Your combat log NPC has been removed. You are no longer in combat and may log out safely.", NamedTextColor.GREEN));
                }
            }
            return;
        }

        // Check for punishment record from previous server session
        if (dataManager.wasNpcKilled(playerId)) {
            // NPC was killed in a previous session, clear their inventory
            player.getInventory().clear();
            player.setHealth(0.0);
            combatManager.untagPlayer(player);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    player.sendMessage(Component.text("Your combat log NPC was killed while you were offline. You have lost your items.", NamedTextColor.RED));
                }, 20);

            // Remove the record now that it's been processed
            dataManager.removeKilledNpcRecord(playerId);

            plugin.getLogger().info(player.getName() + " lost items due to a killed combat log NPC (previous session).");
        }
    }

    /**
     * Handle an NPC being damaged or killed
     *
     * @param entityId The entity ID of the NPC
     * @param killerName The name of the player who killed the NPC (null if environmental)
     */
    public void handleNPCDeath(int entityId, String killerName) {
        if (!entityPlayerMap.containsKey(entityId)) {
            return;
        }

        UUID playerId = entityPlayerMap.get(entityId);

        // Prevent double processing using atomic check-and-set
        if (processingNPCDeath.putIfAbsent(playerId, true) != null) {
            return; // Already being processed
        }

        removeCombatLogNPC(playerId, true, killerName);
    }

    /**
     * Remove all combat log NPCs (for plugin shutdown)
     */
    public void removeAllCombatLogs() {
        // Cancel all scheduled tasks
        for (int taskId : scheduledTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        // Create a copy to avoid ConcurrentModificationException
        for (UUID playerId : new HashMap<>(combatLogNPCs).keySet()) {
            removeCombatLogNPC(playerId, false, null);
        }

        // Clear all tracking data
        scheduledTasks.clear();
        npcLocations.clear();
    }

    /**
     * Check if there's a combat log NPC for a player
     *
     * @param playerId The UUID of the player to check
     * @return True if a combat log NPC exists, false otherwise
     */
    public boolean hasCombatLogNPC(UUID playerId) {
        return combatLogNPCs.containsKey(playerId);
    }

    /**
     * Check if an entity is a combat log NPC
     *
     * @param entityId The entity ID to check
     * @return True if it's a combat log NPC, false otherwise
     */
    public boolean isCombatLogNPC(int entityId) {
        return entityPlayerMap.containsKey(entityId);
    }

    /**
     * Get the NamespacedKey used for marking combat log NPCs
     *
     * @return The NamespacedKey for combat log NPCs
     */
    public NamespacedKey getCombatLogKey() {
        return combatLogKey;
    }
}
