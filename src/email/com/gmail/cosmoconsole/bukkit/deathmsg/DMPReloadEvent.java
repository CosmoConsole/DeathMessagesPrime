package email.com.gmail.cosmoconsole.bukkit.deathmsg;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * An event that is guaranteed to trigger on every player death before any other DMP events are triggered or any formatTag functions called.
 *
 */
public class DMPReloadEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    
    public DMPReloadEvent() {
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

}
