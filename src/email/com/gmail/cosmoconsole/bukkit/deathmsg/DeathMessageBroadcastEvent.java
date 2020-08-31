package email.com.gmail.cosmoconsole.bukkit.deathmsg;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import net.md_5.bungee.api.chat.TextComponent;

/**
 * An event that is thrown when a death message is broadcasted using the custom broadcasting function. The event is triggered once per world. If per-world-messages is disabled, this event will trigger ONCE (with world being set to null). 
 *
 */
public class DeathMessageBroadcastEvent extends Event implements Cancellable {
    private boolean cancelled;
    private static final HandlerList handlers = new HandlerList();
    private TextComponent message;
    private Player player;
    private World world;
    private long id;
    private boolean isPvP;
    
    public DeathMessageBroadcastEvent(long id, TextComponent message, Player player, World world, boolean isPvP) {
        this.id = id;
        this.message = message;
        this.player = player;
        this.world = world;
        this.cancelled = false;
        this.isPvP = isPvP;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean arg0) {
        this.cancelled = arg0;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
    
    /**
     * Returns the ID of the death message. This ID is guaranteed to be unique to a sensible limit (2^64 death messages), after which it may wrap around.
     * 
     * @return The ID of the death message.
     */
    public long getId() {
        return this.id;
    }

    /**
     * Returns the death message as a JSON component.
     * 
     * @return The death message.
     */
    public TextComponent getMessage() {
        return this.message;
    }


    /**
     * Returns the player that died.
     * 
     * @return The player.
     */
    public Player getPlayer() {
        return this.player;
    }

    /**
     * Returns the world that the death message would be broadcasted into. There is always a single event per world that the message is broadcasted to.
     * 
     * @return The world.
     */
    public World getWorld() {
        return this.world;
    }
    
    /**
     * Returns whether the death was related to a PvP (player vs player) action, that is, a player death caused by the attack of another player.
     * 
     * @return Whether the death was PvP-related.
     */
    public boolean isPVP() {
        return this.isPvP;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

}
