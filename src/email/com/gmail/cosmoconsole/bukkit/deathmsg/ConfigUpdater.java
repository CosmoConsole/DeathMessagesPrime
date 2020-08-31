package email.com.gmail.cosmoconsole.bukkit.deathmsg;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;

public class ConfigUpdater {
    
    public static boolean updateConfig(File configFile, int targetVersion) {
        return false;
    }
        /*
         // does not work, messes up formatting
         
        int version = config.getInt("config-version");
        do {
            if (version < 54) return false;
            switch (version)
            {
            case 54: updateConfig54(config); break;
            }
            ++version;
        } while (version < targetVersion);
        config.set("config-version", version + 1);
        return true;
    }
    
    private static List<String> makeList(String x) {
        return Arrays.asList(x);
    }

    private static void updateConfig54(FileConfiguration config) {
        config.set("death-messages.mob.PiglinBruteMelee", makeList("%plrtag% was slain by Piglin Brute"));
        config.set("death-messages.mob.PiglinBruteCustom", makeList("%plrtag% was slain by Piglin Brute using [%weapon%&f]"));
        config.set("death-messages.namedmob.PiglinBruteMelee", makeList("%plrtag% was slain by %killer%"));
        config.set("death-messages.namedmob.PiglinBruteCustom", makeList("%plrtag% was slain by %killer% using [%weapon%&f]"));
        config.set("mob-names.PiglinBrute", "Piglin Brute");
    }*/
    
}
