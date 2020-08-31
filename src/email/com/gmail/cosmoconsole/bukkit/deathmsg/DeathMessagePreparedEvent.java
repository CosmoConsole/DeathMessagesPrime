package email.com.gmail.cosmoconsole.bukkit.deathmsg;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import net.md_5.bungee.api.chat.TextComponent;

/**
 * An event intended to signify that the plugin prepared a death message. The death message cannot be changed. There is no guarantee that the prepared message ever gets broadcasted. The event also allows access to sets, and plugins can define players to whom the death message will always be shown or players to whom the death message will never be shown. 
 *
 */
public class DeathMessagePreparedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private TextComponent message;
    private Player player;
    private long id;
    private boolean isPvP;
    private Set<UUID> alwaysShow;
    private Set<UUID> alwaysHide;
    
    public DeathMessagePreparedEvent(long id, TextComponent message, Player player, boolean isPvP) {
        this.id = id;
        this.message = message;
        this.player = player;
        this.isPvP = isPvP;
        this.alwaysShow = new HashSet<UUID>();
        this.alwaysHide = new HashSet<UUID>();
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
     * Returns whether the death was related to a PvP (player vs player) action, that is, a player death caused by the attack of another player.
     * 
     * @return Whether the death was PvP-related.
     */
    public boolean isPVP() {
        return this.isPvP;
    }
    
    /**
     * Returns the set of players that will always be shown the death message. This set is empty by default, but other plugins may modify it.
     * 
     * @return Returns the aforementioned set of players
     */
    public Set<UUID> getAlwaysShowSet() {
        return this.alwaysShow;
    }
    
    /**
     * Returns the set of players that will never be shown the death message. This set is empty by default, but other plugins may modify it.
     * 
     * @return Returns the aforementioned set of players 
     */
    public Set<UUID> getAlwaysHideSet() {
        return this.alwaysHide;
    }
    
    /**
     * Adds a player to the always-show set. When the event finishes, the player will always be broadcasted the death message, regardless of their world or other settings (however, DMP's /toggledeathmsg stays effective).
     *  This set is empty by default, but other plugins may modify it.
     *  If the player is already on the set, this will have no effect.
     *  If the player is on the always-hide set, the player will be removed from there. 
     * 
     * @param uuid UUID of the player. 
     */
    public void addAlwaysShow(UUID uuid) {
        this.alwaysHide.remove(uuid);
        this.alwaysShow.add(uuid);
    }
    
    /**
     * Adds a player to the always-hide set. When the event finishes, the player will never be broadcasted the death message, regardless of their world or other settings.
     *  This set is empty by default, but other plugins may modify it.
     *  If the player is already on the set, this will have no effect.
     *  If the player is on the always-show set, the player will be removed from there. 
     * 
     * @param uuid UUID of the player. 
     */
    public void addAlwaysHide(UUID uuid) {
        this.alwaysShow.remove(uuid);
        this.alwaysHide.add(uuid);
    }
    
    /**
     * Removes a player from the always-show set.
     *  If the player is not on the set, this will have no effect.
     * 
     * @param uuid UUID of the player. 
     */
    public void removeAlwaysShow(UUID uuid) {
        this.alwaysShow.remove(uuid);
    }
    
    /**
     * Removes a player from the always-hide set.
     *  If the player is not on the set, this will have no effect.
     * 
     * @param uuid UUID of the player. 
     */
    public void removeAlwaysHide(UUID uuid) {
        this.alwaysHide.remove(uuid);
    }
    
    public static HandlerList getHandlerList() {
        return handlers;
    }

}
