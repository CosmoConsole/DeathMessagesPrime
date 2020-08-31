package email.com.gmail.cosmoconsole.bukkit.deathmsg;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * An event that allows setting a custom tag for death messages during their preparation. The event will be called before the DMP priority - plugin authors should make sure their PlayerDeathEvent (or similar) has lower priority than what is configured for DMP.<br>
 * <br>
 * Intended usage:<br>
 * 1. Allow configuring the priority in your plugin's configuration or assume the lowest possible priority (MONITOR).<br>
 * 2. Schedule your PlayerDeathEvent (or similar) event to lowest priority possible (at least lower than DMP setting).<br>
 * 3. When the death event occurs, store some sort of temporary data based on the event.<br>
 * 4. When DeathMessageCustomEvent occurs, use the previously temporarily stored data to set the appropriate tag, if any.<br>
 *
 */
public class DeathMessageCustomEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private String tag;
    private Player player;
    private String killer;
    private String killer2;
    private ItemStack weapon;
    private long id;
    private boolean pvp;
    private boolean cancelled;
    
    public DeathMessageCustomEvent(long id, Player player, boolean pvp) {
        this.id = id;
        this.tag = null;
        this.killer = "";
        this.killer2 = "";
        this.weapon = null;
        this.player = player;
        this.pvp = pvp;
        this.cancelled = false;
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
     * Returns the current tag. The tag corresponds to a death message under config.yml: for example, "mob.ZombieMelee" gives a message under "death-messages.mob.ZombieMelee". By default, it is null before setTag has been called, but other plugins may also call it first.
     * 
     * @return The tag, or null if none have been set.
     */
    public String getTag() {
        return this.tag;
    }
    
    /**
     * Returns whether the death was related to a PvP (player vs player) action, that is, a player death caused by the attack of another player.
     * 
     * @return Whether the death was PvP-related.
     */
    public boolean isPVP() {
        return this.pvp;
    }

    /**
     * Returns the current killer raw name (used for %killer%).
     * 
     * @return The killer name, or null if none have been set.
     */
    public String getKiller() {
        return this.killer;
    }

    /**
     * Returns the current killer display name (used for %killer2%).
     * 
     * @return The killer display name, or null if none have been set.
     */
    public String getKiller2() {
        return this.killer2;
    }

    /**
     * Returns the current weapon (used for %weapon% and %weapon_name%).
     * 
     * @return The weapon, or null if none have been set.
     */
    public ItemStack getWeapon() {
        return this.weapon;
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
     * Returns the world in which the player died.
     * 
     * @return The world.
     */
    public World getWorld() {
        return this.player.getWorld();
    }

    /**
     * Modifies the death message according to a tag. The tag corresponds to a death message under config.yml: for example, "mob.ZombieMelee" gives a message under "death-messages.mob.ZombieMelee".
     * 
     * If the tag is not found under config.yml, the message stays unchanged.
     * 
     * @param tag A death message tag.
     */
    public void setTag(String tag) {
        this.tag = tag;
    }

    /**
     * Modifies the killer raw name for the death message (used for %killer%).
     * 
     * @param killer The new killer name to set.
     */
    public void setKiller(String killer) {
        this.killer = (killer == null ? "" : killer);
    }

    /**
     * Modifies the killer display name for the death message (used for %killer2%).
     * 
     * @param killer The new killer display name to set.
     */
    public void setKiller2(String killer2) {
        this.killer2 = (killer2 == null ? "" : killer2);
    }

    /**
     * Modifies the weapon for the death message (used for %weapon% and %weapon_name%).
     * 
     * @param weapon The weapon to set, as an ItemStack.
     */
    public void setWeapon(ItemStack weapon) {
        this.weapon = weapon;
    }

    /**
     * Modifies whether the death was caused by PVP activity.
     * 
     * @param flag Whether the death was caused by PVP.
     */
    public void setIsPVP(boolean flag) {
        this.pvp = flag;
    }
    
    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean arg0) {
        cancelled = arg0;
    }
    
}
