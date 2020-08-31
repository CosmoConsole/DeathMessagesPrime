package email.com.gmail.cosmoconsole.bukkit.deathmsg;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import net.md_5.bungee.api.chat.TextComponent;

public interface DeathMessageTagListener {
    /**
     * Formats a death message tag using the given info.
     * 
     * @param tag The tag itself
     * @param died The player that died
     * @param cause The last damage cause for the player
     * @param killer The entity that acted as the killer. Usually a player, a mob or null (if no entity related to death)
     * @return The text component to replace the tag
     */
    public TextComponent formatTag(String tag, Player died, EntityDamageEvent.DamageCause cause, Entity killer);
}
