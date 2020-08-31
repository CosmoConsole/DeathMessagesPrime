package email.com.gmail.cosmoconsole.bukkit.deathmsg;

import org.bukkit.plugin.java.*;
import org.bukkit.configuration.file.*;
import org.bukkit.*;
import org.bukkit.plugin.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.configuration.*;
import java.io.*;
import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;

/*
 * 
 * Disclaimers to anyone reading this source code:
 * 
 * 1. This code is ugly. It started development in 2014 and has never gone
 *    under a major code clean-up or refactoring process.
 *    Don't be under the false assumption that I think this is clean
 *    or well laid out code.
 *    
 * 2. Anyone who tries to maintain this plugin should first and foremost
 *    think of a plan to clean up, refactor or perhaps even completely
 *    rewrite this code, in whole or in part, particularly the
 *    DeathListener class which is much of the core of this plugin's
 *    functionality.
 *    
 *    ~~ Refactoring the code should be the UTMOST priority of anyone
 *       willing to maintain this plugin long-term.
 * 
 * 3. Despite my best efforts, the plugin suffers from extensive feature
 *    creep, which will greatly complicate any such plans to refactor
 *    this code. Notwithstanding that, it should remain the top priority
 *    to anyone willing to maintain this code.
 * 
 * This source code was released publicly on 2020-08-31. If any subsequent
 * modifications have been made to it by its original developer, those
 * changes have last been made on 2020-08-31.
 * 
 */

public class DeathMessagesPrime extends JavaPlugin implements TabCompleter
{
    public final int CONFIG_VERSION = 55;
    
    boolean debug;
    FileConfiguration config;
    /* World names of worlds in which PvP messages are hidden */
    List<String> pl;
    /* World names of worlds in which non-PvP messages are hidden */
    List<String> nl;
    /* World names of worlds in which PvP messages are private */
    List<String> ppl;
    /* World names of worlds in which non-PvP messages are private */
    List<String> pnl;
    HashMap<String, Double> radius;
    HashMap<String, Double> pvpradius;
    ArrayList<UUID> dmpban;
    HashMap<UUID, Long> tempban;
    HashMap<UUID, Boolean> showdeath;
    static DeathMessagesPrime instance;
    static HashMap<String, DeathMessageTagListener> taglisteners;
    static HashMap<String, DeathMessageTagListener> taglistenerprefixes;
    static boolean petMessages;
    private DeathListener dl;
    
    static {
        DeathMessagesPrime.instance = null;
        petMessages = false;
    }
    
    public DeathMessagesPrime() {
        this.debug = true;
        this.config = null;
        this.pl = null;
        this.nl = null;
        this.ppl = null;
        this.pnl = null;
    }

    private static int mc_ver = 0;
    private static int mc_rev = 0;
    
    public static boolean mcVer(int comp) {
        return mc_ver >= comp;
    }

    public static boolean mcVerRev(int comp, int rev) {
        return mc_ver > comp || (mc_ver == comp && mc_rev >= rev);
    }
    
    public void onEnable() {
        (DeathMessagesPrime.instance = this).loadConfig();
        taglisteners = new HashMap<String, DeathMessageTagListener>();
        taglistenerprefixes = new HashMap<String, DeathMessageTagListener>();
        dl = new DeathListener(this, this.config);
        Bukkit.getPluginManager().registerEvents((Listener)dl, (Plugin)this);
        try {
            dl.pr = EventPriority.valueOf(this.config.getString("death-listener-priority"));
        } catch (Exception ex) {
            this.getLogger().severe("Event priority value is invalid, using default HIGH.");
        }
        if (dl.pr == EventPriority.MONITOR) {
            // once upon a time, this was suggested as a workaround for some plugins that use
            // MONITOR as the priority for catching player death events. using MONITOR for DMP
            // will however lead to undefined behavior, which is why it cannot be recommended
            this.getLogger().warning("You are using MONITOR as the DMP priority; this is not supported. Please switch to another priority.");
        }
        String ver = Bukkit.getServer().getVersion().split("\\(MC:")[1].split("\\)")[0].trim().split(" ")[0].trim();
        this.getLogger().info("Minecraft version is " + ver);
        getCommand("dmsg").setTabCompleter(this);
        try {
            String[] tokens = ver.split("\\.");
            int mcMajor = Integer.parseInt(tokens[0]);
            int mcMinor = 0;
            int mcRevision = 0;
            if (tokens.length > 1) {
                mcMinor = Integer.parseInt(tokens[1]);
            }
            if (tokens.length > 2) {
                mcRevision = Integer.parseInt(tokens[2]);
            }
            mc_ver = mcMajor * 1000 + mcMinor;
            mc_rev = mcRevision;
            // 1.8 = 1_008
            // 1.9 = 1_009
            // 1.10 = 1_010
            // ...
            // 1.14 = 1_014
            // 1.15 = 1_015
        } catch (Exception ex) {
            this.getLogger().warning("Cannot detect Minecraft version from string - " +
                                     "some features will not work properly. " + 
                                     "Please contact the plugin author if you are on " +
                                     "standard CraftBukkit or Spigot. This plugin " + 
                                     "expects getVersion() to return a string " + 
                                     "containing '(MC: 1.15)' or similar. The version " + 
                                     "DMP tried to parse was '" + ver + "'");
        }
    }
    
    private void loadConfig() {
        this.config = this.getConfig();
        this.dmpban = new ArrayList<UUID>();
        this.tempban = new HashMap<UUID, Long>();
        this.showdeath = new HashMap<UUID, Boolean>();
        try {
            this.config.load(new File(this.getDataFolder(), "config.yml"));
            if (!this.config.contains("config-version")) {
                throw new Exception();
            }
            if (this.config.getInt("config-version") < CONFIG_VERSION) {
                if (!ConfigUpdater.updateConfig(new File(this.getDataFolder(), "config.yml"), CONFIG_VERSION))
                    throw new ConfigTooOldException();
                else
                    this.config.save(new File(this.getDataFolder(), "config.yml"));
            }
        }
        catch (FileNotFoundException e6) {
            this.getLogger().info("Extracting default config.");
            this.saveResource("config.yml", true);
            try {
                this.config.load(new File(this.getDataFolder(), "config.yml"));
            }
            catch (IOException | InvalidConfigurationException ex3) {
                ex3.printStackTrace();
                this.getLogger().severe("The JAR config is broken, disabling");
                this.getServer().getPluginManager().disablePlugin((Plugin)this);
                this.setEnabled(false);
            }
        }
        catch (ConfigTooOldException e3) {
            this.getLogger().warning("!!! WARNING !!! Your configuration is old. There may be new features or some config behavior might have changed, so it is advised to regenerate your config when possible!");
        }
        catch (Exception e4) {
            e4.printStackTrace();
            this.getLogger().severe("Configuration is invalid. Re-extracting it.");
            final boolean success = !new File(this.getDataFolder(), "config.yml").isFile() || new File(this.getDataFolder(), "config.yml").renameTo(new File(this.getDataFolder(), "config.yml.broken" + new Date().getTime()));
            if (!success) {
                this.getLogger().severe("Cannot rename the broken config, disabling");
                this.getServer().getPluginManager().disablePlugin((Plugin)this);
                this.setEnabled(false);
            }
            this.saveResource("config.yml", true);
            try {
                this.config.load(new File(this.getDataFolder(), "config.yml"));
            }
            catch (IOException | InvalidConfigurationException ex4) {
                ex4.printStackTrace();
                this.getLogger().severe("The JAR config is broken, disabling");
                this.getServer().getPluginManager().disablePlugin((Plugin)this);
                this.setEnabled(false);
            }
        }
        this.debug = this.config.getBoolean("debug");
        this.nl = (List<String>)this.config.getStringList("worlds-no-natural-death-messages");
        if (this.nl == null) {
            this.nl = new ArrayList<String>();
        }
        this.pl = (List<String>)this.config.getStringList("worlds-no-pvp-death-messages");
        if (this.pl == null) {
            this.pl = new ArrayList<String>();
        }
        this.pnl = (List<String>)this.config.getStringList("worlds-private-natural-death-messages");
        if (this.pnl == null) {
            this.pnl = new ArrayList<String>();
        }
        this.ppl = (List<String>)this.config.getStringList("worlds-private-pvp-death-messages");
        if (this.ppl == null) {
            this.ppl = new ArrayList<String>();
        }
        petMessages = this.config.getBoolean("show-named-pet-death-messages", false);
        List<String> banned = this.config.getStringList("player-blacklist");
        if (banned != null) {
            for (String s: banned) {
                try {
                    dmpban.add(UUID.fromString(s));
                } catch (Exception ex) {
                    this.getLogger().warning("Illegal UUID in player blacklist! " + s);
                }
            }
        }
        this.radius = new HashMap<String, Double>();
        this.pvpradius = new HashMap<String, Double>();
        try {
            ConfigurationSection cs = this.config.getConfigurationSection("worlds-death-message-radius");
            for (String key: cs.getKeys(false)) {
                double d = cs.getDouble(key);
                radius.put(key, d * d);
            }
        } catch (NullPointerException ex) {
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        try {
            ConfigurationSection cs = this.config.getConfigurationSection("worlds-pvp-death-message-radius");
            for (String key: cs.getKeys(false)) {
                double d = cs.getDouble(key);
                pvpradius.put(key, d * d);
            }
        } catch (NullPointerException ex) {
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Gets the death message for a specific PlayerDeathEvent. This
     * death message may either be the default death message given
     * by Minecraft (or passed from below) or the message prepared
     * by DeathMessagesPrime, depending on whether the message has
     * been prepared by the point this function is called.
     * 
     * Because of this, if you want the DeathMessagesPrime death
     * message, this function should be called as late as possible
     * - ideally on the MONITOR event priority.
     * 
     * Note that this function is only supported to be called from
     * within any listener of the event this function is given.
     * If the event is stored for later use and this is called
     * much later, this function can give wrong results.
     * 
     * @param event The PlayerDeathEvent to get the message for.
     * 
     * @return Either the Minecraft death message or the
     *         DeathMessagesPrime death message, if the latter has
     *         been prepared.
     */
    public String getDeathMessage(PlayerDeathEvent event) {
        return dl.getDeathMessage(event);
    }
    
    /**
     * Returns the event priority that DMP has been configured to use
     * within the configuration.
     * 
     * @return The event priority; any event priority is possible, but
     *         MONITOR should be considered unsupported.
     */
    public EventPriority getEventPriority() {
        return dl.pr;        
    }
    
    /**
     * Registers a tag (like %player% and %world%) for use in death messages.
     * 
     * @param plugin The plugin that is registering a tag.
     * @param tag A unique (for this and all other plugins) tag without the % signs, such as test for %test%. The tag must be unique for all plugins that may register tags. 
     * 
     * If it overlaps with existing tags in DeathMessagesPrime itself, the register will be successful, but the listener will never be called.
     * Tag listeners should return null or an empty TextComponent on failure or "unknown tag". 
     * 
     * Tags are case-sensitive. If possible, tags should only contain alphanumeric characters and underscores.
     * @param listener A class implementing the DeathMessageTagListener interface.
     */
    public void registerTag(Plugin plugin, String tag, DeathMessageTagListener listener) {
        if (taglisteners.containsKey(tag)) {
            throw new IllegalArgumentException("tag already registered");
        }
        taglisteners.put(tag, listener);
    }
    
    /**
     * Registers a tag prefix for use in death messages.
     * 
     * @param plugin The plugin that is registering a tag.
     * @param tag A unique prefix for tags. It will be automatically followed by an underscore. For example, if the prefix is test, the plugin will get formatTag calls for %test_*%, with * being anything. 
     * 
     * If it overlaps with existing tags in DeathMessagesPrime itself, the register will be successful, but the listener will never be called.
     * Tag listeners should return null on failure or "unknown tag". 
     * 
     * Tags are case-sensitive. If possible, tags should only contain alphanumeric characters and underscores.
     * @param listener A class implementing the DeathMessageTagListener interface.
     */
    public void registerTagPrefix(Plugin plugin, String prefix, DeathMessageTagListener listener) {
        if (taglisteners.containsKey(prefix + "_")) {
            throw new IllegalArgumentException("prefix already registered");
        }
        taglistenerprefixes.put(prefix + "_", listener);
    }
    
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final String COMMAND_NO_PERMISSION_MESSAGE = ChatColor.RED + ChatColor.translateAlternateColorCodes('&', config.getString("no-permission", "You have no permission to run this command."));
        final boolean include_prefix = config.getBoolean("dmp-prefix-in-command-messages", true);
        if (cmd.getName().equalsIgnoreCase("dmsg")) {
            if (args.length == 0) {
                if (sender instanceof Player && !sender.hasPermission("deathmessagesprime.version")) {
                    sender.sendMessage(COMMAND_NO_PERMISSION_MESSAGE);
                    return true;
                }
                sender.sendMessage("§aDeathMessagesPrime v" + this.getDescription().getVersion() + " by CosmoConsole");
                if (!(sender instanceof Player) || sender.hasPermission("deathmessagesprime.reload")) {
                    sender.sendMessage("§6/dmsg reload to reload");
                }
                if (!(sender instanceof Player) || sender.hasPermission("deathmessagesprime.uuid")) {
                    sender.sendMessage("§6/dmsg uuid playername to get UUID");
                }
                return true;
            }
            if (args[0].equalsIgnoreCase("reload")) {
                if (sender instanceof Player && !sender.hasPermission("deathmessagesprime.reload")) {
                    sender.sendMessage(COMMAND_NO_PERMISSION_MESSAGE);
                    return true;
                }
                this.loadConfig();
                DMPReloadEvent pree = new DMPReloadEvent();
                this.getServer().getPluginManager().callEvent(pree);
                sender.sendMessage((include_prefix ? "§a[DMP] §6" : "") + ChatColor.translateAlternateColorCodes('&', config.getString("reload-complete", "Reload complete!")));
                return true;
            }
            if (args[0].equalsIgnoreCase("uuid")) {
                if (sender instanceof Player && !sender.hasPermission("deathmessagesprime.reload")) {
                    sender.sendMessage(COMMAND_NO_PERMISSION_MESSAGE);
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("/dmsg uuid <playername>");
                    return true;
                }
                String pn = args[1];
                Player pl = Bukkit.getPlayer(pn);
                if (pl == null) {
                    sender.sendMessage((include_prefix ? "§a[DMP] §c" : "") + ChatColor.translateAlternateColorCodes('&', config.getString("cannot-find-online-player", "Cannot find online player")));
                    return true;
                }
                sender.sendMessage((include_prefix ? "§a[DMP] §a" : "") + pl.getUniqueId().toString());
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("toggledeathmsg")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + ChatColor.translateAlternateColorCodes('&', config.getString("players-only-command", "Only players can run this command.")));
                return true;
            }
            if (!sender.hasPermission("deathmessagesprime.toggle")) {
                sender.sendMessage(COMMAND_NO_PERMISSION_MESSAGE);
                return true;
            }
            UUID u = ((Player) sender).getUniqueId();
            boolean old_value = false;
            if (showdeath.containsKey(u))
                old_value = showdeath.get(u);
            if (old_value) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("death-messages-shown", "§a[DMP] §aDeath messages from other players will now be SHOWN")));
                showdeath.put(u, false);
            } else {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("death-messages-hidden", "§a[DMP] §aDeath messages from other players will now be HIDDEN")));
                showdeath.put(u, true);
            }
            return true;
        }
        return false;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equals("dmsg")) {
            if (args.length == 1) {
                List<String> suggestions = new ArrayList<>();

                if (sender instanceof Player && !sender.hasPermission("deathmessagesprime.reload")) {
                    return null;
                }
                
                suggestions.add("reload");
                suggestions.add("uuid");
                return suggestions;
            } else if (args.length == 2 && args[0].equalsIgnoreCase("uuid")) {
                List<String> suggestions = new ArrayList<>();

                if (sender instanceof Player && !sender.hasPermission("deathmessagesprime.reload")) {
                    return null;
                }
                for (Player p: getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        suggestions.add(p.getName());
                    }
                }

                return suggestions;
            }
        }
        return null;
    }
}
 