package email.com.gmail.cosmoconsole.bukkit.deathmsg;

import java.util.HashMap;

// a HashMap the size of which is limited
public class LimitedHashMap<K,V> extends HashMap<K,V> {
    private static final long serialVersionUID = 7515019755579498427L;
    private LimitList<K> ls;
    
    public LimitedHashMap(int capacity) {
        super(capacity + 1);
        ls = new LimitList<K>(capacity);
    }

    @Override
    public V put(K key, V value) {
        V old = super.put(key, value);
        K removing = ls.bringToTop(key);
        if (containsKey(removing) && removing != null) {
            remove(removing);
        }
        return old;
    }
}
