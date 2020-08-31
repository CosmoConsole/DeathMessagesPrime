package email.com.gmail.cosmoconsole.bukkit.deathmsg;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * An event that is guaranteed to trigger on every player death before any other DMP events are triggered or any formatTag functions called.
 *
 */
public class DeathPreDMPEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private double hp;
    private Entity damager;
    private Player player;
    private EntityDamageEvent.DamageCause cause;
    
    public DeathPreDMPEvent(Player player, Entity damager, EntityDamageEvent.DamageCause cause, double hp) {
        this.player = player;
        this.damager = damager;
        this.cause = cause;
        this.hp = hp;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    /**
     * Returns the cause of the damage that proved to be fatal to the player.
     * 
     * @return The cause of the fatal damage.
     */
    public EntityDamageEvent.DamageCause getCause() {
        return this.cause;
    }

    /**
     * Returns the entity that caused the fatal damage.
     * 
     * @return The damager entity, or null if none.
     */
    public Entity getDamager() {
        return this.damager;
    }

    /**
     * Returns the amount of damage dealt that proved to be fatal to the player.
     * 
     * This value can be 0 if the damage isn't known (for various reasons)
     * 
     * @return The amount of damage as a double in HP (2 HP = 1 heart)
     */
    public double getDamage() {
        return this.hp;
    }


    /**
     * Returns the player that died.
     * 
     * @return The player.
     */
    public Player getPlayer() {
        return this.player;
    }
    
    public static HandlerList getHandlerList() {
        return handlers;
    }

}
