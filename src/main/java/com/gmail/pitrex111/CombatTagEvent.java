package com.gmail.pitrex111;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public class CombatTagEvent extends PlayerEvent{

    public enum TagStatus {
        TAGGED,
        UNTAGGED
    }

    private TagStatus status;

    public CombatTagEvent(@NotNull Player player, TagStatus status) {
        super(player);
        this.status = status;
    }

    public CombatTagEvent(@NotNull Player player, boolean async, TagStatus status) {
        super(player, async);
        this.status = status;
    }

    public boolean isTagged()
    {
        return status == TagStatus.TAGGED;
    }

    public boolean isUnTagged()
    {
        return status == TagStatus.UNTAGGED;
    }

    private static final HandlerList HANDLER_LIST = new HandlerList();

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

}
