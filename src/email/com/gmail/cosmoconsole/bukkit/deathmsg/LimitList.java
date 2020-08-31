package email.com.gmail.cosmoconsole.bukkit.deathmsg;

import java.util.ArrayDeque;
import java.util.Deque;

// a List the size of which is limited
public class LimitList<T> {

    private int bound;
    private Deque<T> list;
    
    public LimitList(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        
        bound = capacity;
        list = new ArrayDeque<>(capacity + 1);
    }

    public synchronized T bringToTop(T old) {
        if (list.contains(old)) {
            list.remove(old);
        }
        list.addLast(old);
        if (list.size() >= bound) {
            return list.removeFirst();
        } else {
            return null;
        }
    }
    
}
