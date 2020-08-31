package email.com.gmail.cosmoconsole.bukkit.deathmsg;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ItemTag;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.*;
import org.bukkit.plugin.*;
import org.bukkit.metadata.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.scheduler.*;
import org.json.simple.JSONObject;
import org.bukkit.projectiles.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.entity.*;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.player.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The event listener, including the death message generator
 */
class DeathListener implements Listener
{
    // this whole lot is not even thread-safe
    
    DeathMessagesPrime pl;                          // plugin instance
    FileConfiguration fc;                           // config
    Random r;                                       // RNG
    EventPriority pr;                               // active event priority
    private ArrayList<DeathMessage> queue;          // queue of death messages to be broadcast
    private Player lastkiller;                      // killer of current death message, for broadcasting 
    private Player lastvictim;                      // died player of current death message, for broadcasting
    private HashMap<UUID, Material> lastBlock;      // the last block type a player has touched
    private HashMap<UUID, Long> lastTicks;          // the last time a player has touched the block above
    private HashMap<UUID, Long> bedTicks;           // the last time a player slept in a bed
    private HashMap<UUID, Long> tempbanC;           // temp ban (rate limit) info; count
    private HashMap<UUID, Long> tempbanT;           // temp ban (rate limit) info; time
    private HashMap<UUID, String> dmc;              // temp death message for death-message-compat-mode           
    private HashMap<UUID, Boolean> pvphm;           // was last/current death caused by PVP?
    private long dmid = -1;                         // ID of current death message
    private Lock flushQueue = new ReentrantLock();  // mutex for queue flush
    private String last_killer_name;                // killer name for current/last death message
    private String last_killer_name2;               // killer display name (player only) for current/last death message
    private ItemStack last_weapon;                  // weapon for current/last death message
    private LimitedHashMap<UUID, String> dhistory;  // current death message for compat
    private static int curPrefixLen = 0;            // length of the formatted prefix in the current death message
    private static int curSuffixLen = 0;            // length of the formatted suffix in the current death message
    
    DeathListener(final DeathMessagesPrime plugin, final FileConfiguration config) {
        this.pl = null;
        this.fc = null;
        this.r = null;
        this.pr = EventPriority.HIGH;
        this.queue = new ArrayList<DeathMessage>();
        this.lastkiller = null;
        this.lastvictim = null;
        this.pl = plugin;
        this.fc = config;
        this.r = new Random();
        this.lastBlock = new HashMap<UUID, Material>();
        this.lastTicks = new HashMap<UUID, Long>();
        this.bedTicks = new HashMap<UUID, Long>();
        this.tempbanC = new HashMap<UUID, Long>();
        this.tempbanT = new HashMap<UUID, Long>();
        this.dmc = new HashMap<UUID, String>();
        this.pvphm = new HashMap<UUID, Boolean>();
        this.dmid = 0;
        this.dhistory = new LimitedHashMap<UUID, String>(4);
    }
    
    static boolean mcVer(int comp) {
        return DeathMessagesPrime.mcVer(comp);
    }

    static boolean mcVerRev(int comp, int rev) {
        return DeathMessagesPrime.mcVerRev(comp, rev);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamaged(final EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            final Player p = (Player)e.getEntity();
            if (p.hasMetadata("dmp.lastCause")) {
                p.removeMetadata("dmp.lastCause", (Plugin)this.pl);
            }
            if (p.hasMetadata("dmp.lastDamageSize")) {
                p.removeMetadata("dmp.lastDamageSize", (Plugin)this.pl);
            }
            p.setMetadata("dmp.lastCause", (MetadataValue)new FixedMetadataValue((Plugin)this.pl, (Object)e.getCause().toString()));
            p.setMetadata("dmp.lastDamageSize", (MetadataValue)new FixedMetadataValue((Plugin)this.pl, e.getDamage()));
        }/* else if (DeathMessagesPrime.petMessages) { // No API support
            if (e.getEntity() instanceof LivingEntity && e.getEntity() instanceof Tameable) {
                LivingEntity w = (LivingEntity) e.getEntity();
                if (((Tameable)w).isTamed() && w.getCustomName() != null) {
                    if (w.hasMetadata("dmp.lastCause")) {
                        w.removeMetadata("dmp.lastCause", (Plugin)this.pl);
                    }
                    if (w.hasMetadata("dmp.lastDamageSize")) {
                        w.removeMetadata("dmp.lastDamageSize", (Plugin)this.pl);
                    }
                    w.setMetadata("dmp.lastCause", (MetadataValue)new FixedMetadataValue((Plugin)this.pl, (Object)e.getCause().toString()));
                    w.setMetadata("dmp.lastDamageSize", (MetadataValue)new FixedMetadataValue((Plugin)this.pl, e.getDamage()));
                }
            }
        }*/
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedClick(final PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        boolean check = false;
        Material m = e.getClickedBlock().getType();
        if (mcVer(1_013)) {
            check = m == Material.BLACK_BED ||
                    m == Material.BLUE_BED ||
                    m == Material.BROWN_BED ||
                    m == Material.CYAN_BED ||
                    m == Material.GRAY_BED ||
                    m == Material.GREEN_BED ||
                    m == Material.LIGHT_BLUE_BED ||
                    m == Material.LIGHT_GRAY_BED ||
                    m == Material.LIME_BED ||
                    m == Material.MAGENTA_BED ||
                    m == Material.ORANGE_BED ||
                    m == Material.PINK_BED ||
                    m == Material.PURPLE_BED ||
                    m == Material.RED_BED ||
                    m == Material.WHITE_BED ||
                    m == Material.YELLOW_BED;
        } else {
            check = materialSafeCheck(m, "BED") || materialSafeCheck(m, "BED_BLOCK");
        }
        
        if (mcVer(1_016))
        {
            check |= m == Material.RESPAWN_ANCHOR;
        }
        if (check) {
            long now = System.currentTimeMillis();
            Location bl = e.getClickedBlock().getLocation();
            for (Player p: e.getClickedBlock().getWorld().getPlayers()) {
                UUID u = p.getUniqueId();
                if (p.getLocation().distanceSquared(bl) < 100) {
                    this.bedTicks.put(u, now);
                } else {
                    this.bedTicks.remove(u);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(final PlayerMoveEvent e) {
        Material m = e.getTo().getWorld().getBlockAt(e.getTo()).getType();
        boolean check = false;
        if (mcVer(1_016)) {
            check = m == Material.LADDER || m == Material.VINE || m == Material.WATER || m == Material.SCAFFOLDING
                    || isTrapdoor(m) || m == Material.TWISTING_VINES || m == Material.WEEPING_VINES;
        } else if (mcVer(1_013)) {
            check = m == Material.LADDER || m == Material.VINE || m == Material.WATER;
        } else {
            check = m == Material.LADDER || m == Material.VINE || m == Material.WATER || materialSafeCheck(m, "STATIONARY_WATER");
        }
        if (check) {
            this.lastBlock.put(e.getPlayer().getUniqueId(), m);
            this.lastTicks.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamaged(final EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) {
            Entity en = e.getDamager();
            ProjectileSource sht = null;
            EntityType pjt = null;
            if (en == null) { // might happen
                return;
            }
            if (en instanceof Projectile) {
                // get projectile info
                sht = ((Projectile)en).getShooter();
                pjt = en.getType();
                try {
                    en = (Entity)sht;
                }
                catch (Exception ex) {}
            }
            if (en == null) { // might still happen
                return;
            }
            if (!mcVer(1_014) && pjt != null && pjt.name().equalsIgnoreCase("TIPPED_ARROW")) {
                pjt = EntityType.ARROW;
            }
            String typ = en.getType().toString();
            
            if (!mcVer(1_011)) {
                // skeleton/zombie subtype hacks
                Entity damager = e.getDamager();
                if (damager.getType() == EntityType.SKELETON) {
                    @SuppressWarnings("deprecation")
                    Skeleton.SkeletonType skt = ((Skeleton)damager).getSkeletonType();
                    @SuppressWarnings("deprecation")
                    Skeleton.SkeletonType skt_w = Skeleton.SkeletonType.WITHER;
                    if (skt == skt_w)
                        typ = "WITHER_SKELETON";
                    else if (mcVer(1_010)) {
                        @SuppressWarnings("deprecation")
                        Skeleton.SkeletonType skt_s = Skeleton.SkeletonType.STRAY;
                        if (skt == skt_s)
                            typ = "STRAY";
                    }
                } else if (damager.getType() == EntityType.ZOMBIE) {
                    @SuppressWarnings("deprecation")
                    boolean z_v = ((Zombie)damager).isVillager();
                    if (z_v)
                        typ = "ZOMBIE_VILLAGER";
                    else if (isHusk(damager))
                        typ = "HUSK";
                }
            }

            // use [mob] as a placeholder prefix internally to signify that this is *not* a player
            String dmgr = "[mob]" + typ;
            if (e.getDamager() instanceof Player) {
                // and replace with player name here if it actually is
                dmgr = ((Player)e.getDamager()).getName();
            }
            if (sht != null && sht instanceof BlockProjectileSource) {
                dmgr = "Dispenser";
            }
            
            // handle metadata
            
            /* this is as good as time as any to explain what they signify.
             *   they are all referenced to when the player dies to get the cause of death:
             * dmp.lastCause: the last damage cause
             * dmp.lastDamageSize. the last damage size
             * dmp.lastDamage: the latest damager as a string, either player name or [mob] etc.
             * dmp.lastDamageEx: same as dmp.lastDamage, but used to matter.
             *                      I should know why it wasn't removed, but I don't
             * dmp.lastDamageEnt: the latest damager entity, be it of any type, including projectiles
             * dmp.lastDamageEnt2: if the last damager entity is a projectile, TNT, etc:
             *                          the projectile source entity or TNT igniter
             * dmp.lastDamageEntT: the type of the projectile if the latest damager entity was a projectile
             * dmp.lastDamageExpl: whether the last damage was caused by an explosion
             * dmp.lastDamageTime: the timestamp of the damage information, used to determine if the damage
             *                     data has changed to avoid wiping it by timer 
             * 
             */
            
            final Player p = (Player)e.getEntity();
            if (p.hasMetadata("dmp.lastDamageTime")) {
                p.removeMetadata("dmp.lastDamageTime", (Plugin)this.pl);
            }
            p.setMetadata("dmp.lastDamageTime", (MetadataValue)new FixedMetadataValue((Plugin)this.pl, new Date().getTime()));
            if (p.hasMetadata("dmp.lastDamageEx")) {
                p.removeMetadata("dmp.lastDamageEx", (Plugin)this.pl);
            }
            if (p.hasMetadata("dmp.lastDamage")) {
                p.removeMetadata("dmp.lastDamage", (Plugin)this.pl);
            }
            if (p.hasMetadata("dmp.lastDamageEntT")) {
                p.removeMetadata("dmp.lastDamageEntT", (Plugin)this.pl);
            }
            if (en instanceof Player && p.getName().equalsIgnoreCase(((Player)en).getName())) {
                return;
            }
            if (en instanceof Projectile) {
                final ProjectileSource src = ((Projectile)en).getShooter();
                if (src != null) {
                    p.setMetadata("dmp.lastDamageEnt2", (MetadataValue)new FixedMetadataValue((Plugin)this.pl, (Object)src));
                }
            }
            if (en instanceof TNTPrimed) {
                final Entity src2 = ((TNTPrimed)en).getSource();
                if (src2 != null) {
                    p.setMetadata("dmp.lastDamageEnt2", (MetadataValue)new FixedMetadataValue((Plugin)this.pl, (Object)src2));
                }
            }
            p.setMetadata("dmp.lastDamage", (MetadataValue)new FixedMetadataValue((Plugin)this.pl, (Object)dmgr));
            if (e.getCause() == DamageCause.BLOCK_EXPLOSION || e.getCause() == DamageCause.ENTITY_EXPLOSION)
                p.setMetadata("dmp.lastDamageExpl", (MetadataValue)new FixedMetadataValue((Plugin)this.pl, true));
            p.setMetadata("dmp.lastDamageEnt", (MetadataValue)new FixedMetadataValue((Plugin)this.pl, (Object)en));
            if (pjt != null) {
                p.setMetadata("dmp.lastDamageEntT", (MetadataValue)new FixedMetadataValue((Plugin)this.pl, (Object)pjt));
            }
            p.setMetadata("dmp.lastDamageEx", (MetadataValue)new FixedMetadataValue((Plugin)this.pl, (Object)dmgr));
            
            // remove damage info after 10 sec, unless newer damage info has been registered
            final BukkitRunnable t = new BukkitRunnable() {
                public void run() {
                    if (p.hasMetadata("dmp.lastDamageTime")) {
                        // don't remove data if < 10 sec from last damage
                        long n = new Date().getTime();
                        long t = p.getMetadata("dmp.lastDamageTime").get(0).asLong();
                        if ((n - t) < 9900L) {
                            return;
                        } else {
                            p.removeMetadata("dmp.lastDamageTime", (Plugin)DeathListener.this.pl);
                        }
                    }
                    if (p.hasMetadata("dmp.lastDamage")) {
                        p.removeMetadata("dmp.lastDamage", (Plugin)DeathListener.this.pl);
                    }
                    if (p.hasMetadata("dmp.lastDamageEx")) {
                        p.removeMetadata("dmp.lastDamageEx", (Plugin)DeathListener.this.pl);
                    }
                    if (p.hasMetadata("dmp.lastDamageExpl")) {
                        p.removeMetadata("dmp.lastDamageExpl", (Plugin)DeathListener.this.pl);
                    }
                    if (p.hasMetadata("dmp.lastDamageEnt")) {
                        p.removeMetadata("dmp.lastDamageEnt", (Plugin)DeathListener.this.pl);
                    }
                    if (p.hasMetadata("dmp.lastDamageEntT")) {
                        p.removeMetadata("dmp.lastDamageEntT", (Plugin)DeathListener.this.pl);
                    }
                    if (p.hasMetadata("dmp.lastDamageEnt2")) {
                        p.removeMetadata("dmp.lastDamageEnt2", (Plugin)DeathListener.this.pl);
                    }
                }
            };
            t.runTaskLater((Plugin)pl, 200L);
        }
    }

    String getItemStackNBTTag(ItemStack itemStack) throws Throwable {
        // Minecraft has used a variety of less-than-JSON formats in the past
        // so it leads to a lot of stuff like this
        String json = convertItemStackToJsonRegular(itemStack);
        int tagIndex = json.indexOf("tag:");
        if (tagIndex < 0) throw new IllegalArgumentException();
        return json.substring(tagIndex, json.length() - 1);
    }

    String convertItemStackToJsonRegular(ItemStack itemStack) throws Throwable {
        // yay, NMS
        Class<?> craftItemStackClazz = ReflectionUtil.getOBCClass("inventory.CraftItemStack");
        Method asNMSCopyMethod = ReflectionUtil.getMethod(craftItemStackClazz, "asNMSCopy", ItemStack.class);

        Class<?> nmsItemStackClazz = ReflectionUtil.getNMSClass("ItemStack");
        Class<?> nbtTagCompoundClazz = ReflectionUtil.getNMSClass("NBTTagCompound");
        Method saveNmsItemStackMethod = ReflectionUtil.getMethod(nmsItemStackClazz, "save", nbtTagCompoundClazz);

        Object nmsNbtTagCompoundObj; 
        Object nmsItemStackObj; 
        Object itemAsJsonObject; 

        try {
            nmsNbtTagCompoundObj = nbtTagCompoundClazz.newInstance();
            nmsItemStackObj = asNMSCopyMethod.invoke(null, itemStack);
            itemAsJsonObject = saveNmsItemStackMethod.invoke(nmsItemStackObj, nmsNbtTagCompoundObj);
        } catch (Throwable t) {
            throw t;
        }

        return itemAsJsonObject.toString();
    }
    
    private String capitalize(String s) {
        // capitalizes every word (separated by spaces) in a string
        if (s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        for (String word: s.split(" ")) {
            sb.append(" ");
            if (word.length() < 2) {
                sb.append(word.toUpperCase());
            } else {    
                sb.append(word.toUpperCase().charAt(0));
                sb.append(word.substring(1));
            }
        }
        return sb.toString().substring(1);
    }
    
    @SuppressWarnings("unchecked")
    String convertEntityToJson(Entity entity, String name) throws Exception {
        Map<String, String> data = new HashMap<String, String>();
        data.put( "name", name );
        data.put( "type",
                mcVer(1_014)
                ? entity.getType().getKey().toString()
                : capitalize(entity.getType().toString().replace('_', ' ').toLowerCase()).replace(" ","")
                );
        data.put( "id", entity.getUniqueId().toString() );  
        JSONObject json = new JSONObject();
        json.putAll( data );
        return json.toString();
    }

    // whole bunch of redirects that should/would not exist in cleaner code

    TextComponent format(final PlayerDeathEvent e, final String s, final String killer_name, final String weapon_name) {
        return this.format(e, s, killer_name, "", weapon_name);
    }
    
    TextComponent format(final PlayerDeathEvent e, final String s, final String killer_name, final String weapon_name, final ItemStack is) {
        return format(e, s, killer_name, "", weapon_name, is);
    }

    TextComponent format(final PlayerDeathEvent e, final String s, final String killer_name, final String killer_name2, final String weapon_name) {
        return format(e, s, killer_name, killer_name2, weapon_name, null);
    }
    
    TextComponent formatV(final PlayerDeathEvent e, final String r, final String s, final String killer_name, final String weapon_name) {
        return this.formatV(e, r, s, killer_name, "", weapon_name);
    }
    
    TextComponent formatV(final PlayerDeathEvent e, final String r, final String s, final String killer_name, final String weapon_name, final ItemStack is) {
        return formatV(e, r, s, killer_name, "", weapon_name, is);
    }

    TextComponent formatV(final PlayerDeathEvent e, final String r, final String s, final String killer_name, final String killer_name2, final String weapon_name) {
        return formatV(e, r, s, killer_name, killer_name2, weapon_name, null);
    }

    TextComponent formatV(final PlayerDeathEvent e, final Entity k, final String r, final String s, final String killer_name, final String killer_name2, final String weapon_name) {
        return formatV(e, k, r, s, killer_name, killer_name2, weapon_name, null);
    }
    
    TextComponent formatV(final PlayerDeathEvent e, final String vr, final String s, final String killer_name, final String killer_name2, final String weapon_name, final ItemStack is) {
        return this.formatV(e, null, vr, s, killer_name, killer_name2, weapon_name, is);
    }
    
    TextComponent formatV(final PlayerDeathEvent e, final Entity k, final String rr, final String s, final String killer_name, final String killer_name2, final String weapon_name, final ItemStack is) {
        last_killer_name = killer_name;
        last_killer_name2 = killer_name2;
        last_weapon = is;
        String vr = rr.isEmpty() ? "unknown" : rr;
        String ar = vr.trim();
        // this is extremely messy, but the first space signifies that most custom-messages-per-* should be
        // fetched beforehand for this message
        // this should absolutely be a boolean parameter, yet it's not
        boolean custom = vr.charAt(0) == ' ';
        boolean shouldFetch = pl.config.getBoolean("custom-messages-per-player.override", false) || !custom;
        String messageToUse = s;

        if (is != null)
        {
            try {
                ConfigurationSection cc = this.fc.getConfigurationSection("custom-messages-per-weapon-type");
                String mt = is.getType().name();
                if (cc != null && cc.contains(mt)) {
                    ConfigurationSection cc2 = cc.getConfigurationSection(mt);
                    if (cc2 != null)
                    {
                        List<String> ls = cc2.getStringList(ar);
                        if (ls.size() < 1) ls = cc2.getStringList("*");
                        if (ls.size() < 1)
                            messageToUse = "";
                        else
                            messageToUse = ls.get(r.nextInt(ls.size()));
                    }
                }
            } catch (Exception ex) {
            }
            try {
                ConfigurationSection cc = this.fc.getConfigurationSection("custom-messages-per-weapon-name");
                if (cc != null)
                    for (String rx: cc.getKeys(false)) {
                        if (weapon_name.matches(rx.replace("_","."))) {
                            ConfigurationSection cc2 = cc.getConfigurationSection(rx);
                            if (cc2 != null)
                            {
                                List<String> ls = cc2.getStringList(ar);
                                if (ls.size() < 1) ls = cc2.getStringList("*");
                                if (ls.size() < 1) {
                                    messageToUse = "";
                                    break;
                                }
                                messageToUse = ls.get(r.nextInt(ls.size())); 
                                break;
                            }
                        }
                    }
            } catch (Exception ex) {
            }
        }
        if (shouldFetch) {
            try {
                ConfigurationSection A = pl.config.getConfigurationSection("custom-messages-per-player");
                if (A != null)
                {
                    ConfigurationSection B = A.getConfigurationSection(e.getEntity().getUniqueId().toString());
                    if (!B.contains(ar)) ar = "*";
                    if (B.contains(ar))
                    {
                        List<String> dmsgs = B.getStringList(ar);
                        if (dmsgs.size() < 1)
                            messageToUse = "";
                        else
                            messageToUse = dmsgs.get(r.nextInt(dmsgs.size()));
                    }
                }
            } catch (Exception ex) {
            }
            if (k != null && k instanceof Player) {
                try {
                    ConfigurationSection A = pl.config.getConfigurationSection("custom-messages-per-killer-player");
                    if (A != null)
                    {
                        ConfigurationSection B = A.getConfigurationSection(((Player)k).getUniqueId().toString());
                        if (!B.contains(ar)) ar = "*";
                        if (B.contains(ar))
                        {
                            List<String> dmsgs = B.getStringList(ar);
                            if (dmsgs.size() < 1)
                                messageToUse = "";
                            else
                                messageToUse = dmsgs.get(r.nextInt(dmsgs.size()));
                        }
                    }
                } catch (Exception ex) {
                }
            }
            try {
                ConfigurationSection A = pl.config.getConfigurationSection("custom-messages-per-world");
                if (A != null)
                {
                    ConfigurationSection B = A.getConfigurationSection(e.getEntity().getWorld().getName());
                    if (!B.contains(ar)) ar = "*";
                    if (B.contains(ar))
                    {
                        List<String> dmsgs = B.getStringList(ar);
                        if (dmsgs.size() < 1)
                            messageToUse = "";
                        else
                            messageToUse = dmsgs.get(r.nextInt(dmsgs.size()));
                    }
                }
            } catch (Exception ex) {
            }
        }
        
        return this.format(e, messageToUse, killer_name, killer_name2, weapon_name, is);
    }
    
    String handleStandardPlaceholders(String text, Player p, String killer_name, String killer_name2, String weapon_name, ItemStack is, boolean killer_xp_ok, int killer_xp, int killer_level, double rdist) {
        return ChatColor.translateAlternateColorCodes('&', 
                text)
                .replace("%player%", p.getName())
                .replace("%name%", p.getDisplayName())
                .replace("%biome%", this.getBiomeName(p))
                .replace("%world%", this.getWorldName(p.getWorld().getName()))
                .replace("%killer%", killer_name)
                .replace("%killer2%", killer_name2)
                .replace("%weapon_name%", (pl.config.getBoolean("death-message-show-rarity", true) ? getRarityColor(is) : "") + weapon_name)
                .replace("%playerxp%", String.valueOf(getXP(p)))
                .replace("%playerlevel%", String.valueOf(p.getLevel()))
                .replace("%killerxp%", killer_xp_ok ? String.valueOf(killer_xp) : "")
                .replace("%killerlevel%", killer_xp_ok ? String.valueOf(killer_level) : "")
                .replace("%x%", String.valueOf(p.getLocation().getBlockX()))
                .replace("%y%", String.valueOf(p.getLocation().getBlockY()))
                .replace("%z%", String.valueOf(p.getLocation().getBlockZ()))
                .replace("%distance%", rdist < 0 ? "" : String.valueOf(Math.round(rdist)));
    }
    
    @SuppressWarnings("deprecation")
    TextComponent handleSpecialPlaceholders(String t, Player p, ItemStack is, String weapon_name, String killer_name, String killer_name2, Entity damager) {
        TextComponent message = new TextComponent("");
        BaseComponent[] src = TextComponent.fromLegacyText(t);
        List<BaseComponent> dst = new ArrayList<>();
        dst.add(new TextComponent(TextComponent.fromLegacyText(ChatColor.RESET.toString())));
        
        // special placeholders
        for (BaseComponent c: src) {
            if (c.getColorRaw() == null) {
                copyFormatting(c, dst.get(dst.size() - 1));
            }
            String txt = c.toPlainText();
            if (txt.contains("%")) {
                int start = 0, end, oldstart = 0;
                BaseComponent last = c;
                while (true) {
                    // get next %placeholder%
                    if (start >= txt.length()) {
                        break;
                    };
                    start = txt.indexOf("%", start);
                    if (start < 0) {
                        // no more placeholders
                        TextComponent tmp = new TextComponent(txt.substring(oldstart));
                        copyFormatting(tmp, last);
                        dst.add(tmp);
                        break;
                    }
                    end = txt.indexOf("%", start + 1);
                    if (end < 0) {
                        // got first % but not a second one. this is a syntax error, but we'll let it slide
                        TextComponent tmp = new TextComponent(txt.substring(oldstart));
                        copyFormatting(tmp, last);
                        dst.add(tmp);
                        break;
                    }
                    String tag = txt.substring(start + 1, end);
                    TextComponent splitbefore = new TextComponent(txt.substring(oldstart, start));
                    copyFormatting(splitbefore, last);
                    BaseComponent result = null; 
                    String hover = null; 
                    HoverEvent.Action hvt = null;
                    if (tag.equals("weapon")) {
                        if (is == null) {
                            result = new TextComponent(TextComponent.fromLegacyText(
                                    (pl.config.getBoolean("death-message-show-rarity", true) ? getRarityColor(is) : "") +
                                    ((weapon_name != null && weapon_name.length() > 0) ? weapon_name : itemStackNameToString(is))));
                        } else {
                            result = new TextComponent(TextComponent.fromLegacyText(
                                    (pl.config.getBoolean("death-message-show-rarity", true) ? getRarityColor(is) : "") +
                                    ((weapon_name != null && weapon_name.length() > 0) ? weapon_name : itemStackNameToString(is))));
                            try {
                                String text = convertItemStackToJsonRegular(is);
                                hvt = HoverEvent.Action.SHOW_ITEM;
                                if (hasPiglinBrute())
                                    result.setHoverEvent(new HoverEvent(hvt, new net.md_5.bungee.api.chat.hover.content.Item(
                                            is.getType().getKey().toString(), is.getAmount(), ItemTag.ofNbt(getItemStackNBTTag(is)))));
                                else
                                {
                                    if (text == null) throw new Exception();
                                    hover = text;   
                                }
                            } catch (Throwable t_) {
                                if (pl.debug) t_.printStackTrace();
                            }
                        }
                    } else if (tag.equals("entity")) {
                        try {
                            if (damager == null) throw new Exception();
                            result = new TextComponent(TextComponent.fromLegacyText((killer_name2.length() < 1 ? killer_name : killer_name2) ));
                            hvt = HoverEvent.Action.SHOW_ENTITY;
                            if (!hasPiglinBrute())
                                hover = prepareEntityJson(convertEntityToJson(damager, killer_name));
                            else
                                result.setHoverEvent(new HoverEvent(hvt, new net.md_5.bungee.api.chat.hover.content.Entity(
                                        damager.getType().getKey().toString(), damager.getUniqueId().toString(), new TextComponent(TextComponent.fromLegacyText(killer_name)))));
                        } catch (Throwable t_) {
                            if (pl.debug) t_.printStackTrace();
                            result = new TextComponent("");
                        }
                    } else if (tag.equals("plrtag")) {
                        result = new TextComponent(TextComponent.fromLegacyText(p.getName()));
                        try {
                            hvt = HoverEvent.Action.SHOW_ENTITY;
                            if (!hasPiglinBrute())
                                hover = prepareEntityJson(convertEntityToJson(p, p.getName()));
                            else
                                result.setHoverEvent(new HoverEvent(hvt, new net.md_5.bungee.api.chat.hover.content.Entity(
                                        p.getType().getKey().toString(), p.getUniqueId().toString(), new TextComponent(TextComponent.fromLegacyText(p.getName())))));
                        } catch (Throwable t_) {
                            if (pl.debug) t_.printStackTrace();
                        }
                    } else if (tag.equals("victim")) {
                        result = new TextComponent(TextComponent.fromLegacyText(p.getDisplayName()));
                        try {
                            hvt = HoverEvent.Action.SHOW_ENTITY;
                            if (!hasPiglinBrute())
                                hover = prepareEntityJson(convertEntityToJson(p, p.getDisplayName()));
                            else
                                result.setHoverEvent(new HoverEvent(hvt, new net.md_5.bungee.api.chat.hover.content.Entity(
                                        p.getType().getKey().toString(), p.getUniqueId().toString(), new TextComponent(TextComponent.fromLegacyText(p.getDisplayName())))));
                        } catch (Throwable t_) {
                            if (pl.debug) t_.printStackTrace();
                        }
                    } else if (tag.equals("")) {
                        result = new TextComponent(TextComponent.fromLegacyText("%"));
                    } else if (DeathMessagesPrime.taglisteners.containsKey(tag)) {
                        EntityDamageEvent.DamageCause dam = DamageCause.CUSTOM;
                        if (p.hasMetadata("dmp.lastCause") && p.getMetadata("dmp.lastCause").size() > 0) {
                            dam = EntityDamageEvent.DamageCause.valueOf(p.getMetadata("dmp.lastCause").get(0).asString());
                        }
                        result = DeathMessagesPrime.taglisteners.get(tag).formatTag(tag, p, dam, damager);
                    } else {
                        // try to find prefix
                        EntityDamageEvent.DamageCause dam = DamageCause.CUSTOM;
                        if (p.hasMetadata("dmp.lastCause") && p.getMetadata("dmp.lastCause").size() > 0) {
                            dam = EntityDamageEvent.DamageCause.valueOf(p.getMetadata("dmp.lastCause").get(0).asString());
                        }
                        for (String k: DeathMessagesPrime.taglistenerprefixes.keySet()) {
                            if (tag.startsWith(k)) {
                                result = DeathMessagesPrime.taglistenerprefixes.get(k).formatTag(tag, p, dam, damager);
                                break;
                            }
                        }
                    }
                    if (result == null) {
                        result = new TextComponent(TextComponent.fromLegacyText(""));
                    }
                    if (hvt != null && hover != null) {
                        if (mcVer(1_016))
                            result.setHoverEvent(new HoverEvent(hvt, new ComponentBuilder(hover).create()));
                        else
                            result.setHoverEvent(new HoverEvent(hvt, new BaseComponent[] {new TextComponent(hover)}));
                    }
                    List<BaseComponent> comps = flattenComponent(splitbefore, result);
                    for (int i = 1; i < comps.size(); ++i) {
                        if (comps.get(i).getColorRaw() == null) {
                            copyFormatting(comps.get(i), comps.get(i - 1));
                        }
                    }
                    dst.add(splitbefore);
                    dst.add(result);
                    last = comps.get(comps.size() - 1);
                    oldstart = start = end + 1;
                }
            } else {
                dst.add(c);
            }
        }
        message.setExtra(dst);
        if (ComponentSerializer.toString(message).length() > 30000) { // too long, strip special NBT
            message.setExtra(Arrays.asList(TextComponent.fromLegacyText(message.toLegacyText())));
        }
        if (ComponentSerializer.toString(message).length() > 30000) { // too long still
            message.setExtra(Arrays.asList(TextComponent.fromLegacyText(message.toLegacyText().substring(256))));
        }
        return message;
    }
    
    TextComponent format(final PlayerDeathEvent e, final String s, final String killer_name, final String killer_name2, final String weapon_name, final ItemStack is_orig) {
        ItemStack is = is_orig;
        final Player p = e.getEntity();
        if (s.isEmpty()) return new TextComponent("");
        String pref = this.fc.getString("death-messages.prefix","");
        String suff = this.fc.getString("death-messages.suffix","");
        if (pvphm.get(p.getUniqueId())) {
            pref = this.fc.getString("death-messages.prefix-pvp", pref);
            suff = this.fc.getString("death-messages.suffix-pvp", suff);
        }
        double rdist = -1;
        Entity damager = null;
        try {
            damager = (Entity) p.getMetadata("dmp.lastDamageEnt").get(0).value();
            if (damager != null && damager.getLocation() != null)
                rdist = p.getLocation().distance(damager.getLocation());
        } catch (Exception ex) {
            damager = null;
        }
        boolean killer_xp_ok = false;
        int killer_xp = 0, killer_level = 0;
        if (killer_xp_ok = (damager instanceof Player)) {
            killer_xp = getXP(((Player) damager));
            killer_level = ((Player) damager).getLevel();
        }
        // item flags
        if (mcVer(1_008) && pl.config.contains("death-message-item-flags") && is_orig != null) {
            is = is_orig.clone();
            ItemMeta im = is.getItemMeta();
            try {
                for (String flag: pl.config.getStringList("death-message-item-flags")) {
                    try {
                        ItemFlag ifl = ItemFlag.valueOf(flag);
                        if (!im.hasItemFlag(ifl)) {
                            im.addItemFlags(ifl);
                        }
                    } catch (Exception ex) {}
                }
            } catch (Exception ex) { }
            is.setItemMeta(im);
        }
        
        // standard placeholders
        String tp = handleStandardPlaceholders(pref, p, killer_name, killer_name2, weapon_name, is, killer_xp_ok, killer_xp, killer_level, rdist);
        String ts = handleStandardPlaceholders(suff, p, killer_name, killer_name2, weapon_name, is, killer_xp_ok, killer_xp, killer_level, rdist);
        String t = handleStandardPlaceholders(s, p, killer_name, killer_name2, weapon_name, is, killer_xp_ok, killer_xp, killer_level, rdist);
        
        TextComponent ttp = handleSpecialPlaceholders(tp, p, is, weapon_name, killer_name, killer_name2, damager);
        TextComponent tts = handleSpecialPlaceholders(ts, p, is, weapon_name, killer_name, killer_name2, damager);
        
        curPrefixLen = ttp.toLegacyText().length();
        curSuffixLen = tts.toLegacyText().length();
        t = tp + t + ts;

        return handleSpecialPlaceholders(t, p, is, weapon_name, killer_name, killer_name2, damager);
    }

    private String prepareEntityJson(String json) {
        if (mcVer(1_015)) {
            return json;
        }
        return json.replace("\"id\"", "id").replace("\"name\"", "name").replace("\"type\"", "type");
    }

    @SuppressWarnings("deprecation")
    private Object getBiome(Player p) {
        if (mcVer(1_015)) {
            return p.getWorld().getBiome(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
        } else {
            return p.getWorld().getBiome(p.getLocation().getBlockX(), p.getLocation().getBlockZ());
        }
    }
    
    private String getBiomeName(Player p)
    {
        Object biome = getBiome(p);
        if (biome == null) return "???";
        String biomeName = biome.toString();
        return getBiomeName(biomeName == null ? "???" : getBiomeName(biomeName));
    }

    private String getRarityColor(ItemStack is) {
        try {
            int rarity = getRarity(is);
            if (rarity == 1) { // uncommon
                return ChatColor.YELLOW.toString();
            } else if (rarity == 2) { // rare
                return ChatColor.AQUA.toString();
            } else if (rarity == 3) { // epic
                return ChatColor.LIGHT_PURPLE.toString();
            }
        } catch (Exception ex) {
            return "";
        }
        return "";
    }

    private int getRarity(ItemStack is) {
        // the API should really offer something like this as a helper function
        Material ism = is.getType();
        if (!is.getEnchantments().isEmpty()) {
            if (materialSafeCheck(ism, "ENCHANTED_BOOK")) {
                return 1; // uncommon
            } else {
                return 2; // rare
            }
        } else if (materialSafeCheck(ism, "EXPERIENCE_BOTTLE") || materialSafeCheck(ism, "LEGACY_EXP_BOTTLE")
                || materialSafeCheck(ism, "DRAGON_BREATH") || materialSafeCheck(ism, "LEGACY_DRAGONS_BREATH")
                || materialSafeCheck(ism, "ELYTRA") || materialSafeCheck(ism, "LEGACY_ELYTRA")
                || materialSafeCheck(ism, "NETHER_STAR") || materialSafeCheck(ism, "LEGACY_NETHER_STAR")
                || materialSafeCheck(ism, "CREEPER_HEAD")
                || materialSafeCheck(ism, "DRAGON_HEAD")
                || materialSafeCheck(ism, "PLAYER_HEAD")
                || materialSafeCheck(ism, "ZOMBIE_HEAD")
                || materialSafeCheck(ism, "CREEPER_HEAD")
                || materialSafeCheck(ism, "SKELETON_SKULL") || materialSafeCheck(ism, "LEGACY_SKULL")
                || materialSafeCheck(ism, "WITHER_SKELETON_SKULL")
                || materialSafeCheck(ism, "HEART_OF_THE_SEA")) {
            return 1; // uncommon
        } else if (materialSafeCheck(ism, "GOLDEN_APPLE") || materialSafeCheck(ism, "LEGACY_GOLDEN_APPLE")
                || materialSafeCheck(ism, "BEACON") || materialSafeCheck(ism, "LEGACY_BEACON")
                || materialSafeCheck(ism, "END_CRYSTAL") || materialSafeCheck(ism, "LEGACY_END_CRYSTAL")
                || materialSafeCheck(ism, "CONDUIT")
                || ism.name().startsWith("MUSIC_DISC_")) {
            return 2; // rare
        } else if (materialSafeCheck(ism, "COMMAND_BLOCK") || materialSafeCheck(ism, "LEGACY_COMMAND")
                || materialSafeCheck(ism, "REPEATING_COMMAND_BLOCK") || materialSafeCheck(ism, "LEGACY_COMMAND_CHAIN")
                || materialSafeCheck(ism, "CHAIN_COMMAND_BLOCK") || materialSafeCheck(ism, "LEGACY_COMMAND_REPEATING")
                || materialSafeCheck(ism, "DRAGON_EGG") || materialSafeCheck(ism, "LEGACY_DRAGON_EGG")
                || materialSafeCheck(ism, "STRUCTURE_BLOCK") || materialSafeCheck(ism, "LEGACY_STRUCTURE_BLOCK")
                || materialSafeCheck(ism, "ENCHANTED_GOLDEN_APPLE")) {
            return 3; // epic
        }
        return 0;
    }

    private int getXP(Player p) {
        int exp = 0;
        int level = p.getLevel();
        int levelexp = 0;
        if (level <= 16) {
            exp = level * level + 6 * level;
        } else if (level <= 31) {
            exp = (int) (2.5 * level * level - 40.5 * level + 360);
        } else {
            exp = (int) (4.5 * level * level - 162.5 * level + 2220);
        }
        if (level <= 15) {
            levelexp = 2 * level + 7;
        } else if (level <= 30) {
            levelexp = 5 * level - 38;
        } else {
            levelexp = 9 * level - 158;
        }
        return exp + Math.round(levelexp * p.getExp());
    }

    private void copyFormattingClassic(BaseComponent t, BaseComponent s) {
        if (s.getColorRaw() != null) {
            net.md_5.bungee.api.ChatColor cc = s.getColor();
            if (cc == null) {
                cc = net.md_5.bungee.api.ChatColor.WHITE;
            }
            t.setColor(cc);
            t.setBold(s.isBold());
            t.setItalic(s.isItalic());
            t.setUnderlined(s.isUnderlined());
            t.setStrikethrough(s.isStrikethrough());
            t.setObfuscated(s.isObfuscated());
        }
    }

    private void copyFormattingModern(BaseComponent t, BaseComponent s) {
        try {
            t.copyFormatting(s, FormatRetention.FORMATTING, true);
        } catch (NoSuchMethodError nme) {
            copyFormattingClassic(t, s);
        }
    }

    private void copyFormatting(BaseComponent t, BaseComponent s) {
        if (mcVer(1_008))
            copyFormattingModern(t, s);
        else
            copyFormattingClassic(t, s);
    }

    private void flattenComponentTree(List<BaseComponent> list, BaseComponent text) {
        list.add(text);
        if (text.getExtra() != null) {
            for (BaseComponent bc: text.getExtra()) {
                flattenComponentTree(list, bc);
            }
        }
    }

    private List<BaseComponent> flattenComponent(BaseComponent before, BaseComponent text) {
        List<BaseComponent> comps = new ArrayList<>();
        comps.add(before);
        flattenComponentTree(comps, text);
        return comps;
    }

    private String getBiomeName(String b) {
        try {
            ConfigurationSection c = pl.config.getConfigurationSection("custom-biome-names");
            for (String k: c.getKeys(false)) {
                if (k.equals(b))
                    return c.getString(k);
            }
        } catch (Exception ex) {}
        return this.correctCase(b);
    }

    private String getWorldName(String w) {
        try {
            ConfigurationSection c = pl.config.getConfigurationSection("custom-world-names");
            for (String k: c.getKeys(false)) {
                if (k.equals(w))
                    return c.getString(k);
            }
        } catch (Exception ex) {}
        return w;
    }
    
    boolean materialSafeCheck(Material m, String s) {
        return m.name().equalsIgnoreCase(s);
    }
    
    String correctCase(final String string) {
        return (String.valueOf(string.substring(0, 1).toUpperCase()) + string.substring(1).toLowerCase()).replace('_', ' ');
    }
    
    String mobNameConfigurate(final String s) {
        if (s.equalsIgnoreCase("ZOMBIFIED_PIGLIN")) {
            return "ZombifiedPiglin";
        }
        if (s.equalsIgnoreCase("BAT")) {
            return "Bat";
        }
        if (s.equalsIgnoreCase("BEE")) {
            return "Bee";
        }
        if (s.equalsIgnoreCase("BLAZE")) {
            return "Blaze";
        }
        if (s.equalsIgnoreCase("CAT")) {
            return "Cat";
        }
        if (s.equalsIgnoreCase("CAVE_SPIDER")) {
            return "CaveSpider";
        }
        if (s.equalsIgnoreCase("CHICKEN")) {
            return "Chicken";
        }
        if (s.equalsIgnoreCase("COMPLEX_PART")) {
            return "EnderDragon";
        }
        if (s.equalsIgnoreCase("COW")) {
            return "Cow";
        }
        if (s.equalsIgnoreCase("CREEPER")) {
            return "Creeper";
        }
        if (s.equalsIgnoreCase("DONKEY")) {
            return "Donkey";
        }
        if (s.equalsIgnoreCase("ENDER_DRAGON")) {
            return "EnderDragon";
        }
        if (s.equalsIgnoreCase("ELDER_GUARDIAN")) {
            return "ElderGuardian";
        }
        if (s.equalsIgnoreCase("GUARDIAN")) {
            return "Guardian";
        }
        if (s.equalsIgnoreCase("ENDERMAN")) {
            return "Enderman";
        }
        if (s.equalsIgnoreCase("ENDERMITE")) {
            return "Endermite";
        }
        if (s.equalsIgnoreCase("EVOKER")) {
            return "Evoker";
        }
        if (s.equalsIgnoreCase("FOX")) {
            return "Fox";
        }
        if (s.equalsIgnoreCase("GHAST")) {
            return "Ghast";
        }
        if (s.equalsIgnoreCase("GIANT")) {
            return "Giant";
        }
        if (s.equalsIgnoreCase("HORSE")) {
            return "Horse";
        }
        if (s.equalsIgnoreCase("ILLUSIONER")) {
            return "Illusioner";
        }
        if (s.equalsIgnoreCase("IRON_GOLEM")) {
            return "IronGolem";
        }
        if (s.equalsIgnoreCase("LLAMA")) {
            return "Llama";
        }
        if (s.equalsIgnoreCase("MAGMA_CUBE")) {
            return "MagmaCube";
        }
        if (s.equalsIgnoreCase("MUSHROOM_COW")) {
            return "Mooshroom";
        }
        if (s.equalsIgnoreCase("MULE")) {
            return "Mule";
        }
        if (s.equalsIgnoreCase("OCELOT")) {
            return "Ocelot";
        }
        if (s.equalsIgnoreCase("PANDA")) {
            return "Panda";
        }
        if (s.equalsIgnoreCase("PARROT")) {
            return "Parrot";
        }
        if (s.equalsIgnoreCase("PIG")) {
            return "Pig";
        }
        if (s.equalsIgnoreCase("PILLAGER")) {
            return "Pillager";
        }
        if (s.equalsIgnoreCase("POLAR_BEAR")) {
            return "PolarBear";
        }
        if (s.equalsIgnoreCase("RABBIT")) {
            return "Rabbit";
        }
        if (s.equalsIgnoreCase("RAVAGER")) {
            return "Ravager";
        }
        if (s.equalsIgnoreCase("SHEEP")) {
            return "Sheep";
        }
        if (s.equalsIgnoreCase("SHULKER")) {
            return "Shulker";
        }
        if (s.equalsIgnoreCase("SILVERFISH")) {
            return "Silverfish";
        }
        if (s.equalsIgnoreCase("SKELETON_HORSE")) {
            return "SkeletonHorse";
        }
        if (s.equalsIgnoreCase("WITHER_SKELETON")) {
            return "WitherSkeleton";
        }
        if (s.equalsIgnoreCase("SQUID")) {
            return "Squid";
        }
        if (s.equalsIgnoreCase("STRAY")) {
            return "Stray";
        }
        if (s.equalsIgnoreCase("SKELETON")) {
            return "Skeleton";
        }
        if (s.equalsIgnoreCase("SLIME")) {
            return "Slime";
        }
        if (s.equalsIgnoreCase("SNOWMAN")) {
            return "SnowGolem";
        }
        if (s.equalsIgnoreCase("SPIDER")) {
            return "Spider";
        }
        if (s.equalsIgnoreCase("SQUID")) {
            return "Squid";
        }
        if (s.equalsIgnoreCase("TRADER_LLAMA")) {
            return "TraderLlama";
        }
        if (s.equalsIgnoreCase("WITCH")) {
            return "Witch";
        }
        if (s.equalsIgnoreCase("WITHER")) {
            return "Wither";
        }
        if (s.equalsIgnoreCase("WOLF")) {
            return "Wolf";
        }
        if (s.equalsIgnoreCase("HUSK")) {
            return "Husk";
        }
        if (s.equalsIgnoreCase("VILLAGER")) {
            return "Villager";
        }
        if (s.equalsIgnoreCase("ZOMBIE_VILLAGER")) {
            return "ZombieVillager";
        }
        if (s.equalsIgnoreCase("ZOMBIE")) {
            return "Zombie";
        }
        if (s.equalsIgnoreCase("PIG_ZOMBIE")) {
            return mcVer(1_016) ? "ZombifiedPiglin" : "ZombiePigMan";
        }
        if (s.equalsIgnoreCase("VEX")) {
            return "Vex";
        }
        if (s.equalsIgnoreCase("VINDICATOR")) {
            return "Vindicator";
        }
        if (s.equalsIgnoreCase("DROWNED")) {
            return "Drowned";
        }
        if (s.equalsIgnoreCase("PHANTOM")) {
            return "Phantom";
        }
        if (s.equalsIgnoreCase("DOLPHIN")) {
            return "Dolphin";
        }
        if (s.equalsIgnoreCase("TURTLE")) {
            return "Turtle";
        }
        if (s.equalsIgnoreCase("COD")) {
            return "Cod";
        }
        if (s.equalsIgnoreCase("SALMON")) {
            return "Salmon";
        }
        if (s.equalsIgnoreCase("PUFFER_FISH")) {
            return "PufferFish";
        }
        if (s.equalsIgnoreCase("TROPICAL_FISH")) {
            return "TropicalFish";
        }
        if (s.equalsIgnoreCase("HOGLIN")) {
            return "Hoglin";
        }
        if (s.equalsIgnoreCase("PIGLIN")) {
            return "Piglin";
        }
        if (s.equalsIgnoreCase("ZOGLIN")) {
            return "Zoglin";
        }
        if (s.equalsIgnoreCase("STRIDER")) {
            return "Strider";
        }
        if (s.equalsIgnoreCase("PIGLIN_BRUTE")) {
            return "PiglinBrute";
        }
        return String.valueOf(s) + "(?)";
    }
    
    private TextComponent handleCustomMessages(PlayerDeathEvent e, Player p, Entity damager, boolean isPvP, String section, String death_tag, boolean use_player_for_get_killer_name) {
        TextComponent deathmsg = null;
        String checkname = this.getCheckName(damager, p, false);
        if (isPvP) {
            deathmsg = handleCustomPlayerMessages(e, p, damager, section, death_tag, checkname, use_player_for_get_killer_name);
        } else {
            deathmsg = handleCustomMobMessages(e, p, damager, section, death_tag, use_player_for_get_killer_name);
        }
        return deathmsg;
    }

    private TextComponent handleCustomPlayerMessages(PlayerDeathEvent e, Player p, Entity source, String section, String death_tag, String checkname, boolean use_player_for_get_killer_name) {
        TextComponent deathmsg = null;
        if (this.fc.isConfigurationSection("custom-user-death-messages")) {
            ConfigurationSection cc = this.fc.getConfigurationSection("custom-user-death-messages").getConfigurationSection("Potion");
            if (cc != null)
                for (String rx: cc.getKeys(false)) {
                    if (checkname.matches(rx.replace("_","."))) {
                        List<String> ls = cc.getStringList(rx);
                        if (ls.size() < 1) {
                            break;
                        }
                        deathmsg = this.formatV(e, source, death_tag, ls.get(r.nextInt(ls.size())), this.getKillerName(source, use_player_for_get_killer_name ? p : null), this.getKillerName2(source, use_player_for_get_killer_name ? p : null), ""); 
                        break;
                    }
                }
        }
        if (deathmsg == null) {
            checkname = this.getCheckName(source, p, true);
            if (this.fc.isConfigurationSection("custom-player-death-messages")) {
                ConfigurationSection cc = this.fc.getConfigurationSection("custom-player-death-messages").getConfigurationSection("Potion");
                if (cc != null)
                    for (String rx: cc.getKeys(false)) {
                        if (checkname.matches(rx.replace("_","."))) {
                            List<String> ls = cc.getStringList(rx);
                            if (ls.size() < 1) {
                                break;
                            }
                            deathmsg = this.formatV(e, source, death_tag, ls.get(r.nextInt(ls.size())), this.getKillerName(source, use_player_for_get_killer_name ? p : null), this.getKillerName2(source, use_player_for_get_killer_name ? p : null), ""); 
                            break;
                        }
                    }
            }
        }
        return deathmsg;
    }

    private TextComponent handleCustomMobMessages(PlayerDeathEvent e, Player p, Entity le, String section, String death_tag, boolean use_player_for_get_killer_name) {
        TextComponent deathmsg = null;
        if (this.fc.isConfigurationSection("custom-mob-death-messages")) {
            String checkname = this.getCheckName(le, p, false);
            ConfigurationSection cc = this.fc.getConfigurationSection("custom-mob-death-messages").getConfigurationSection(section);
            if (cc != null)
                for (String rx: cc.getKeys(false)) {
                    if (checkname.matches(rx.replace("_","."))) {
                        List<String> ls = cc.getStringList(rx);
                        if (ls.size() < 1) {
                            break;
                        }
                        deathmsg = this.formatV(e, le, death_tag, ls.get(r.nextInt(ls.size())), this.getKillerName(le, use_player_for_get_killer_name ? p : null), this.getKillerName2(le, use_player_for_get_killer_name ? p : null), ""); 
                        break;
                    }
                }
        }
        return deathmsg;
    }
    
    private boolean hasPiglinBrute() {
        return mcVerRev(1_016, 2);
    }
    
    private String getReasonForMob(String mob, LivingEntity le) {
        if (le.getCustomName() == null || !passable(le.getCustomName())) {
            return "mob." + mob;
        } else {
            return getNamedMobSection() + "." + mob;
        }
    }
    
    private String getLEName(String customName) {
        if (customName != null && passable(customName)) {
            return customName;
        } else {
            return "";
        }
    }
    
    // the big one
    synchronized void onPlayerDeath_i(final PlayerDeathEvent e) {
        final Player p = e.getEntity();
        final UUID pu = p.getUniqueId();
        
        // tempban
        if (pl.tempban.containsKey(pu)) {
            long now = new Date().getTime();
            long then = pl.tempban.get(pu);
            if ((now - then) >= 1000L * pl.config.getInt("cooldown-death-cooldown", 0)) {
                pl.tempban.remove(pu);
            }
        }
        if (!pl.tempban.containsKey(pu)) {
            if (!tempbanC.containsKey(pu)) {
                tempbanC.put(pu, 1L);
                tempbanT.put(pu, new Date().getTime());
            } else {
                long d = new Date().getTime();
                if ((d - tempbanT.get(pu)) >= (pl.config.getInt("cooldown-death-interval", 1) * 1000)) {
                    tempbanC.put(pu, 1L);
                    tempbanT.put(pu, d);
                } else {
                    long c = tempbanC.get(pu);
                    ++c;
                    tempbanC.put(pu, c);
                    if (c > pl.config.getInt("cooldown-death-count", 20)) {
                        long now = new Date().getTime();
                        tempbanC.put(pu, 1L);
                        tempbanT.put(pu, now);
                        pl.tempban.put(pu, now);
                    }
                }
            }
        } else if (pl.config.getBoolean("cooldown-death-reset", false)) {
            pl.tempban.put(pu, new Date().getTime());
        }

        // clear out API death message
        e.setDeathMessage("");
        
        // damage info setup
        this.lastvictim = p;
        this.lastkiller = null;
        String vdeathmsg = "";
        boolean isPvP = false;
        pvphm.put(pu, false);
        TextComponent deathmsg = null;
        Entity damager = null;
        final World w = p.getWorld();
        try {
            damager = (Entity) p.getMetadata("dmp.lastDamageEnt").get(0).value();
        }
        catch (Exception ex3) {}
        EntityDamageEvent.DamageCause d = EntityDamageEvent.DamageCause.CUSTOM;
        if (p.getLastDamageCause() != null) {
            d = p.getLastDamageCause().getCause();
        } else if (p.hasMetadata("dmp.lastCause") && p.getMetadata("dmp.lastCause").size() > 0) {
            d = EntityDamageEvent.DamageCause.valueOf(p.getMetadata("dmp.lastCause").get(0).asString());
        }
        
        if (this.pl.debug) {
            this.broadcastDebug("§ePlayer " + p.getName(), p, w, false);
            this.broadcastDebug("§eDamage cause " + d.toString(), p, w, false);
            if (p.hasMetadata("dmp.lastDamage")) {
                this.broadcastDebug("§eFound entity damager " + String.valueOf(damager) + " | " + this.getKillerName(damager, p), p, w, false);
            }
            else if (p.hasMetadata("dmp.lastDamageEx")) {
                this.broadcastDebug("§eFound extended entity damager " + this.getKillerName(damager, p), p, w, false);
            }
            else {
                this.broadcastDebug("§eFound no entity damager", p, w, false);
            }
            if (damager == null) {
                this.broadcastDebug("§eDamager entity could not be retrieved", p, w, false);
            }
            else {
                this.broadcastDebug("§eDamager entity could be retrieved: " + damager.toString(), p, w, false);
            }
        }
        
        final Location l = p.getLocation();
        try {
            vdeathmsg = "Death[Player=" + p.getName() + ",LastCause=" + d.toString() + ",Damager=" + (p.hasMetadata("dmp.lastDamage") ? (String.valueOf(String.valueOf(damager)) + "(" + this.getKillerName(damager, p) + ")") : (p.hasMetadata("dmp.lastDamageEx") ? (String.valueOf(String.valueOf(damager)) + "(" + this.getKillerName(damager, p) + ")EX") : "null")) + ",Location=[" + l.getWorld().getName() + "](" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() + ")";
        }
        catch (Exception ex) {
            try {
                vdeathmsg = "Death[Player=" + p.getName() + ",LastCause=" + d.toString() + ",Damager=???,Location=[" + l.getWorld().getName() + "](" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() + ")";
            }
            catch (Exception ex2) {
                vdeathmsg = "Death[Player=" + p.getName() + ",LastCause=???,Damager=???,Location=[" + l.getWorld().getName() + "](" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() + ")";
            }
        }
        
        EntityDamageEvent.DamageCause dc = DamageCause.CUSTOM;
        if (d != null) dc = d;
        double dmgd = 0;
        try {
            dmgd = p.getMetadata("dmp.lastDamageSize").get(0).asDouble();
        } catch (Exception ex) {}
        
        // pre DMP event
        
        DeathPreDMPEvent pree = new DeathPreDMPEvent(p, damager, dc, dmgd);
        pl.getServer().getPluginManager().callEvent(pree);
        if (!this.pl.debug && damager == null) {
            p.removeMetadata("dmp.lastDamage", (Plugin)this.pl);
            p.removeMetadata("dmp.lastDamageEx", (Plugin)this.pl);
        }
        String death_tag = "unknown";
        p.removeMetadata("dmp.lastDamageSize", (Plugin)this.pl);
        
        
        
        // #####################################
        
        // start scanning for damage causes here
        // this should be split into smaller functions as part of refactoring

        // #####################################
        

        if (d == EntityDamageEvent.DamageCause.STARVATION) {
            deathmsg = this.formatV(e, death_tag = "natural.Starvation", this.getMessage("death-messages.natural.Starvation"), "", "");
        }
        else if (d == EntityDamageEvent.DamageCause.SUFFOCATION) {
            deathmsg = this.formatV(e, death_tag = "natural.Suffocation", this.getMessage("death-messages.natural.Suffocation"), "", "");
        }
        else if (mcVer(1_009) && d == EntityDamageEvent.DamageCause.FLY_INTO_WALL) {
            damager = resolveDamager(p, damager);
            if (damager != null && p.hasMetadata("dmp.lastDamage")) {
                if (damager instanceof Player) {
                    isPvP = true;
                    pvphm.put(pu, true);
                    this.lastkiller = (Player)damager;
                }
                TextComponent result = handleCustomMessages(e, p, damager, isPvP, "ElytraKill", " natural.ElytraKill", true);
                if (result != null) {
                    deathmsg = result;
                    death_tag = " natural.ElytraKill";
                } else {
                    deathmsg = this.formatV(e, damager, death_tag = "natural.ElytraKill", this.getMessage("death-messages.natural.ElytraKill"), this.getKillerName(damager, p), this.getKillerName2(damager, p), "");
                }
            }
            else {
                deathmsg = this.formatV(e, death_tag = "natural.Elytra", this.getMessage("death-messages.natural.Elytra"), "", "");
            }
        }
        else if (mcVer(1_009) && d == EntityDamageEvent.DamageCause.DRAGON_BREATH) {
            deathmsg = this.formatV(e, death_tag = "mob.EnderDragonBreath", this.getMessage("death-messages.mob.EnderDragonBreath"), "", "");
        }
        else if (d == EntityDamageEvent.DamageCause.MELTING) {
            deathmsg = this.formatV(e, death_tag = "natural.Melting", this.getMessage("death-messages.natural.Melting"), "", "");
        }
        else if (d == EntityDamageEvent.DamageCause.CUSTOM) {
            deathmsg = this.formatV(e, death_tag = "natural.Custom", this.getMessage("death-messages.natural.Custom"), "", "");
        }
        else if (d == EntityDamageEvent.DamageCause.SUICIDE) {
            deathmsg = this.formatV(e, death_tag = "natural.Suicide", this.getMessage("death-messages.natural.Suicide"), "", "");
        }
        else if (mcVer(1_013) && d == EntityDamageEvent.DamageCause.DRYOUT) {
            deathmsg = this.formatV(e, death_tag = "natural.DryOut", this.getMessage("death-messages.natural.DryOut"), "", "");
        }
        else if (d == EntityDamageEvent.DamageCause.FALLING_BLOCK) {
            if (damager != null && isAnvil((FallingBlock) damager)) {
                damager = resolveDamager(p, damager);
                if (damager != null && p.hasMetadata("dmp.lastDamage")) {
                    if (damager instanceof Player) {
                        isPvP = true;
                        pvphm.put(pu, true);
                        this.lastkiller = (Player)damager;
                    }
                    TextComponent result = handleCustomMessages(e, p, damager, isPvP, "AnvilKill", " natural.AnvilKill", true);
                    if (result != null) {
                        deathmsg = result;
                        death_tag = " natural.AnvilKill";
                    } else {
                        deathmsg = this.formatV(e, damager, death_tag = "natural.AnvilKill", this.getMessage("death-messages.natural.AnvilKill"), this.getKillerName(damager, p), this.getKillerName2(damager, p), "");
                    }
                }
                else {
                    deathmsg = this.formatV(e, death_tag = "natural.Anvil", this.getMessage("death-messages.natural.Anvil"), "", "");
                }
            } else {
                if (damager != null && p.hasMetadata("dmp.lastDamage")) {
                    if (damager instanceof Player) {
                        isPvP = true;
                        pvphm.put(pu, true);
                        this.lastkiller = (Player)damager;
                    }
                    TextComponent result = handleCustomMessages(e, p, damager, isPvP, "FallingBlockKill", " natural.FallingBlockKill", true);
                    if (result != null) {
                        deathmsg = result;
                        death_tag = " natural.FallingBlockKill";
                    } else {
                        deathmsg = this.formatV(e, damager, death_tag = "natural.FallingBlockKill", this.getMessage("death-messages.natural.FallingBlockKill"), this.getKillerName(damager, p), this.getKillerName2(damager, p), "");
                    }
                }
                else {
                    deathmsg = this.formatV(e, death_tag = "natural.FallingBlock", this.getMessage("death-messages.natural.FallingBlock"), "", "");
                }
            }
        }
        else if (d == EntityDamageEvent.DamageCause.CONTACT) {
            damager = resolveDamager(p, damager);
            String contactType = "Cactus";
            if (mcVer(1_014) && p.getWorld().getBlockAt(p.getLocation()).getType() == Material.SWEET_BERRY_BUSH) {
                contactType = "BerryBush";
            }
            if (damager != null && p.hasMetadata("dmp.lastDamage")) {
                if (damager instanceof Player) {
                    isPvP = true;
                    pvphm.put(pu, true);
                    this.lastkiller = (Player)damager;
                }
                TextComponent result = handleCustomMessages(e, p, damager, isPvP, contactType + "Kill", " natural." + contactType + "Kill", true);
                if (result != null) {
                    deathmsg = result;
                    death_tag = " natural." + contactType + "Kill";
                } else {
                    deathmsg = this.formatV(e, damager, death_tag = "natural." + contactType + "Kill", this.getMessage("death-messages.natural." + contactType + "Kill"), this.getKillerName(damager, p), this.getKillerName2(damager, p), "");
                }
            }
            else {
                deathmsg = this.formatV(e, death_tag = "natural." + contactType, this.getMessage("death-messages.natural." + contactType), "", "");
            }
        }
        else if (d == EntityDamageEvent.DamageCause.FIRE) {
            damager = resolveDamager(p, damager);
            if (damager != null && p.hasMetadata("dmp.lastDamage")) {
                if (damager instanceof Player) {
                    isPvP = true;
                    pvphm.put(pu, true);
                    this.lastkiller = (Player)damager;
                }
                TextComponent result = handleCustomMessages(e, p, damager, isPvP, "FireBlockKill", " natural.FireBlockKill", true);
                if (result != null) {
                    deathmsg = result;
                    death_tag = " natural.FireBlockKill";
                } else {
                    deathmsg = this.formatV(e, damager, death_tag = "natural.FireBlockKill", this.getMessage("death-messages.natural.FireBlockKill"), this.getKillerName(damager, p), this.getKillerName2(damager, p), "");
                }
            }
            else {
                deathmsg = this.formatV(e, death_tag = "natural.FireBlock", this.getMessage("death-messages.natural.FireBlock"), "", "");
            }
        }
        else if (d == EntityDamageEvent.DamageCause.FIRE_TICK) {
            damager = resolveDamager(p, damager);
            if (damager != null && p.hasMetadata("dmp.lastDamage")) {
                if (damager instanceof Player) {
                    isPvP = true;
                    pvphm.put(pu, true);
                    this.lastkiller = (Player)damager;
                }
                TextComponent result = handleCustomMessages(e, p, damager, isPvP, "FireTickKill", " natural.FireTickKill", true);
                if (result != null) {
                    deathmsg = result;
                    death_tag = " natural.FireTickKill";
                } else {
                    deathmsg = this.formatV(e, damager, death_tag = "natural.FireTickKill", this.getMessage("death-messages.natural.FireTickKill"), this.getKillerName(damager, p), this.getKillerName2(damager, p), "");
                }
            }
            else {
                deathmsg = this.formatV(e, death_tag = "natural.FireTick", this.getMessage("death-messages.natural.FireTick"), "", "");
            }
        }
        else if (mcVer(1_011) && d == EntityDamageEvent.DamageCause.CRAMMING) {
            damager = resolveDamager(p, damager);
            if (damager != null && p.hasMetadata("dmp.lastDamage")) {
                if (damager instanceof Player) {
                    isPvP = true;
                    pvphm.put(pu, true);
                    this.lastkiller = (Player)damager;
                }
                TextComponent result = handleCustomMessages(e, p, damager, isPvP, "CrammingKill", " natural.CrammingKill", true);
                if (result != null) {
                    deathmsg = result;
                    death_tag = " natural.CrammingKill";
                } else {
                    deathmsg = this.formatV(e, damager, death_tag = "natural.CrammingKill", this.getMessage("death-messages.natural.CrammingKill"), this.getKillerName(damager, p), this.getKillerName2(damager, p), "");
                }
            }
            else {
                deathmsg = this.formatV(e, death_tag = "natural.Cramming", this.getMessage("death-messages.natural.Cramming"), "", "");
            }
        }
        else if (d == EntityDamageEvent.DamageCause.DROWNING) {
            damager = resolveDamager(p, damager);
            if (damager != null && p.hasMetadata("dmp.lastDamage")) {
                if (damager instanceof Player) {
                    isPvP = true;
                    pvphm.put(pu, true);
                    this.lastkiller = (Player)damager;
                }
                TextComponent result = handleCustomMessages(e, p, damager, isPvP, "DrowningKill", " natural.DrowningKill", true);
                if (result != null) {
                    deathmsg = result;
                    death_tag = " natural.DrowningKill";
                } else {
                    deathmsg = this.formatV(e, damager, death_tag = "natural.DrowningKill", this.getMessage("death-messages.natural.DrowningKill"), this.getKillerName(damager, p), this.getKillerName2(damager, p), "");
                }
            }
            else {
                deathmsg = this.formatV(e, death_tag = "natural.Drowning", this.getMessage("death-messages.natural.Drowning"), "", "");
            }
        }
        else if (d == EntityDamageEvent.DamageCause.WITHER) {
            damager = resolveDamager(p, damager);
            if (damager != null && p.hasMetadata("dmp.lastDamage")) {
                if (damager instanceof Player) {
                    isPvP = true;
                    pvphm.put(pu, true);
                    this.lastkiller = (Player)damager;
                }
                TextComponent result = handleCustomMessages(e, p, damager, isPvP, "PotionWitherKill", " natural.PotionWitherKill", true);
                if (result != null) {
                    deathmsg = result;
                    death_tag = " natural.PotionWitherKill";
                } else {
                    deathmsg = this.formatV(e, damager, death_tag = "natural.PotionWitherKill", this.getMessage("death-messages.natural.PotionWitherKill"), this.getKillerName(damager, p), this.getKillerName2(damager, p), "");
                }
            }
            else {
                deathmsg = this.formatV(e, death_tag = "natural.PotionWither", this.getMessage("death-messages.natural.PotionWither"), "", "");
            }
        }
        else if (d == EntityDamageEvent.DamageCause.POISON) {
            damager = resolveDamager(p, damager);
            if (damager != null && p.hasMetadata("dmp.lastDamage")) {
                if (damager instanceof Player) {
                    isPvP = true;
                    pvphm.put(pu, true);
                    this.lastkiller = (Player)damager;
                }
                TextComponent result = handleCustomMessages(e, p, damager, isPvP, "PotionPoisonKill", " natural.PotionPoisonKill", true);
                if (result != null) {
                    deathmsg = result;
                    death_tag = " natural.PotionPoisonKill";
                } else {
                    deathmsg = this.formatV(e, damager, death_tag = "natural.PotionPoisonKill", this.getMessage("death-messages.natural.PotionPoisonKill"), this.getKillerName(damager, p), this.getKillerName2(damager, p), "");
                }
            }
            else {
                deathmsg = this.formatV(e, death_tag = "natural.PotionPoison", this.getMessage("death-messages.natural.PotionPoison"), "", "");
            }
        }
        else if (d == EntityDamageEvent.DamageCause.LAVA) {
            damager = resolveDamager(p, damager);
            if (damager != null && p.hasMetadata("dmp.lastDamage")) {
                if (damager instanceof Player) {
                    isPvP = true;
                    pvphm.put(pu, true);
                    this.lastkiller = (Player)damager;
                }
                TextComponent result = handleCustomMessages(e, p, damager, isPvP, "LavaKill", " natural.LavaKill", true);
                if (result != null) {
                    deathmsg = result;
                    death_tag = " natural.LavaKill";
                } else {
                    deathmsg = this.formatV(e, damager, death_tag = "natural.LavaKill", this.getMessage("death-messages.natural.LavaKill"), this.getKillerName(damager, p), this.getKillerName2(damager, p), "");
                }
            }
            else {
                deathmsg = this.formatV(e, death_tag = "natural.Lava", this.getMessage("death-messages.natural.Lava"), "", "");
            }
        }
        else if (mcVer(1_010) && d == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            damager = resolveDamager(p, damager);
            if (damager != null && p.hasMetadata("dmp.lastDamage")) {
                if (damager instanceof Player) {
                    isPvP = true;
                    pvphm.put(pu, true);
                    this.lastkiller = (Player)damager;
                }
                TextComponent result = handleCustomMessages(e, p, damager, isPvP, "MagmaKill", " natural.MagmaKill", true);
                if (result != null) {
                    deathmsg = result;
                    death_tag = " natural.MagmaKill";
                } else {
                    deathmsg = this.formatV(e, damager, death_tag = "natural.MagmaKill", this.getMessage("death-messages.natural.MagmaKill"), this.getKillerName(damager, p), this.getKillerName2(damager, p), "");
                }
            }
            else {
                deathmsg = this.formatV(e, death_tag = "natural.Magma", this.getMessage("death-messages.natural.Magma"), "", "");
            }
        }
        else if (d == EntityDamageEvent.DamageCause.VOID) {
            damager = resolveDamager(p, damager);
            if (damager != null && p.hasMetadata("dmp.lastDamage")) {
                if (damager instanceof Player) {
                    isPvP = true;
                    pvphm.put(pu, true);
                    this.lastkiller = (Player)damager;
                }
                TextComponent result = handleCustomMessages(e, p, damager, isPvP, "VoidKill", " natural.VoidKill", true);
                if (result != null) {
                    deathmsg = result;
                    death_tag = " natural.VoidKill";
                } else {
                    deathmsg = this.formatV(e, damager, death_tag = "natural.VoidKill", this.getMessage("death-messages.natural.VoidKill"), this.getKillerName(damager, p), this.getKillerName2(damager, p), "");
                }
            }
            else {
                if (p.getFallDistance() > (64 - p.getLocation().getY())) {
                    deathmsg = this.formatV(e, death_tag = "natural.VoidFall", this.getMessage("death-messages.natural.VoidFall"), "", "");
                } else {
                    deathmsg = this.formatV(e, death_tag = "natural.Void", this.getMessage("death-messages.natural.Void"), "", "");
                }
            }
        }
        else if (d == EntityDamageEvent.DamageCause.THORNS) {
            if (damager instanceof Player) {
                isPvP = true;
                pvphm.put(pu, true);
                this.lastkiller = (Player)damager;
            }
            TextComponent result = handleCustomMessages(e, p, damager, isPvP, "Thorns", " natural.Thorns", true);
            if (result != null) {
                deathmsg = result;
                death_tag = " natural.Thorns";
            } else {
                deathmsg = this.formatV(e, damager, death_tag = "natural.Thorns", this.getMessage("death-messages.natural.Thorns"), this.getKillerName(damager, p), this.getKillerName2(damager, p), "");
            }
        }
        else if (d == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            if (damager instanceof TNTPrimed) {
                Entity source = null;
                try {
                    source = (Entity) p.getMetadata("dmp.lastDamageEnt2").get(0).value();
                }
                catch (Exception ex4) {}
                if (this.pl.debug) {
                    if (source != null) {
                        this.broadcastDebug("Â§eTNT igniter could be retrieved: " + source.toString(), p, w, false);
                    }
                    else {
                        this.broadcastDebug("Â§eTNT igniter could not be retrieved", p, w, false);
                    }
                }
                boolean selfIgnited = false;
                if (source instanceof Player) {
                    selfIgnited = ((Player)source).getName().equalsIgnoreCase(p.getName());
                }
                if (source instanceof LivingEntity && !selfIgnited) {
                    TextComponent result = handleCustomMessages(e, p, source, isPvP, "TNTKill", " natural.TNTKill", false);
                    if (result != null) {
                        deathmsg = result;
                        death_tag = " natural.TNTKill";
                    } else {
                        deathmsg = this.formatV(e, source, death_tag = "natural.TNTKill", this.getMessage("death-messages.natural.TNTKill"), this.getKillerName(source), this.getKillerName2(source), "");
                    }
                }
                else if (source instanceof Projectile) {
                    selfIgnited = false;
                    final Entity shooter = (Entity)((Projectile)source).getShooter();
                    if (shooter instanceof Player) {
                        selfIgnited = ((Player)shooter).getName().equalsIgnoreCase(p.getName());
                        if (source instanceof LivingEntity && !selfIgnited) {
                            isPvP = true;
                            pvphm.put(pu, true);
                            this.lastkiller = (Player)shooter;
                            TextComponent result = handleCustomMessages(e, p, source, isPvP, "TNTKill", " natural.TNTKill", false);
                            if (result != null) {
                                deathmsg = result;
                                death_tag = " natural.TNTKill";
                            } else {
                                deathmsg = this.formatV(e, source, death_tag = "natural.TNTKill", this.getMessage("death-messages.natural.TNTKill"), this.getKillerName(source), this.getKillerName2(source), "");
                            }
                        }
                        else {
                            deathmsg = this.formatV(e, death_tag = "natural.TNT", this.getMessage("death-messages.natural.TNT"), "", "");
                        }
                    }
                }
                else {
                    deathmsg = this.formatV(e, death_tag = "natural.TNT", this.getMessage("death-messages.natural.TNT"), "", "");
                }
            }
            else {
                long now = System.currentTimeMillis();
                UUID uuid = p.getUniqueId();
                if (this.bedTicks.containsKey(uuid) && (now - this.bedTicks.get(uuid)) < 100L) {
                    deathmsg = this.formatV(e, death_tag = "natural.Bed", this.getMessage("death-messages.natural.Bed"), "", "");
                } else
                    deathmsg = this.formatV(e, death_tag = "unknown", this.getMessage("death-messages.unknown"), "", "");
            }
        }
        else if (d == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            if (damager instanceof Creeper) {
                final LivingEntity le = (LivingEntity)damager;
                TextComponent result = handleCustomMobMessages(e, p, le, "TNTKill", " natural.TNTKill", false);
                if (result != null) {
                    deathmsg = result;
                    death_tag = " natural.TNTKill";
                } else {
                    String csuf = "";
                    if (((Creeper) le).isPowered())
                        csuf = "Charged";
                    
                    if (le.getCustomName() == null || !passable(le.getCustomName())) {
                        deathmsg = this.formatV(e, death_tag = "mob.Creeper" + csuf, this.getMessage("death-messages.mob.Creeper" + csuf), "", "");
                    } else {
                        deathmsg = this.formatV(e, death_tag = getNamedMobSection() + ".Creeper" + csuf, this.getMessage("death-messages." + getNamedMobSection() + ".Creeper" + csuf), le.getCustomName(), "");
                    }
                }
            }
            else if (damager instanceof EnderCrystal) {
                deathmsg = this.formatV(e, death_tag = "natural.EnderCrystal", this.getMessage("death-messages.natural.EnderCrystal"), "", "");
            }
            else if (damager instanceof Wither) {
                final LivingEntity le = (LivingEntity)damager;
                TextComponent result = handleCustomMobMessages(e, p, le, "TNTKill", " natural.TNTKill", false);
                if (result != null) {
                    deathmsg = result;
                    death_tag = " natural.TNTKill";
                } else {
                    if (le.getCustomName() == null || !passable(le.getCustomName())) {
                        deathmsg = this.formatV(e, death_tag = "mob.WitherSpawnBoom", this.getMessage("death-messages.mob.WitherSpawnBoom"), "", "");
                    } else {
                        deathmsg = this.formatV(e, death_tag = getNamedMobSection() + ".WitherSpawnBoom", this.getMessage("death-messages." + getNamedMobSection() + ".WitherSpawnBoom"), le.getCustomName(), "");
                    }
                }
            }
            else if (damager instanceof WitherSkull) {
                deathmsg = null;
                if (mcVer(1_016))
                {
                    ProjectileSource source = ((WitherSkull)damager).getShooter();
                    if (source != null && source instanceof LivingEntity)
                    {
                        LivingEntity le = (LivingEntity)source;
                        TextComponent result = handleCustomMessages(e, p, le, isPvP, "WitherSkull", " natural.WitherSkull", false);
                        if (result != null) {
                            deathmsg = result;
                            death_tag = " natural.WitherSkull";
                        } else {
                            deathmsg = this.formatV(e, le, death_tag = " natural.WitherSkull", this.getMessage("death-messages.natural.WitherSkull"), this.getKillerName(le), this.getKillerName2(le), "");
                        }
                    }
                }
                if (deathmsg == null)
                    deathmsg = this.formatV(e, death_tag = "natural.TNT", this.getMessage("death-messages.natural.TNT"), "", "");
            }
            else if (damager instanceof Firework) {
                boolean src = false;
                if (mcVer(1_016) && damager instanceof Projectile)
                {
                    boolean selfIgnited;
                    final Entity shooter = (Entity)((Projectile)damager).getShooter();
                    if (shooter instanceof Player) {
                        selfIgnited = ((Player)shooter).getName().equalsIgnoreCase(p.getName());
                        if (damager instanceof LivingEntity && !selfIgnited) {
                            String wn = null;
                            isPvP = true;
                            pvphm.put(pu, true);
                            Player pshoot = (Player)shooter;
                            this.lastkiller = pshoot;
                            ItemStack is = getItemInMainHand(pshoot.getInventory());
                            if (mcVer(1_009) && is.getType() != Material.CROSSBOW)
                                is = pshoot.getInventory().getItemInOffHand();
                            if (is.getType() == Material.CROSSBOW)
                            {
                                if (is.getItemMeta() != null && !isEmptyDisplayName(is.getItemMeta().getDisplayName())) {
                                    wn = is.getItemMeta().getDisplayName();
                                } else if (wn == null && this.fc.getBoolean("show-custom-death-msg-on-all-weapons", false)) {
                                    if (is != null && is.getAmount() > 0 && is.getType() != Material.AIR)
                                        wn = itemStackNameToString(is);
                                }
                            }
                            String cfc = wn != null ? "Custom" : "";
                            if (wn == null) wn = "";
                            TextComponent result = handleCustomMessages(e, p, damager, isPvP, "TNTKill", " natural.CrossbowFirework" + cfc, false);
                            if (result != null) {
                                deathmsg = result;
                                death_tag = " natural.CrossbowFirework" + cfc;
                            } else {
                                deathmsg = this.formatV(e, damager, death_tag = "natural.CrossbowFirework" + cfc, this.getMessage("death-messages.natural.CrossbowFirework" + cfc), this.getKillerName(damager), this.getKillerName2(damager), wn);
                            }
                        }
                        else {
                            deathmsg = this.formatV(e, death_tag = "natural.Firework", this.getMessage("death-messages.natural.Firework"), "", "");
                        }
                    }
                }
                if (!src)
                {
                    damager = resolveDamager(p, damager);
                    if (damager != null && p.hasMetadata("dmp.lastDamage")) {
                        if (damager instanceof Player) {
                            isPvP = true;
                            pvphm.put(pu, true);
                            this.lastkiller = (Player)damager;
                        }
                        TextComponent result = handleCustomMessages(e, p, damager, isPvP, "FireworkKill", " natural.FireworkKill", true);
                        if (result != null) {
                            deathmsg = result;
                            death_tag = " natural.FireworkKill";
                        } else {
                            deathmsg = this.formatV(e, damager, death_tag = "natural.FireworkKill", this.getMessage("death-messages.natural.FireworkKill"), this.getKillerName(damager, p), this.getKillerName2(damager, p), "");
                        }
                    }
                    else {
                        deathmsg = this.formatV(e, death_tag = "natural.Firework", this.getMessage("death-messages.natural.Firework"), "", "");
                    }   
                }
            }
            else if (!(damager instanceof TNTPrimed)) {
                deathmsg = this.formatV(e, death_tag = "natural.TNT", this.getMessage("death-messages.natural.TNT"), "", "");
            }
            else {
                Entity source = null;
                try {
                    source = (Entity) p.getMetadata("dmp.lastDamageEnt2").get(0).value();
                }
                catch (Exception ex4) {}
                if (this.pl.debug) {
                    if (source != null) {
                        this.broadcastDebug("Â§eTNT igniter could be retrieved: " + source.toString(), p, w, false);
                    }
                    else {
                        this.broadcastDebug("Â§eTNT igniter could not be retrieved", p, w, false);
                    }
                }
                boolean selfIgnited = false;
                if (source instanceof Player) {
                    selfIgnited = ((Player)source).getName().equalsIgnoreCase(p.getName());
                }
                if (source instanceof LivingEntity && !selfIgnited) {
                    TextComponent result = handleCustomMessages(e, p, source, isPvP, "TNTKill", " natural.TNTKill", false);
                    if (result != null) {
                        deathmsg = result;
                        death_tag = " natural.TNTKill";
                    } else {
                        deathmsg = this.formatV(e, source, death_tag = " natural.TNTKill", this.getMessage("death-messages.natural.TNTKill"), this.getKillerName(source), this.getKillerName2(source), "");
                    }
                }
                else if (source instanceof Projectile) {
                    selfIgnited = false;
                    final Entity shooter = (Entity)((Projectile)source).getShooter();
                    if (shooter instanceof Player) {
                        selfIgnited = ((Player)shooter).getName().equalsIgnoreCase(p.getName());
                        if (source instanceof LivingEntity && !selfIgnited) {
                            isPvP = true;
                            pvphm.put(pu, true);
                            this.lastkiller = (Player)shooter;
                            TextComponent result = handleCustomMessages(e, p, source, isPvP, "TNTKill", " natural.TNTKill", false);
                            if (result != null) {
                                deathmsg = result;
                                death_tag = " natural.TNTKill";
                            } else {
                                deathmsg = this.formatV(e, source, death_tag = "natural.TNTKill", this.getMessage("death-messages.natural.TNTKill"), this.getKillerName(source), this.getKillerName2(source), "");
                            }
                        }
                        else {
                            deathmsg = this.formatV(e, death_tag = "natural.TNT", this.getMessage("death-messages.natural.TNT"), "", "");
                        }
                    }
                }
                else {
                    deathmsg = this.formatV(e, death_tag = "natural.TNT", this.getMessage("death-messages.natural.TNT"), "", "");
                }
            }
        }
        else if (d == EntityDamageEvent.DamageCause.FALL) {
            long now = System.currentTimeMillis();
            Material m = Material.AIR;
            UUID uuid = p.getUniqueId();
            // do we know the last block the player touched?
            if (lastTicks.containsKey(uuid)) {
                if ((now - lastTicks.get(uuid)) < 5000L) {
                    m = lastBlock.get(uuid);
                }
            }
            String safeAlways = "";
            if (m == Material.LADDER)
                safeAlways = "Ladder";
            if (m == Material.VINE)
                safeAlways = "Vine";
            if (mcVer(1_016) && m == Material.TWISTING_VINES)
                safeAlways = "TwistingVine";
            if (mcVer(1_016) && m == Material.WEEPING_VINES)
                safeAlways = "WeepingVine";
            if (mcVer(1_016) && isTrapdoor(m))
                safeAlways = "Trapdoor";
            if (mcVer(1_016) && m == Material.SCAFFOLDING)
                safeAlways = "Scaffolding";
            String safeKill = safeAlways;
            if (p.hasMetadata("dmp.lastDamageExpl"))
                safeKill = "Explosion";
            if (!p.hasMetadata("dmp.lastDamage") || !p.getMetadata("dmp.lastDamage").get(0).asString().equalsIgnoreCase("[mob]ENDER_PEARL"))
                damager = resolveDamager(p, damager);
            if (p.hasMetadata("dmp.lastDamage")) {
                //System.out.println("has last damage, " + safeKill);
                if (p.getMetadata("dmp.lastDamage").get(0).asString().equalsIgnoreCase("[mob]ENDER_PEARL")) {
                    deathmsg = this.formatV(e, death_tag = "natural.FallShort", this.getMessage("death-messages.natural.FallShort"), "", "");
                }
                else if (damager != null) {
                    if (damager instanceof Player) {
                        isPvP = true;
                        pvphm.put(pu, true);
                        this.lastkiller = (Player)damager;
                    }
                    TextComponent result = handleCustomMessages(e, p, damager, isPvP, "FallKill", " natural.FallKill", true);
                    if (result != null) {
                        deathmsg = result;
                        death_tag = " natural.FallKill";
                    } else {
                        deathmsg = null;
                        if (safeKill.isEmpty()) {
                            String wpn2 = "";
                            isPvP = true;
                            pvphm.put(pu, true);
                            if (damager instanceof Player)
                                this.lastkiller = (Player) damager;
                            ItemStack is3 = null;
                            if (damager instanceof LivingEntity && ((LivingEntity)damager).getEquipment() != null && getItemInMainHand(((LivingEntity)damager).getEquipment()) != null) {
                                String cn = null;
                                final ItemStack is = getItemInMainHand(((LivingEntity)damager).getEquipment());
                                if (is.getItemMeta() != null && !isEmptyDisplayName(is.getItemMeta().getDisplayName())) {
                                    cn = is.getItemMeta().getDisplayName();
                                } else if (cn == null && this.fc.getBoolean("show-custom-death-msg-on-all-weapons", false)) {
                                    if (is != null && is.getAmount() > 0 && is.getType() != Material.AIR)
                                        cn = itemStackNameToString(is);
                                }
                                if (cn != null) {
                                    wpn2 = cn;
                                    is3 = is;
                                    deathmsg = this.formatV(e, damager, death_tag = "natural.FallKillWeapon", this.getMessage("death-messages.natural.FallKillWeapon"), this.getKillerName(damager, p), this.getKillerName2(damager, p), wpn2, is3);
                                }
                            }
                        }
                        if (deathmsg == null)
                            deathmsg = this.formatV(e, damager, death_tag = "natural.Fall" + safeKill + "Kill", this.getMessage("death-messages.natural.Fall" + safeKill + "Kill"), this.getKillerName(damager, p), this.getKillerName2(damager, p), "");
                    }
                }/*
                else {
                    String entType = p.getMetadata("dmp.lastDamage").get(0).asString();
                    if (entType.equalsIgnoreCase("[mob]CREEPER")) {
                        deathmsg = this.formatV(e, death_tag = "natural.Fall" + safeKill + "Kill", this.getMessage("death-messages.natural.Fall" + safeKill + "Kill"), this.getMobName("CREEPER"), this.getMobName("CREEPER"), "");
                    }
                }*/
            }
            else if (safeAlways.length() > 0) {
                deathmsg = this.formatV(e, death_tag = "natural.Fall" + safeAlways, this.getMessage("death-messages.natural.Fall" + safeAlways), "", "");
            } else if (mcVer(1_013) && m == Material.WATER) {
                deathmsg = this.formatV(e, death_tag = "natural.FallWater", this.getMessage("death-messages.natural.FallWater"), "", "");
            } else if (!mcVer(1_013) && (m == Material.WATER || materialSafeCheck(m, "STATIONARY_WATER"))) {
                deathmsg = this.formatV(e, death_tag = "natural.FallWater", this.getMessage("death-messages.natural.FallWater"), "", "");
            } else if (p.getWorld().getBlockAt(p.getLocation()).getType() == Material.FIRE) {
                deathmsg = this.formatV(e, death_tag = "natural.FallFire", this.getMessage("death-messages.natural.FallFire"), "", "");
            } else if (p.getLocation().getY() > 1 && p.getWorld().getBlockAt(p.getLocation().add(0, -1, 0)).getType() == Material.CACTUS) {
                deathmsg = this.formatV(e, death_tag = "natural.FallCacti", this.getMessage("death-messages.natural.FallCacti"), "", "");
            } else if (mcVer(1_014) && p.getLocation().getY() > 1 && (p.getWorld().getBlockAt(p.getLocation().add(0, -1, 0)).getType() == Material.SWEET_BERRY_BUSH || p.getWorld().getBlockAt(p.getLocation()).getType() == Material.SWEET_BERRY_BUSH)) {
                deathmsg = this.formatV(e, death_tag = "natural.FallCacti", this.getMessage("death-messages.natural.FallBerryBush"), "", "");
            } else if (p.getFallDistance() > 5.0f) {
                deathmsg = this.formatV(e, death_tag = "natural.FallLong", this.getMessage("death-messages.natural.FallLong"), "", "");
            } else {
                deathmsg = this.formatV(e, death_tag = "natural.FallShort", this.getMessage("death-messages.natural.FallShort"), "", "");
            }
        }
        else if (d == EntityDamageEvent.DamageCause.MAGIC) {
            if (damager != null) {
                ProjectileSource sht = null;
                if (damager instanceof ThrownPotion) {
                    sht = ((ThrownPotion)damager).getShooter();
                } else if (mcVer(1_009) && damager instanceof AreaEffectCloud) {
                    sht = ((AreaEffectCloud)damager).getSource();
                } else if (damager instanceof ProjectileSource) {
                    sht = (ProjectileSource)damager;
                }
                String reason = null;
                if (sht != null) {
                    if (sht instanceof Witch) {
                        final LivingEntity le2 = (LivingEntity)sht;
                        reason = getReasonForMob("Witch", le2);
                        deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages." + reason), getLEName(le2.getCustomName()), "");
                    } else if (mcVer(1_009) && sht instanceof EnderDragon && damager instanceof AreaEffectCloud) {
                        final LivingEntity le2 = (LivingEntity)sht;
                        reason = getReasonForMob("EnderDragonBreath", le2);
                        deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages." + reason), getLEName(le2.getCustomName()), "");
                    } else if (sht instanceof Player) {
                        final Player le3 = (Player)sht;
                        isPvP = true;
                        pvphm.put(pu, true);
                        this.lastkiller = le3;
                        Player source = le3;
                        reason = "pvp.PlayerPotion";
                        TextComponent result = handleCustomPlayerMessages(e, p, source, "Potion", reason, "", false);
                        if (result != null) {
                            deathmsg = result;
                            death_tag = reason;
                        } else {
                            deathmsg = this.formatV(e, source, death_tag = reason, this.getMessage("death-messages.pvp.PlayerPotion"), le3.getName(), le3.getDisplayName(), "");
                        }
                    } else {
                        reason = "natural.PotionHarming";
                        deathmsg = this.formatV(e, death_tag = "natural.PotionHarming", this.getMessage("death-messages.natural.PotionHarming"), "", "");
                    }
                    if (sht instanceof LivingEntity) {
                        damager = (LivingEntity)sht;
                        TextComponent result = handleCustomMessages(e, p, damager, isPvP, "Potion", (reason == null || reason.isEmpty()) ? "natural.PotionHarming" : reason, true);
                        if (result != null) {
                            deathmsg = result;
                            death_tag = reason;
                        }
                    }
                }
            }
            else {
                deathmsg = this.formatV(e, death_tag = "natural.PotionHarming", this.getMessage("death-messages.natural.PotionHarming"), "", "");
            }
        }
        else if (d == EntityDamageEvent.DamageCause.PROJECTILE) {
            Projectile pj = null;
            EntityType pjt = null;
            ProjectileSource sht2 = null;
            if (p.hasMetadata("dmp.lastDamageEnt2")) {
                Object obj = p.getMetadata("dmp.lastDamageEnt2").get(0).value();
                if (obj instanceof ProjectileSource) {
                    sht2 = (ProjectileSource) obj;
                }
                if (damager instanceof Projectile) {
                    pj = (Projectile)damager;
                    pjt = pj.getType();
                } else if (p.hasMetadata("dmp.lastDamageEntT")) {
                    pjt = (EntityType)p.getMetadata("dmp.lastDamageEntT").get(0).value();
                }
            }
            if (sht2 == null && damager != null) {
                if (damager instanceof Projectile) {
                    pj = (Projectile)damager;
                    pjt = pj.getType();
                    sht2 = pj.getShooter();
                }
                else if (p.hasMetadata("dmp.lastDamageEntT")) {
                    pjt = (EntityType)p.getMetadata("dmp.lastDamageEntT").get(0).value();
                    sht2 = (ProjectileSource)damager;
                }
            }
            if (this.pl.debug) {
                if (sht2 != null) {
                    this.broadcastDebug("Â§eProjectile shooter could be retrieved: " + sht2.toString(), p, w, false);
                }
                else {
                    this.broadcastDebug("Â§eProjectile shooter could not be retrieved", p, w, false);
                }
            }
            if (!mcVer(1_014) && pjt != null &&pjt.name().equalsIgnoreCase("TIPPED_ARROW")) {
                pjt = EntityType.ARROW;
            }
            String reason = "";
            if (pjt == null) {
                reason = "unknown";
                deathmsg = this.formatV(e, death_tag = "unknown", this.getMessage("death-messages.unknown"), "", "");
            }
            else {
                if (sht2 instanceof BlockProjectileSource) {
                    switch (pjt) {
                        case ARROW:
                        case SPECTRAL_ARROW: 
                            reason = "natural.DispenserArrow";
                            break;
                        case FIREBALL:
                        case SMALL_FIREBALL: 
                            reason = "natural.DispenserFireball";
                            break;
                        case ENDER_PEARL:
                        case EGG:
                        case SNOWBALL: 
                            reason = "natural.DispenserSnowball";
                            break;
                        default: 
                            reason = "unknown";
                    }
                    deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages." + reason), "", "");
                } else if (sht2 instanceof Player) {
                    final Player ply = (Player)sht2;
                    String wpn = "";
                    isPvP = true;
                    pvphm.put(pu, true);
                    this.lastkiller = ply;
                    boolean useCustomWeapon = false;
                    ItemStack is5 = null;
                    if (getItemInMainHand(ply.getInventory()) != null) {
                        String cn = null;
                        final ItemStack is = getItemInMainHand(ply.getInventory());
                        is5 = is;
                        if (is.getItemMeta() != null && !isEmptyDisplayName(is.getItemMeta().getDisplayName())) {
                            cn = is.getItemMeta().getDisplayName();
                        } else if (cn == null && this.fc.getBoolean("show-custom-death-msg-on-all-weapons", false)) {
                            if (is != null && is.getAmount() > 0 && is.getType() != Material.AIR)
                                cn = itemStackNameToString(is);
                        }
                        if (cn != null) {
                            wpn = cn;
                            useCustomWeapon = true;
                        }
                    }
                    Entity source = ply;
                    boolean noUseItem = false;
                    switch (pjt) {
                        case ARROW:
                        case SPECTRAL_ARROW: 
                            reason = "pvp.PlayerArrow";
                            break;
                        case TRIDENT: 
                            reason = "pvp.PlayerTrident";
                            break;
                        case FIREBALL:
                        case SMALL_FIREBALL: 
                            reason = "pvp.PlayerFireball";
                            noUseItem = true;
                            break;
                        case ENDER_PEARL:
                        case EGG:
                        case SNOWBALL: 
                            reason = "pvp.PlayerSnowball";
                            noUseItem = true;
                            break;
                        default:
                            reason = "unknown";
                    }
                    if (useCustomWeapon) {
                        String part = "Projectile";
                        if (mcVer(1_013) && pjt == EntityType.TRIDENT) {
                            part = "Trident";
                        }
                        death_tag = reason = "pvp.Player" + part + "Custom";
                        noUseItem = false;
                        //deathmsg = this.formatV(e, ply, death_tag = reason, this.getMessage("death-messages.pvp.Player" + part + "Custom"), ply.getName(), ply.getDisplayName(), wpn, getItemInMainHand(ply.getInventory()));
                    } else if (wpn.isEmpty() && is5 != null) {
                        wpn = itemStackNameToString(is5);
                    }
                    TextComponent result = handleCustomPlayerMessages(e, p, source, "Ranged", reason, "", false);
                    if (result != null) {
                        deathmsg = result;
                        death_tag = reason;
                    } else if (!noUseItem)
                        deathmsg = this.formatV(e, ply, death_tag = reason, this.getMessage("death-messages." + reason), ply.getName(), ply.getDisplayName(), wpn, is5);
                    else
                        deathmsg = this.formatV(e, ply, death_tag = reason, this.getMessage("death-messages." + reason), ply.getName(), ply.getDisplayName(), "");
                } else if (mcVer(1_009) && sht2 instanceof Shulker) {
                    final LivingEntity le4 = (LivingEntity)sht2;
                    switch (pjt) {
                        case SHULKER_BULLET:
                            reason = getReasonForMob("Shulker", le4);
                            break;
                        default: 
                            reason = "unknown";
                    }
                    deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages." + reason), getLEName(le4.getCustomName()), "");
                } else if (sht2 instanceof Wither) {
                    final LivingEntity le4 = (LivingEntity)sht2;
                    switch (pjt) {
                        case WITHER_SKULL: 
                            reason = getReasonForMob("Wither", le4);
                            break;
                        default:
                            reason = "unknown";
                    }
                    deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages." + reason), getLEName(le4.getCustomName()), "");
                } else if (sht2 instanceof Ghast) {
                    final LivingEntity le4 = (LivingEntity)sht2;
                    switch (pjt) {
                        case FIREBALL:
                        case SMALL_FIREBALL: 
                            reason = getReasonForMob("Ghast", le4);
                            break;
                        default:
                            reason = "unknown";
                    }
                    deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages." + reason), getLEName(le4.getCustomName()), "");
                } else if (sht2 instanceof Blaze) {
                    final LivingEntity le4 = (LivingEntity)sht2;
                    switch (pjt) {
                        case FIREBALL:
                        case SMALL_FIREBALL: 
                            reason = getReasonForMob("BlazeFireball", le4);
                            break;
                        default:
                            reason = "unknown";
                    }
                    deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages." + reason), getLEName(le4.getCustomName()), "");
                } else if (mcVer(1_011) && sht2 instanceof Llama) {
                    final LivingEntity le4 = (LivingEntity)sht2;
                    switch (pjt) {
                        case LLAMA_SPIT: 
                            reason = getReasonForMob("Llama", le4);
                            break;
                        default:
                            reason = "unknown";
                    }
                    deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages." + reason), getLEName(le4.getCustomName()), "");
                } else if (sht2 instanceof Snowman) {
                    final LivingEntity le4 = (LivingEntity)sht2;
                    switch (pjt) {
                        case SNOWBALL: 
                            reason = getReasonForMob("SnowGolem", le4);
                            break;
                        default:
                            reason = "unknown";
                    }
                    deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages." + reason), getLEName(le4.getCustomName()), "");
                } else if (mcVer(1_011) && sht2 instanceof Stray) {
                    final LivingEntity le4 = (LivingEntity)sht2;
                    switch (pjt) {
                        case ARROW:
                        case SPECTRAL_ARROW:
                            reason = getReasonForMob("StrayArrow", le4);
                            break;
                        default:
                            reason = "unknown";
                    }
                    deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages." + reason), getLEName(le4.getCustomName()), "");
                } else if (mcVer(1_012) && sht2 instanceof Illusioner) {
                    final LivingEntity le4 = (LivingEntity)sht2;
                    switch (pjt) {
                        case ARROW:
                        case SPECTRAL_ARROW:
                            reason = getReasonForMob("Illusioner", le4);
                            break;
                        default:
                            reason = "unknown";
                    }
                    deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages." + reason), getLEName(le4.getCustomName()), "");
                } else if (mcVer(1_013) && sht2 instanceof Drowned) {
                    final LivingEntity le4 = (LivingEntity)sht2;
                    switch (pjt) {
                        case TRIDENT: {
                            String customfooter = "";
                            ItemStack is2 = getItemInMainHand(le4.getEquipment());
                            String cn2 = "";
                            if (is2 == null || is2.getItemMeta() == null || isEmptyDisplayName(is2.getItemMeta().getDisplayName()))
                                cn2 = itemStackNameToString(is2);
                            else {
                                customfooter = "Custom";
                                cn2 = is2.getItemMeta().getDisplayName();
                            }
                            if (le4.getCustomName() == null || !passable(le4.getCustomName())) {
                                reason = "mob.DrownedTrident" + customfooter;
                                deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages.mob.DrownedTrident" + customfooter), "", cn2, is2);
                                break;
                            }
                            reason = getNamedMobSection() + ".DrownedTrident" + customfooter;
                            deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages." + getNamedMobSection() + ".DrownedTrident" + customfooter), le4.getCustomName(), cn2, is2);
                            break;
                        }
                        default: {
                            reason = "unknown";
                            deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages.unknown"), "", "");
                            break;
                        }
                    }
                } else if (sht2 instanceof Skeleton) {
                    final LivingEntity le4 = (LivingEntity)sht2;
                    switch (pjt) {
                        case ARROW:
                        case SPECTRAL_ARROW: {
                            String customfooter = "";
                            ItemStack is2 = getItemInMainHand(le4.getEquipment());
                            String cn2 = "";
                            if (!this.fc.getBoolean("show-custom-death-msg-on-all-weapons", false) && (is2 == null || is2.getItemMeta() == null || isEmptyDisplayName(is2.getItemMeta().getDisplayName()))) {
                                cn2 = itemStackNameToString(is2);
                            } else {
                                customfooter = "Custom";
                                cn2 = is2.getItemMeta().getDisplayName();
                            }
                            if (le4.getCustomName() == null || !passable(le4.getCustomName())) {
                                reason = "mob.SkeletonArrow" + customfooter;
                                deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages.mob.SkeletonArrow" + customfooter), "", cn2, is2);
                                break;
                            }
                            reason = getNamedMobSection() + ".SkeletonArrow" + customfooter;
                            deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages." + getNamedMobSection() + ".SkeletonArrow" + customfooter), le4.getCustomName(), cn2, is2);
                            break;
                        }
                        default: {
                            reason = "unknown";
                            deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages.unknown"), "", "");
                            break;
                        }
                    }
                } else if (mcVer(1_014) && sht2 instanceof Pillager) {
                    final LivingEntity le4 = (LivingEntity)sht2;
                    switch (pjt) {
                        case ARROW:
                        case SPECTRAL_ARROW: {
                            String customfooter = "";
                            ItemStack is2 = getItemInMainHand(le4.getEquipment());
                            String cn2 = "";
                            if (!this.fc.getBoolean("show-custom-death-msg-on-all-weapons", false) && (is2 == null || is2.getItemMeta() == null || isEmptyDisplayName(is2.getItemMeta().getDisplayName()))) {
                                cn2 = itemStackNameToString(is2);
                            } else {
                                customfooter = "Custom";
                                cn2 = is2.getItemMeta().getDisplayName();
                            }
                            if (le4.getCustomName() == null || !passable(le4.getCustomName())) {
                                reason = "mob.PillagerArrow" + customfooter;
                                deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages.mob.PillagerArrow" + customfooter), "", cn2, is2);
                                break;
                            }
                            reason = getNamedMobSection() + ".PillagerArrow" + customfooter;
                            deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages." + getNamedMobSection() + ".PillagerArrow" + customfooter), le4.getCustomName(), cn2, is2);
                            break;
                        }
                        default: {
                            reason = "unknown";
                            deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages.unknown"), "", "");
                            break;
                        }
                    }
                }
            }
            if (deathmsg == null) {
                if (pjt == EntityType.ARROW || pjt == EntityType.SPECTRAL_ARROW) {
                    reason = "natural.UnknownArrow";
                    deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages.natural.UnknownArrow"), "", "");
                } else {
                    reason = "unknown";
                    deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages.unknown"), "", "");  
                }
            }
            if (damager instanceof LivingEntity) {
                TextComponent result = handleCustomMessages(e, p, damager, isPvP, "Ranged", reason, true);
                if (result != null) {
                    deathmsg = result;
                    death_tag = reason;
                }
            }
        }
        else if (d == EntityDamageEvent.DamageCause.ENTITY_ATTACK || (mcVer(1_009) && d == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            LivingEntity le = null;
            boolean fallback = true;
            boolean fangs = false;
            if (mcVer(1_011) && damager instanceof EvokerFangs) {
                damager = ((EvokerFangs)damager).getOwner();
                fangs = true;
            }
            if (damager instanceof LivingEntity) {
                le = (LivingEntity)damager;
            }
            String reason = "";
            if (mcVer(1_016) && damager instanceof WitherSkull)
            {
                WitherSkull ws = (WitherSkull)damager;
                ProjectileSource ps = ws.getShooter();
                if (ps instanceof Player) {
                    Player source = (Player)ps;
                    reason = "natural.WitherSkull";
                    TextComponent result = handleCustomPlayerMessages(e, p, source, "Ranged", reason, "", false);
                    if (result != null) {
                        deathmsg = result;
                        death_tag = reason;
                    } else
                        deathmsg = this.formatV(e, source, death_tag = reason, this.getMessage("death-messages.natural.WitherSkull"), source.getName(), source.getDisplayName(), "");
                } else if (ps instanceof Wither || ps == null) {
                    reason = "natural.WitherSkull";
                    deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages.natural.WitherSkull"), "", "");
                } else if (ps instanceof BlockProjectileSource) {
                    reason = "natural.TNT";
                    deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages.natural.TNT"), "", "");
                } else {
                    deathmsg = this.formatV(e, death_tag = "unknown", this.getMessage("death-messages.unknown"), "", "");
                }
            }
            if (mcVer(1_009) && damager instanceof AreaEffectCloud) {
                AreaEffectCloud aec = (AreaEffectCloud)damager;
                ProjectileSource ps = aec.getSource();
                fallback = false;
                if (ps instanceof Player) {
                    Player source = (Player)ps;
                    reason = "pvp.PlayerPotion";
                    TextComponent result = handleCustomPlayerMessages(e, p, source, "Potion", reason, "", false);
                    if (result != null) {
                        deathmsg = result;
                        death_tag = reason;
                    } else
                        deathmsg = this.formatV(e, source, death_tag = reason, this.getMessage("death-messages.pvp.PlayerPotion"), source.getName(), source.getDisplayName(), "");
                } else if (ps instanceof EnderDragon || ps == null) {
                    reason = "mob.EnderDragonBreath";
                    if (ps != null) {
                        EnderDragon le6 = ((EnderDragon)ps);
                        if (le6.getCustomName() != null && passable(le6.getCustomName())) {
                            reason = getNamedMobSection() + ".EnderDragonBreath";
                            deathmsg = this.formatV(e, death_tag = getNamedMobSection() + ".EnderDragonBreath", this.getMessage("death-messages." + getNamedMobSection() + ".EnderDragonBreath"), le6.getCustomName(), "");
                        }
                        else
                            deathmsg = this.formatV(e, death_tag = "mob.EnderDragonBreath", this.getMessage("death-messages.mob.EnderDragonBreath"), "", "");
                    }
                    else
                        deathmsg = this.formatV(e, death_tag = "mob.EnderDragonBreath", this.getMessage("death-messages.mob.EnderDragonBreath"), "", "");
                } else if (ps instanceof BlockProjectileSource) {
                    reason = "natural.PotionHarming";
                    deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages.natural.PotionHarming"), "", "");
                } else {
                    deathmsg = this.formatV(e, death_tag = "unknown", this.getMessage("death-messages.unknown"), "", "");
                }
            }
            boolean is_donkey = false;
            boolean is_shorse = false;
            boolean is_mule = false;
            boolean is_zhorse = false;
            boolean is_horse = false;
            if (!mcVer(1_011) && damager instanceof Horse) {
                @SuppressWarnings("deprecation")
                boolean is_donkey_ = ((Horse)damager).getVariant() == org.bukkit.entity.Horse.Variant.DONKEY;
                is_donkey = is_donkey_;
                @SuppressWarnings("deprecation")
                boolean is_shorse_ = ((Horse)damager).getVariant() == org.bukkit.entity.Horse.Variant.SKELETON_HORSE;
                is_shorse = is_shorse_;
                @SuppressWarnings("deprecation")
                boolean is_mule_ = ((Horse)damager).getVariant() == org.bukkit.entity.Horse.Variant.MULE;
                is_mule = is_mule_;
                @SuppressWarnings("deprecation")
                boolean is_zhorse_ = ((Horse)damager).getVariant() == org.bukkit.entity.Horse.Variant.UNDEAD_HORSE;
                is_zhorse = is_zhorse_;
                @SuppressWarnings("deprecation")
                boolean is_horse_ = ((Horse)damager).getVariant() == org.bukkit.entity.Horse.Variant.HORSE;
                is_horse = is_horse_;
            }
            if (damager instanceof Bat) {
                reason = getReasonForMob("Bat", le);
            } else if (mcVer(1_015) && damager instanceof Bee) {
                reason = getReasonForMob("Bee", le);
            } else if (damager instanceof Blaze) {
                reason = getReasonForMob("BlazeMelee", le);
            } else if (mcVer(1_014) && damager instanceof Cat) {
                reason = getReasonForMob("Cat", le);
            } else if (damager instanceof CaveSpider) {
                reason = getReasonForMob("CaveSpider", le);
            } else if (damager instanceof Chicken) {
                reason = getReasonForMob("Chicken", le);
            } else if (mcVer(1_013) && damager instanceof Cod) {
                reason = getReasonForMob("Cod", le);
            } else if (damager instanceof MushroomCow) {
                reason = getReasonForMob("Mooshroom", le);
            } else if (damager instanceof Cow) {
                reason = getReasonForMob("Cow", le);
            } else if (mcVer(1_013) && damager instanceof Dolphin) {
                reason = getReasonForMob("Dolphin", le);
            } else if ((mcVer(1_011) && damager instanceof Donkey) || (damager instanceof Horse && is_donkey)) {
                reason = getReasonForMob("Donkey", le);
            } else if (damager instanceof EnderDragon) {
                reason = getReasonForMob("EnderDragon", le);
            } else if (damager instanceof Enderman) {
                reason = getReasonForMob("Enderman", le);
            } else if (mcVer(1_008) && damager instanceof Endermite) {
                reason = getReasonForMob("Endermite", le);
            } else if (mcVer(1_011) && damager instanceof Evoker) {
                if (fangs)
                    reason = "natural.EvokerFang";
                else
                    reason = getReasonForMob("Evoker", le);
            } else if (mcVer(1_014) && damager instanceof Fox) {
                reason = getReasonForMob("Fox", le);
            } else if (damager instanceof Ghast) {
                reason = getReasonForMob("Ghast", le);
            } else if (damager instanceof Giant) {
                reason = getReasonForMob("Giant", le);
            } else if (mcVer(1_008) && damager instanceof Guardian) {
                final String el;
                if (mcVer(1_011)) {
                    el = damager instanceof ElderGuardian ? "Elder" : "";
                } else {
                    @SuppressWarnings("deprecation")
                    boolean isElder = ((Guardian)damager).isElder();
                    el = (isElder) ? "Elder" : "";
                }
                reason = getReasonForMob(el + "Guardian", le);
            } else if (mcVer(1_016) && damager instanceof Hoglin) {
                reason = getReasonForMob("Hoglin", le);
            } else if (mcVer(1_012) && damager instanceof Illusioner) {
                reason = getReasonForMob("Illusioner", le);
            } else if (damager instanceof IronGolem) {
                reason = getReasonForMob("IronGolem", le);
            } else if (mcVer(1_011) && damager instanceof Llama) {
                reason = getReasonForMob("Llama", le);
            } else if (damager instanceof MagmaCube) {
                reason = getReasonForMob("MagmaCube", le);
            } else if ((mcVer(1_011) && damager instanceof Mule) || (damager instanceof Horse && is_mule)) {
                reason = getReasonForMob("Mule", le);
            } else if (mcVer(1_014) && damager instanceof Panda) {
                reason = getReasonForMob("Panda", le);
            } else if (mcVer(1_012) && damager instanceof Parrot) {
                reason = getReasonForMob("Parrot", le);
            } else if (mcVer(1_013) && damager instanceof Phantom) {
                reason = getReasonForMob("Phantom", le);
            } else if (damager instanceof Pig) {
                reason = getReasonForMob("Pig", le);
            } else if (mcVer(1_010) && damager instanceof PolarBear) {
                reason = getReasonForMob("PolarBear", le);
            } else if (mcVer(1_013) && damager instanceof PufferFish) {
                reason = getReasonForMob("PufferFish", le);
            } else if (mcVer(1_008) && damager instanceof Rabbit) {
                reason = getReasonForMob("Rabbit", le);
            } else if (mcVer(1_014) && damager instanceof Ravager) {
                reason = getReasonForMob("Ravager", le);
            } else if (mcVer(1_013) && damager instanceof Salmon) {
                reason = getReasonForMob("Salmon", le);
            } else if (damager instanceof Sheep) {
                reason = getReasonForMob("Sheep", le);
            } else if (mcVer(1_009) && damager instanceof Shulker) {
                reason = getReasonForMob("Shulker", le);
            } else if (damager instanceof Silverfish) {
                reason = getReasonForMob("Silverfish", le);
            } else if ((mcVer(1_011) && damager instanceof SkeletonHorse) || (damager instanceof Horse && is_shorse)) {
                reason = getReasonForMob("SkeletonHorse", le);
            } else if (damager instanceof Slime && !(damager instanceof MagmaCube)) {
                reason = getReasonForMob("Slime", le);
            } else if (damager instanceof Spider && !(damager instanceof CaveSpider)) {
                reason = getReasonForMob("Spider", le);
            } else if (damager instanceof Squid) {
                reason = getReasonForMob("Squid", le);
            } else if (mcVer(1_016) && damager instanceof Strider) {
                reason = getReasonForMob("Strider", le);
            } else if (mcVer(1_013) && damager instanceof TropicalFish) {
                reason = getReasonForMob("TropicalFish", le);
            } else if (mcVer(1_013) && damager instanceof Turtle) {
                reason = getReasonForMob("Turtle", le);
            } else if (mcVer(1_011) && damager instanceof Vex) {
                reason = getReasonForMob("Vex", le);
            } else if (mcVer(1_011) && damager instanceof Vindicator) {
                reason = getReasonForMob("Vindicator", le);
            } else if (damager instanceof Wither) {
                reason = getReasonForMob("Wither", le);
            } else if (damager instanceof Wolf) {
                reason = getReasonForMob("Wolf", le);
            } else if ((mcVer(1_011) && damager instanceof ZombieHorse) || (damager instanceof Horse && is_zhorse)) {
                reason = getReasonForMob("ZombieHorse", le);
            } else if (damager instanceof Horse && ((mcVer(1_011) && damager.getType() == EntityType.HORSE) || is_horse)) {
                reason = getReasonForMob("Horse", le);
            } else if (damager instanceof Villager) {
                reason = getReasonForMob("Villager", le);
            } else if (mcVer(1_016) && damager instanceof Zoglin) {
                reason = getReasonForMob("Zoglin", le);
            }
            
            String add = "Melee";
            String wpn2 = "";
            ItemStack is3 = null;
            EntityEquipment ee = null;
            boolean ok_zombie = false;
            if (mcVer(1_011)) {
                ok_zombie = damager instanceof ZombieVillager || damager instanceof Husk;
            }
            if (damager instanceof LivingEntity) {
                ee = ((LivingEntity)damager).getEquipment();
                if (ee != null && getItemInMainHand(ee) != null) {
                    String cn = null;
                    final ItemStack is = getItemInMainHand(ee);
                    if (is.getItemMeta() != null && !isEmptyDisplayName(is.getItemMeta().getDisplayName())) {
                        cn = is.getItemMeta().getDisplayName();
                    } else if (cn == null && this.fc.getBoolean("show-custom-death-msg-on-all-weapons", false)) {
                        if (is != null && is.getAmount() > 0 && is.getType() != Material.AIR)
                            cn = itemStackNameToString(is);
                    }
                    is3 = is;
                    if (cn != null) {
                        add = "Custom";
                        wpn2 = cn;
                    }
                }
            }
            if (fallback && reason != null && !reason.isEmpty()) {
                deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages." + reason), getLEName(le.getCustomName()), wpn2, is3);
                //reason = "";
            }
            
            if (mcVer(1_013) && damager instanceof Drowned) {
                reason = getReasonForMob("Drowned" + add, le);
            } else if (!mcVer(1_016) && damager instanceof PigZombie) {
                reason = getReasonForMob("ZombiePigMan" + add, le);
            } else if (mcVer(1_016) && damager instanceof PigZombie) {
                reason = getReasonForMob("ZombifiedPiglin" + add, le);
            } else if (mcVer(1_016) && hasPiglinBrute() && damager instanceof PiglinBrute) {
                reason = getReasonForMob("PiglinBrute" + add, le);
            } else if (mcVer(1_016) && damager instanceof Piglin) {
                reason = getReasonForMob("Piglin" + add, le);
            } else if (mcVer(1_014) && damager instanceof Pillager) {
                reason = getReasonForMob("Pillager" + add, le);
            } else if (damager instanceof Zombie || ok_zombie) {
                String wth = "Zombie";
                if (mcVer(1_011)) {
                    if (damager instanceof ZombieVillager) {
                        wth = "ZombieVillager";
                    } else if (damager instanceof Husk) {
                        wth = "Husk";
                    }
                } else {
                    @SuppressWarnings("deprecation")
                    boolean z_v = ((Zombie)damager).isVillager();
                    if (z_v) {
                        wth = "ZombieVillager";
                    } else if (mcVer(1_010) && isHusk(damager)) {
                        wth = "Husk";
                    }
                }
                reason = getReasonForMob(wth + add, le);
            } else if (damager instanceof Skeleton) {
                String wth = "Skeleton";
                if (mcVer(1_011)) {
                    if (damager instanceof WitherSkeleton) {
                        wth = "WitherSkeleton";
                    } else if (damager instanceof Stray) {
                        wth = "Stray";
                    }
                } else {
                    @SuppressWarnings("deprecation")
                    Skeleton.SkeletonType skt = ((Skeleton)damager).getSkeletonType();
                    @SuppressWarnings("deprecation")
                    Skeleton.SkeletonType skt_w = Skeleton.SkeletonType.WITHER;
                    if (skt == skt_w) {
                        wth = "WitherSkeleton";
                    } else if (mcVer(1_010)) {
                        @SuppressWarnings("deprecation")
                        Skeleton.SkeletonType skt_s = Skeleton.SkeletonType.STRAY;
                        if (skt == skt_s)
                            wth = "Stray";
                    }
                }
                reason = getReasonForMob(wth + add, le);
            }
            if (fallback && reason != null && !reason.isEmpty()) {
                deathmsg = this.formatV(e, death_tag = reason, this.getMessage("death-messages." + reason), getLEName(le.getCustomName()), wpn2, is3);
                //reason = "";
            }
            
            if (damager instanceof Player) {
                final Player ply2 = (Player)damager;
                isPvP = true;
                pvphm.put(pu, true);
                this.lastkiller = ply2;
                if (getItemInMainHand(ply2.getInventory()) != null) {
                    String cn = null;
                    final ItemStack is = getItemInMainHand(ply2.getInventory());
                    if (is.getItemMeta() != null && !isEmptyDisplayName(is.getItemMeta().getDisplayName())) {
                        cn = is.getItemMeta().getDisplayName();
                    } else if (cn == null && this.fc.getBoolean("show-custom-death-msg-on-all-weapons", false)) {
                        if (is != null && is.getAmount() > 0 && is.getType() != Material.AIR)
                            cn = itemStackNameToString(is);
                    }
                    is3 = is;
                    if (cn != null) {
                        add = "Custom";
                        wpn2 = cn;
                    }
                }
                reason = "pvp.Player" + add;
                deathmsg = this.formatV(e, le, death_tag = reason, this.getMessage("death-messages.pvp.Player" + add), ((Player)le).getName(), ((Player)le).getDisplayName(), wpn2, is3);
            }
            
            if (damager != null && damager instanceof LivingEntity && !reason.isEmpty()) {
                TextComponent result = handleCustomMessages(e, p, damager, isPvP, "Melee", reason, true);
                if (result != null) {
                    deathmsg = result;
                    death_tag = reason;
                }
            }
        }
        else {
            deathmsg = this.formatV(e, death_tag = "unknown", this.getMessage("death-messages.unknown"), "", "");
        }
        
        
        

        // #####################################
        
        // end scanning for damage causes here

        // #####################################
        
        
        
        
        // remove damage info
        if (p.hasMetadata("dmp.lastDamageEntT")) {
            p.removeMetadata("dmp.lastDamageEntT", (Plugin)this.pl);
        }
        if (p.hasMetadata("dmp.lastDamageEnt2")) {
            p.removeMetadata("dmp.lastDamageEnt2", (Plugin)this.pl);
        }
        if (p.hasMetadata("dmp.lastDamageEnt")) {
            p.removeMetadata("dmp.lastDamageEnt", (Plugin)this.pl);
        }
        if (p.hasMetadata("dmp.lastDamageEx")) {
            p.removeMetadata("dmp.lastDamageEx", (Plugin)this.pl);
        }
        if (p.hasMetadata("dmp.lastDamage")) {
            p.removeMetadata("dmp.lastDamage", (Plugin)this.pl);
        }
        if (p.hasMetadata("dmp.lastCause")) {
            p.removeMetadata("dmp.lastCause", (Plugin)this.pl);
        }
        if (deathmsg == null) {
            deathmsg = this.formatV(e, "unknown", this.getMessage("death-messages.unknown"), "", "");
        }
        
        // custom message event
        DeathMessageCustomEvent br = new DeathMessageCustomEvent(dmid, p, isPvP);
        br.setTag(death_tag.trim());
        br.setKiller(last_killer_name);
        br.setKiller2(last_killer_name2);
        br.setWeapon(last_weapon);
        last_killer_name = last_killer_name2 = null;
        last_weapon = null;
        pl.getServer().getPluginManager().callEvent(br);
        if (br.isCancelled()) {
            return;
        }
        isPvP = br.isPVP();
        if (br.getTag() != death_tag.trim() && br.getTag() != null) {
            String tag = br.getTag();
            if (this.fc.contains("death-messages." + tag)) {
                String msg = this.getMessage("death-messages." + tag);
                String wn = "";
                if (br.getWeapon() != null) {
                    wn = br.getWeapon().getItemMeta().getDisplayName();
                }
                deathmsg = this.formatV(e, tag, msg, br.getKiller(), br.getKiller2(), wn, br.getWeapon());
            }
        }
        TextComponent fdeathmsg = new TextComponent("");
        try {
            if (deathmsg.getExtra() == null) {
                return;
            } 
            if (deathmsg.getExtra().size() < 1) {
                return;
            } 
            if (deathmsg.getExtra().size() == 1 && deathmsg.getExtra().get(0).getInsertion().length() < 1) {
                return;
            } 
        } catch (Exception ex) {
        }
        fdeathmsg.addExtra(deathmsg);
        if (this.fc.getBoolean("death-message-compat-mode")) {
            dmc.put(pu, fdeathmsg.toLegacyText());
            e.setDeathMessage(fdeathmsg.toLegacyText());
        }
        
        // add to queue
        this.queueBroadcastDeath(fdeathmsg, p, p.getWorld(), isPvP, vdeathmsg, curPrefixLen, curSuffixLen);
    }

    private String getNamedMobSection() {
        if (!this.fc.getBoolean("death-message-enable-namedmob", true)) {
            return "mob";
        }
        return "namedmob";
    }

    private boolean isEmptyDisplayName(String displayName) {
        return displayName == null || displayName.isEmpty();
    }

    @SuppressWarnings("deprecation")
    private boolean isAnvil(FallingBlock fb) {
        if (mcVer(1_013)) {
            return fb.getBlockData().getMaterial() == Material.ANVIL;
        } else {
            return fb.getMaterial() == Material.ANVIL;
        }
    }

    private Entity resolveDamager(Player p, Entity damager) {
        Entity nDamager = resolveDamagerRaw(damager);
        if (nDamager != damager && nDamager != null)
            p.setMetadata("dmp.lastDamage", (MetadataValue)new FixedMetadataValue((Plugin)this.pl, "[mob]" + nDamager.getType().toString()));
        return damager;
    }

    private Entity resolveDamagerRaw(Entity damager) {
        if (damager == null) return null;
        if (damager instanceof ThrownPotion) {
            ProjectileSource sht = ((ThrownPotion)damager).getShooter();
            if (sht instanceof Entity) {
                return (Entity)sht;
            } else {
                return null;
            }
        }
        else if (mcVer(1_009) && damager instanceof AreaEffectCloud) {
            ProjectileSource sht = ((AreaEffectCloud)damager).getSource();
            if (sht instanceof Entity) {
                return (Entity)sht;
            } else {
                return null;
            }
        }
        else if (damager instanceof ProjectileSource) {
            ProjectileSource sht = (ProjectileSource)damager;
            if (sht instanceof Entity) {
                return (Entity)sht;
            } else {
                return null;
            }
        }
        return damager;
    }
    
    private String itemStackNameToString(ItemStack is) {
        if (is == null) return "???";
        String m = is.getType().toString();
        ConfigurationSection cs = this.fc.getConfigurationSection("item-names");
        if (cs != null) {
            try {
                final Object o = cs.getString(m);
                this.assertNotNull(o);
                return o.toString();
            }
            catch (Exception ex) {}
            catch (AssertionError assertionError) {}
        }
        return getStandardName(m);
    }

    private String getStandardName(String m) {
        return capitalize(m.replace('_', ' ').toLowerCase());
    }

    @SuppressWarnings("deprecation")
    private ItemStack getItemInMainHand(EntityEquipment ee) {
        try {
            return ee.getItemInMainHand();
        } catch (Throwable ex) {
            try {
                return ee.getItemInHand();
            } catch (Throwable t) {
                return null;
            }
        }
    }

    @SuppressWarnings("deprecation")
    private ItemStack getItemInMainHand(PlayerInventory ee) {
        try {
            return ee.getItemInMainHand();
        } catch (Throwable ex) {
            try {
                return ee.getItemInHand();
            } catch (Throwable t) {
                return null;
            }
        }
    }
    
    private boolean isTrapdoor(Material m) {
        return m == Material.OAK_TRAPDOOR || m == Material.BIRCH_TRAPDOOR || m == Material.SPRUCE_TRAPDOOR
                || m == Material.ACACIA_TRAPDOOR || m == Material.DARK_OAK_TRAPDOOR || m == Material.JUNGLE_TRAPDOOR
                || m == Material.IRON_TRAPDOOR;
    }

    private boolean isHusk(Entity damager) {
        if (mcVer(1_011)) {
            return damager instanceof Husk;
        }
        if (!mcVer(1_010)) return false;
        if (damager instanceof Zombie) {
            try {
                Class<?> craftEntityClazz = ReflectionUtil.getOBCClass("entity.CraftEntity");
                Object obce = craftEntityClazz.cast(damager);
                Class<?> nmsEntityClazz = ReflectionUtil.getNMSClass("Entity");
                Class<?> nbtTagCompoundClazz = ReflectionUtil.getNMSClass("NBTTagCompound");
                Object nmstag;
                nmstag = nbtTagCompoundClazz.getConstructor().newInstance();
                Method craftEntityGetHandle = ReflectionUtil.getMethod(craftEntityClazz, "getHandle");
                Object nmse = craftEntityGetHandle.invoke(obce);
                Method c = null;
                try {
                    c = ReflectionUtil.getMethod(nmsEntityClazz, "c", nbtTagCompoundClazz);
                    c.invoke(nmse, nmstag);
                } catch (Exception ex) {
                    try {
                        c = ReflectionUtil.getMethod(nmsEntityClazz, "save", nbtTagCompoundClazz);
                        c.invoke(nmse, nmstag);
                    } catch (Exception ex2) {}
                }
                if (c == null) return false;
                c.invoke(nmse, nmstag);
                Method getIntMethod = ReflectionUtil.getMethod(nbtTagCompoundClazz, "getInt", String.class);
                int i = (Integer)getIntMethod.invoke(nmstag, "ZombieType");
                return (i == 6);
            } catch (ClassNotFoundException e) {
            } catch (NoSuchMethodException | SecurityException e) {
            } catch (InstantiationException e) {
            } catch (IllegalAccessException e) {
            } catch (IllegalArgumentException e) {
            } catch (InvocationTargetException e) {
            }
            return false;
        }
        return false;
    }

    // should we display custom mob name? (false if it has heart characters)
    private boolean passable(String customName) {
        if (pl.config.getBoolean("heart-compat-mode",false)) {
            char[] hearts = pl.config.getString("heart-characters","â™¡â™¥â�¤â– ").toCharArray();
            for (char arg0: hearts)
                if (customName.indexOf(arg0)>=0) 
                    return false;
        }
        return true;
    }

    // add to message queue
    private void queueBroadcastDeath(final TextComponent deathmsg, final Player plr, final World world, final boolean isPvP, final String vdeathmsg, final int prefixlen, final int suffixlen) {
        dhistory.put(plr.getUniqueId(), TextComponent.toLegacyText(deathmsg));
        this.queue.add(new DeathMessage(deathmsg, plr, world, isPvP, vdeathmsg, prefixlen, suffixlen));
    }
    
    // empty message queue
    private void flushBroadcastDeath() {
        flushQueue.lock(); 
        ArrayList<DeathMessage> dmq = new ArrayList<DeathMessage>(this.queue);
        this.queue.clear();
        flushQueue.unlock();
        Iterator<DeathMessage> dmqi = dmq.iterator();
        if (this.fc.getBoolean("death-message-compat-mode")) {
            while (dmqi.hasNext()) {
                final DeathMessage d = dmqi.next();
                if (dmc.containsKey(d.v.getUniqueId())) 
                    this.broadcastDeath(new TextComponent(TextComponent.fromLegacyText(dmc.get(d.v.getUniqueId()))), d.v, d.w, d.pvp, d.vd, d.prel, d.sufl);
                else
                    this.broadcastDeath(d.d, d.v, d.w, d.pvp, d.vd, d.prel, d.sufl);
                dmqi.remove();
            }
        } else {
            while (dmqi.hasNext()) {
                final DeathMessage d = dmqi.next();
                this.broadcastDeath(d.d, d.v, d.w, d.pvp, d.vd, d.prel, d.sufl);
                dmqi.remove();
            }
        }
    }
    
    Entity getEntityByUUID(final World w, final String asString) {
        final UUID id = UUID.fromString(asString);
        for (final Entity e : w.getEntities()) {
            if (e.getUniqueId().equals(id)) {
                return e;
            }
        }
        return null;
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerWorldChange(final PlayerChangedWorldEvent e) {
        final Player p = e.getPlayer();
        if (p.hasMetadata("dmp.lastDamageEnt2")) {
            p.removeMetadata("dmp.lastDamageEnt2", (Plugin)this.pl);
        }
        if (p.hasMetadata("dmp.lastDamageEnt")) {
            p.removeMetadata("dmp.lastDamageEnt", (Plugin)this.pl);
        }
        if (p.hasMetadata("dmp.lastDamageEx")) {
            p.removeMetadata("dmp.lastDamageEx", (Plugin)this.pl);
        }
        if (p.hasMetadata("dmp.lastDamage")) {
            p.removeMetadata("dmp.lastDamage", (Plugin)this.pl);
        }
    }
    
    String getKillerName2(final Entity damager, final Player p) {
        if (damager instanceof Player) {
            return ((Player)damager).getDisplayName();
        }
        return this.getKillerName(damager, p);
    }
    
    String getKillerName(final Entity damager, final Player p) {
        if (damager instanceof Player) {
            return ((Player)damager).getName();
        }
        if (damager == null) {
            return "";
        }
        if (p == null) {
            return getKillerName(damager);
        }
        if (!p.hasMetadata("dmp.lastDamage")) {
            return "";
        }
        
        String x = p.getMetadata("dmp.lastDamage").get(0).asString();
        if (x.startsWith("[mob]")) {
            if (x.equalsIgnoreCase("[mob]player")) {
                try {
                    return ((Player)damager).getName();
                }
                catch (ClassCastException cce) {
                    if (damager instanceof Projectile) {
                        final ProjectileSource ps = ((Projectile)damager).getShooter();
                        if (ps instanceof Player) {
                            return ((Player)ps).getName();
                        }
                        return "?";
                    }
                }
                catch (Exception e) {
                    return "?";
                }
            }
            if (damager instanceof LivingEntity) {
                final LivingEntity le = (LivingEntity)damager;
                if (le.getCustomName() != null && passable(le.getCustomName())) {
                    return le.getCustomName();
                }
            }
            x = x.substring(5);
            if (!mcVer(1_011)) {
                if (damager.getType() == EntityType.SKELETON) {
                    @SuppressWarnings("deprecation")
                    Skeleton.SkeletonType skt = ((Skeleton)damager).getSkeletonType();
                    @SuppressWarnings("deprecation")
                    Skeleton.SkeletonType skt_w = Skeleton.SkeletonType.WITHER;
                    if (skt == skt_w)
                        x = "WITHER_SKELETON";
                    else if (mcVer(1_010)) {
                        @SuppressWarnings("deprecation")
                        Skeleton.SkeletonType skt_s = Skeleton.SkeletonType.STRAY;
                        if (skt == skt_s)
                            x = "STRAY";
                    }
                } else if (damager.getType() == EntityType.ZOMBIE) {
                    @SuppressWarnings("deprecation")
                    boolean z_v = ((Zombie)damager).isVillager();
                    if (z_v)
                        x = "ZOMBIE_VILLAGER";
                    else if (isHusk(damager))
                        x = "HUSK";
                }
            }
            x = this.mobNameConfigurate(x);
            try {
                final Object o = this.fc.getString("mob-names." + x);
                this.assertNotNull(o);
                x = o.toString();
            }
            catch (Exception ex) {}
            catch (AssertionError assertionError) {}
        }
        return ChatColor.translateAlternateColorCodes('&', x);
    }
    
    String getMobName(String mobType) {
        String x = "";
        x = this.mobNameConfigurate(mobType);
        try {
            final Object o = this.fc.getString("mob-names." + x);
            this.assertNotNull(o);
            x = o.toString();
        }
        catch (Exception ex) {}
        catch (AssertionError assertionError) {}
        return ChatColor.translateAlternateColorCodes('&', x);
    }
    
    String getCheckName(final Entity damager, final Player p, final boolean display) {
        if (damager instanceof Player) {
            if (display) {
                return ((Player)damager).getDisplayName();
            } else {
                return ((Player)damager).getName();
            }
        }
        if (damager == null) {
            return "";
        }
        if (!p.hasMetadata("dmp.lastDamage")) {
            return "";
        }
        String x = p.getMetadata("dmp.lastDamage").get(0).asString();
        if (x.startsWith("[mob]")) {
            if (x.equalsIgnoreCase("[mob]player")) {
                try {
                    return ((Player)damager).getName();
                }
                catch (ClassCastException cce) {
                    if (damager instanceof Projectile) {
                        final ProjectileSource ps = ((Projectile)damager).getShooter();
                        if (display) {
                            return ((Player)ps).getDisplayName();
                        } else {
                            return ((Player)ps).getName();
                        }
                    }
                }
                catch (Exception e) {
                    return "";
                }
            }
            if (damager instanceof LivingEntity) {
                final LivingEntity le = (LivingEntity)damager;
                if (le.getCustomName() != null && passable(le.getCustomName())) {
                    return le.getCustomName();
                }
            }
            if (damager instanceof Projectile) {
                final ProjectileSource ps = ((Projectile)damager).getShooter();
                if (ps instanceof LivingEntity) {
                    final LivingEntity le = (LivingEntity)ps;
                    if (le.getCustomName() != null && passable(le.getCustomName())) {
                        return le.getCustomName();
                    }
                }
            }
        }
        return "";
    }
    
    String getKillerName2(final Entity damager) {
        if (damager == null) {
            return "";
        }
        final String x = damager.getType().toString();
        if (x.equalsIgnoreCase("player")) {
            return ((Player)damager).getDisplayName();
        }
        return this.getKillerName(damager);
    }

    @SuppressWarnings("deprecation")
    String getKillerName(final Entity damager) {
        if (damager == null) {
            return "";
        }
        String x = damager.getType().toString();
        if (x.equalsIgnoreCase("player")) {
            return ((Player)damager).getName();
        }
        if (damager instanceof LivingEntity) {
            final LivingEntity le = (LivingEntity)damager;
            if (le.getCustomName() != null && passable(le.getCustomName())) {
                return le.getCustomName();
            }
        }
        if (!mcVer(1_011)) {
            if ((damager instanceof Skeleton) && ((Skeleton)damager).getSkeletonType() == Skeleton.SkeletonType.WITHER)
                x = "WITHER_SKELETON";
            else if (mcVer(1_010) && (damager instanceof Skeleton) && ((Skeleton)damager).getSkeletonType() == Skeleton.SkeletonType.STRAY)
                x = "STRAY";
            else if (damager.getType() == EntityType.ZOMBIE && ((Zombie)damager).isVillager()) 
                x = "ZOMBIE_VILLAGER";
            else if (damager.getType() == EntityType.ZOMBIE && isHusk(damager)) 
                x = "HUSK";
        }
        x = this.mobNameConfigurate(x);
        try {
            final Object o = this.fc.getString("mob-names." + x);
            this.assertNotNull(o);
            x = o.toString();
        }
        catch (Exception ex) {}
        catch (AssertionError assertionError) {}
        return ChatColor.translateAlternateColorCodes('&', x);
    }
    
    private String getMessage(final String string) {
        if (!this.fc.contains(string)) {
            return "";
        }
        final List<String> fx = (List<String>)this.fc.getStringList(string);
        if (fx.size() < 1) {
            return "";
        }
        if (fx.size() == 1) {
            return fx.get(0);
        }
        return fx.get(this.r.nextInt(fx.size()));
    }
    
    private void assertNotNull(final Object o) throws AssertionError {
        if (o == null) {
            throw new AssertionError();
        }
    }
    
    void broadcastDebug(final String deathmsg, final Player victim, final World world, final boolean isPvP) {
        if (!this.fc.getBoolean("per-world-messages")) {
            for (final Player p : this.pl.getServer().getOnlinePlayers()) {
                if (p.hasPermission("deathmessagesprime.debug")) {
                    this.sendMessage(p, deathmsg);
                }
            }
            return;
        }
        if (world == null) {
            return;
        }
        try {
        if (isPvP && this.pl.pl.contains(world.getName())) {
            return;
        }
        if (!isPvP && this.pl.nl.contains(world.getName())) {
            return;
        }
        if (isPvP && this.pl.ppl.contains(world.getName())) {
            if (this.lastvictim != null) {
                if (lastvictim.hasPermission("deathmessagesprime.debug")) {
                    this.sendMessage(this.lastvictim, deathmsg);
                }
            }
            if (this.lastkiller != null) {
                if (lastkiller.hasPermission("deathmessagesprime.debug")) {
                    this.sendMessage(this.lastkiller, deathmsg);
                }
            }
            return;
        }
        if (!isPvP && this.pl.pnl.contains(world.getName())) {
            if (this.lastvictim != null) {
                if (lastvictim.hasPermission("deathmessagesprime.debug")) {
                    this.sendMessage(this.lastvictim, deathmsg);
                }
            }
            return;
        }
        } catch (Exception ex) {
        }
        double radius = -1, pvpradius = -1;
        Location vloc = victim.getLocation();
        if (pl.radius.containsKey(world.getName())) {
            radius = pl.radius.get(world.getName());
        }
        if (pl.pvpradius.containsKey(world.getName())) {
            pvpradius = pl.pvpradius.get(world.getName());
        }
        if (isPvP && (pvpradius >= 0 || (radius >= 0 && !pl.pvpradius.containsKey(world.getName())))) {
            double checkradius = pvpradius;
            if (checkradius < 0) {
                checkradius = radius;
            }
            for (final Player p : world.getPlayers()) {
                if (p.getLocation().distanceSquared(vloc) <= checkradius && p.hasPermission("deathmessagesprime.debug"))
                    this.sendMessage(p, deathmsg);
            }
        } else if (!isPvP && radius >= 0) {
            for (final Player p : world.getPlayers()) {
                if (p.getLocation().distanceSquared(vloc) <= radius && p.hasPermission("deathmessagesprime.debug"))
                    this.sendMessage(p, deathmsg);
            }
        } else
            for (final Player p : world.getPlayers()) {
                if (p.hasPermission("deathmessagesprime.debug")) {
                    this.sendMessage(p, deathmsg);
                }
            }
        final ArrayList<String> f = new ArrayList<String>();
        f.add(world.getName());
        if (this.fc.getBoolean("world-groups._enabled") && (radius < 0 && (!isPvP || pvpradius < 0))) {
            for (final String s : this.fc.getConfigurationSection("world-groups").getKeys(false)) {
                List<String> ls = this.fc.getConfigurationSection("world-groups").getStringList(s);
                if (ls.contains(world.getName())) {
                    for (final String t : ls) {
                        if (f.contains(t)) {
                            continue;
                        }
                        try {
                            for (final Player p2 : this.pl.getServer().getWorld(t).getPlayers()) {
                                if (p2.hasPermission("deathmessagesprime.debug")) {
                                    this.sendMessage(p2, deathmsg);
                                }
                            }
                        }
                        catch (Exception ex) {}
                        f.add(t);
                    }
                }
            }
        }
    }

    void broadcastDeath(final TextComponent deathmsg, final Player victim, final World world, final boolean isPvP, final String vdeathmsg, int prefixLen, int suffixLen) {
        ++dmid;
        DeathMessagePreparedEvent prep = new DeathMessagePreparedEvent(dmid, deathmsg, victim, isPvP);
        pl.getServer().getPluginManager().callEvent(prep);
        Set<UUID> alwaysShow = prep.getAlwaysShowSet();
        Set<UUID> alwaysHide = prep.getAlwaysHideSet();
        if (this.fc.getBoolean("console-death-message-even-if-disabled", true)) {
            if (this.fc.getString("console-death-message", "normal").equalsIgnoreCase("normal")) {
                String s = deathmsg.toLegacyText();
                if (this.fc.getBoolean("console-death-message-strip-prefix", false)) {
                    s = s.substring(prefixLen);
                    s = s.substring(0, s.length() - suffixLen);
                }
                if (this.fc.getBoolean("console-death-message-strip-colors", false)) {
                    s = ChatColor.stripColor(s);
                    this.pl.getLogger().info(s);
                } else {
                    Bukkit.getServer().getConsoleSender().sendMessage(s);
                }
            }
            if (this.fc.getString("console-death-message", "normal").equalsIgnoreCase("verbose")) {
                this.pl.getLogger().info(vdeathmsg);
            }
        }
        for (final UUID u : alwaysShow) {
            Player p = this.pl.getServer().getPlayer(u);
            if (!(this.pl.showdeath.containsKey(p.getUniqueId()) && this.pl.showdeath.get(p.getUniqueId()) && !p.equals(victim)))
                this.sendMessage(p, deathmsg);
        }
        alwaysHide.addAll(alwaysShow);
        if (this.pl.dmpban.contains(victim.getUniqueId()) || this.pl.tempban.containsKey(victim.getUniqueId())) {
            if (!alwaysHide.contains(victim.getUniqueId()))
                this.sendMessage(victim, deathmsg);
            return;
        }
        if (world != null) {
            if (isPvP && this.pl.pl.contains(world.getName())) {
                return;
            }
            if (!isPvP && this.pl.nl.contains(world.getName())) {
                return;
            }
            if (isPvP && this.pl.ppl.contains(world.getName())) {
                if (this.lastvictim != null) {
                    this.sendMessage(this.lastvictim, deathmsg);
                }
                if (this.lastkiller != null) {
                    this.sendMessage(this.lastkiller, deathmsg);
                }
                return;
            }
            if (!isPvP && this.pl.pnl.contains(world.getName())) {
                if (this.lastvictim != null) {
                    this.sendMessage(this.lastvictim, deathmsg);
                }
                return;
            }
        }
        if (!this.fc.getBoolean("console-death-message-even-if-disabled", true)) {
            if (this.fc.getString("console-death-message", "normal").equalsIgnoreCase("normal")) {
                String s = deathmsg.toLegacyText();
                if (this.fc.getBoolean("console-death-message-strip-prefix", false)) {
                    s = s.substring(this.fc.getString("death-messages.prefix","").length());
                    s = s.substring(0, s.length() - this.fc.getString("death-messages.suffix","").length());
                }
                if (this.fc.getBoolean("console-death-message-strip-colors", false)) {
                    s = ChatColor.stripColor(s);
                    this.pl.getLogger().info(s);
                } else {
                    Bukkit.getServer().getConsoleSender().sendMessage(s);
                }
            }
            if (this.fc.getString("console-death-message", "normal").equalsIgnoreCase("verbose")) {
                this.pl.getLogger().info(vdeathmsg);
            }
        }
        if (!this.fc.getBoolean("per-world-messages")) {
            DeathMessageBroadcastEvent br = new DeathMessageBroadcastEvent(dmid, deathmsg, victim, null, isPvP);
            pl.getServer().getPluginManager().callEvent(br);
            if (!br.isCancelled())
                for (final Player p : this.pl.getServer().getOnlinePlayers()) {
                    if (!(this.pl.showdeath.containsKey(p.getUniqueId()) && this.pl.showdeath.get(p.getUniqueId()) && !p.equals(victim)) && (!alwaysHide.contains(p.getUniqueId())))
                        this.sendMessage(p, deathmsg);
                }
            return;
        }
        if (world == null) {
            return;
        }
        double radius = -1, pvpradius = -1;
        Location vloc = victim.getLocation();
        if (pl.radius.containsKey(world.getName())) {
            radius = pl.radius.get(world.getName());
        }
        if (pl.pvpradius.containsKey(world.getName())) {
            pvpradius = pl.pvpradius.get(world.getName());
        }
        if (isPvP && (pvpradius >= 0 || (radius >= 0 && !pl.pvpradius.containsKey(world.getName())))) {
            double checkradius = pvpradius;
            if (checkradius < 0) {
                checkradius = radius;
            }
            DeathMessageBroadcastEvent br = new DeathMessageBroadcastEvent(dmid, deathmsg, victim, world, isPvP);
            pl.getServer().getPluginManager().callEvent(br);
            for (final Player p : world.getPlayers()) {
                if (br.isCancelled())
                    break;
                if (!(this.pl.showdeath.containsKey(p.getUniqueId()) && this.pl.showdeath.get(p.getUniqueId()) && !p.equals(victim)) && (!alwaysHide.contains(p.getUniqueId())))
                    if (p.getLocation().distanceSquared(vloc) <= checkradius)
                        this.sendMessage(p, deathmsg);
            }
        } else if (!isPvP && radius >= 0) {
            DeathMessageBroadcastEvent br = new DeathMessageBroadcastEvent(dmid, deathmsg, victim, world, isPvP);
            pl.getServer().getPluginManager().callEvent(br);
            for (final Player p : world.getPlayers()) {
                if (br.isCancelled())
                    break;
                if (!(this.pl.showdeath.containsKey(p.getUniqueId()) && this.pl.showdeath.get(p.getUniqueId()) && !p.equals(victim)) && (!alwaysHide.contains(p.getUniqueId())))
                    if (p.getLocation().distanceSquared(vloc) <= radius)
                        this.sendMessage(p, deathmsg);
            }
        } else {
            DeathMessageBroadcastEvent br = new DeathMessageBroadcastEvent(dmid, deathmsg, victim, world, isPvP);
            pl.getServer().getPluginManager().callEvent(br);
            for (final Player p : world.getPlayers()) {
                if (br.isCancelled())
                    break;
                if (!(this.pl.showdeath.containsKey(p.getUniqueId()) && this.pl.showdeath.get(p.getUniqueId()) && !p.equals(victim)) && (!alwaysHide.contains(p.getUniqueId())))
                    this.sendMessage(p, deathmsg);
            }   
        }
        ArrayList<String> ps = new ArrayList<String>();
        ArrayList<String> f = new ArrayList<String>();
        f.add(world.getName());
        // broadcast to world groups
        if (this.fc.getBoolean("world-groups._enabled") && (radius < 0 && (!isPvP || pvpradius < 0))) {
            for (final String s : this.fc.getConfigurationSection("world-groups").getKeys(false)) {
                List<String> ls = this.fc.getConfigurationSection("world-groups").getStringList(s);
                if (ls.contains(world.getName())) {
                    for (final String t : ls) {
                        if (f.contains(t)) {
                            continue;
                        }
                        try {
                            World w = this.pl.getServer().getWorld(t);
                            DeathMessageBroadcastEvent br = new DeathMessageBroadcastEvent(dmid, deathmsg, victim, w, isPvP);
                            pl.getServer().getPluginManager().callEvent(br);
                            if (!br.isCancelled()) {
                                for (final Player p2 : w.getPlayers()) {
                                    if (ps.contains(p2.getName())) {
                                        continue;
                                    }
                                    if (!(this.pl.showdeath.containsKey(p2.getUniqueId()) && this.pl.showdeath.get(p2.getUniqueId()) && !p2.equals(victim)) && (!alwaysHide.contains(p2.getUniqueId())))
                                        this.sendMessage(p2, deathmsg);
                                    ps.add(p2.getName());
                                }
                            }
                        }
                        catch (Exception ex) {}
                        f.add(t);
                    }
                }
            }
        }
        ps = null;
        f = null;
    }

    private void sendMessage(final Player p, final String deathmsg) {
        if (deathmsg.length() > 0) {
            p.sendMessage(deathmsg);
        }
    }
    
    private void sendMessage(final Player p, final TextComponent deathmsg) {
        p.spigot().sendMessage(deathmsg);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath_HIGHEST(final PlayerDeathEvent e) {
        if (this.pr == EventPriority.HIGHEST) {
            this.onPlayerDeath_i(e);
        }
        if (e.getEntity() == null) return;
        if (this.fc.getBoolean("death-message-compat-mode", true)) {
            if (e.getDeathMessage() == null) {
                // remove from DMP queue
                Iterator<DeathMessage> iter = queue.iterator();
                while (iter.hasNext()) {
                    DeathMessage dm = iter.next();
                    if (dm == null || dm.v.getUniqueId().equals(e.getEntity().getUniqueId())) {
                        iter.remove();
                    }
                }
                return;
            }
            if (dmc.containsKey(e.getEntity().getUniqueId()) && dmc.get(e.getEntity().getUniqueId()).equals(e.getDeathMessage())) {
                dmc.remove(e.getEntity().getUniqueId());
                e.setDeathMessage("");
            } else {
                if (pl.getConfig().getBoolean("death-message-conflict-broadcast", true)) {
                    dmc.put(e.getEntity().getUniqueId(), e.getDeathMessage());
                    e.setDeathMessage("");
                } else {
                    // remove from DMP queue
                    Iterator<DeathMessage> iter = queue.iterator();
                    while (iter.hasNext()) {
                        DeathMessage dm = iter.next();
                        if (dm == null || dm.v.getUniqueId().equals(e.getEntity().getUniqueId())) {
                            iter.remove();
                        }
                    }
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath_HIGH(final PlayerDeathEvent e) {
        if (this.pr == EventPriority.HIGH) {
            this.onPlayerDeath_i(e);
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath_NORMAL(final PlayerDeathEvent e) {
        if (this.pr == EventPriority.NORMAL) {
            this.onPlayerDeath_i(e);
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath_LOWEST(final PlayerDeathEvent e) {
        this.dhistory.remove(e.getEntity().getUniqueId());
        if (this.pr == EventPriority.LOWEST) {
            this.onPlayerDeath_i(e);
        }
    }
    
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDeath_LOW(final PlayerDeathEvent e) {
        if (this.pr == EventPriority.LOW) {
            this.onPlayerDeath_i(e);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath_MONITOR(final PlayerDeathEvent e) {
        if (this.pr == EventPriority.MONITOR) {
            this.onPlayerDeath_i(e);
        }
        if (e.getDeathMessage() != null && e.getDeathMessage().length() < 1) {
            this.flushBroadcastDeath();
        }
    }

    public String getDeathMessage(PlayerDeathEvent event) {
        UUID u = event.getEntity().getUniqueId();
        if (this.dhistory.containsKey(u)) {
            return this.dhistory.get(u);
        } else {
            return event.getDeathMessage();
        }
    }
}
