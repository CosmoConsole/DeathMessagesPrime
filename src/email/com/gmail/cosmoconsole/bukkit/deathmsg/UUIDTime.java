package email.com.gmail.cosmoconsole.bukkit.deathmsg;

import java.util.UUID;

public class UUIDTime {
    private UUID uuid;
    private long time;
    
    public UUIDTime(UUID u, long t) {
        uuid = u;
        time = t;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof UUIDTime) {
            return ((UUIDTime) obj).uuid.equals(this.uuid) && ((UUIDTime) obj).time == this.time;
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return (int) (uuid.hashCode() + time);
    }
}
