package email.com.gmail.cosmoconsole.bukkit.deathmsg;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;

public class ReflectionUtil {

    static Map<String, Class<?>> nmsCache = new HashMap<>();
    static Map<String, Class<?>> obcCache = new HashMap<>();
    
    private static String getNMSVersion() {
        return Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
    }
    
    private static String getOBCVersion() {
        return getNMSVersion();
    }

    public static Class<?> getNMSClass(String name) throws ClassNotFoundException, SecurityException {
        if (!nmsCache.containsKey(name))
            nmsCache.put(name, Class.forName("net.minecraft.server." + getNMSVersion() + "." + name));
        return nmsCache.get(name);
    }

    public static Class<?> getOBCClass(String name) throws ClassNotFoundException, SecurityException {
        if (!obcCache.containsKey(name))
            obcCache.put(name, Class.forName("org.bukkit.craftbukkit." + getOBCVersion() + "." + name));
        return obcCache.get(name);
    }

    public static Method getMethod(Class<?> clazz, String name) throws NoSuchMethodException, SecurityException {
        return clazz.getMethod(name);
    }

    public static Method getMethod(Class<?> clazz, String name, Class<?> parClazz) throws NoSuchMethodException, SecurityException {
        return clazz.getMethod(name, parClazz);
    }

}
