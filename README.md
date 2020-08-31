_This release is part of the source code release as outlined in [this announcement](https://www.spigotmc.org/threads/deathmessagesprime.48322/page-53#post-3933244)._

# DeathMessagesPrime

_DeathMessagesPrime_ is a death message plugin for Minecraft servers running the
[Spigot](https://www.spigotmc.org/) server software. This plugin was developed from
late 2014 to late-mid 2020, with first public releases in February 2015. Its original
developer, CosmoConsole, will no longer be supporting the plugin starting from
October 2020, and its source code is being released under the CC0 license, effectively
releasing the code into the public domain.

The original plugin page was located [here](https://www.spigotmc.org/resources/deathmessagesprime.3789/); it may, depending on the time of viewing,
have been taken over by another developer who has kindly assumed the task of
maintaining the plugin. As stated, I wish any developer that seeks to maintain
this code or fork it to massively refactor or possibly even rewrite large portions
or even the entire code. It has never been refactored over its long development
history despite the fact that it should have been, perhaps even more than once.
Bad decisions made years ago still haunt this code base and manifest in truly
awful forms of spaghetti code that borders on unmaintainable.

In part, the messiness is also caused by the fact that DMP is meant to support
a wide range of Minecraft versions. The plugin supports versions from 1.7.10
to 1.16.2, the most recent at the time of writing.

For the second time, I encourage any developer seeking to work on this code
to first dedicate their efforts into making it more maintainable. I would never
write code like this today, and I would have rewritten the plugin myself from
the ground up if I had had more time to actually do it. I apologize in advance.

Issues or pull requests made to this repository will be ignored, unless they are
questions about how the code works and are posted before this repository is
archived sometime in October or November 2020.

## Original plugin description

![Plugin banner](https://i.imgur.com/bMXd0BX.png)

**DeathMessagesPrime** is a simple-to-use but still versatile death message plugin. It allows to freely format and modify all death messages in Minecraft to date.

Want to...

- ...change the death message for someone being blown up a creeper?
- ...have multiple different death message choices depending on whether people die from a short or long fall?
- ...add a custom message only for one of your VIP players for dying after being shoved into the void?

No problem, this plugin can handle all of those cases.

### Commands

/dmsg allows to reload the configuration. The permission node `deathmessagesprime.reload` controls the ability to do so. Further, the command without any parameters will display the plugin version and available commands, if the player has the `deathmessagesprime.version` permission.

/toggledeathmsg allows players to toggle displaying death messages to themselves, with the` deathmessagesprime.toggle` permission. Note that this permission is originally for OPs only, which is not what you may want.

The plugin also has a debug mode which can be enabled from the configuration to aid solving conflicts.

Examples of some versatile features of this plugin:

- Allows distinguishing between non-named and named mobs in death messages and displaying forms of them in the death messages.
- Separates normal and elder Guardians.
- Supports CrackShot (kind of)
- Allows to disable PVP and non-PVP messages in specific worlds
- A world group feature to display death messages in a specific world in its group only
- Multiple death messages for each reason
- Biome in death messages
- Distinguishes between short and long fall kills (like Minecraft does, compare "hit the ground too hard" and "fell from a high place")
- Allows to specify death messages when 'pushed' by a mob or player
- Allows changing lightning/enderpearl death messages (same in vanilla Minecraft as fire tick/fall messages)
- Supports player display names in messages
- Custom messages for mobs with specific names, per regex (therefore support for custom bosses)
- Supports item tooltips for when the weapon is displayed in a message
- Death messages within a radius and 1&1 (only killer and victim receives the message) for PVP deaths
- heart-compat-mode for plugins that may display a health bar as the mob name
- API for getting the death message, addons with events and custom tag support (with javadocs)

### Common problems/questions & solutions
#### Hearts appear in the death message instead of the mob's name
Enable heart-compat-mode from within the configuration file (from false to true).

#### Adding multiple death messages so that one is picked at random
Simply add multiple death messages under a specific reason (adding further messages with the same hyphen-first syntax). The plugin will pick one of the messages at random upon a death.

#### Various conflicts with compat-mode on or off
If you're a plugin developer and you modify the death message in PlayerDeathEvent, make sure your plugin's event priority is less than HIGHEST, which is the priority DMP uses to detect and solve conflicts.

If you are a server administrator, you should contact the developers of both plugins, if possible. DMP so far has a fairly advanced mechanism for detecting conflicts, but it's not perfect.

#### Add support for plugin X!
You should ask the developer of plugin X: DMP has an API. In fact, no API whatsoever is needed, as long as the other plugin uses the standard setDeathMessage via PlayerDeathEvent on any priority lower than HIGHEST, and no extra DMP features (such as messages within DMP configs) are needed. For plugins that check on MONITOR, you can try DMPMonitorCompat.

#### invalid character, unacceptable code point, special characters are not allowed...
Use a text editor that supports UTF-8. Most modern editors and even some older ones should support it (note that Notepad does not fully support it, unless you are on Windows 10 May 2019 or above). If your editor does support it, make sure you're saving the file as UTF-8 (the exact steps depend on your text editor). You should not save with BOM (byte order mark), but without it.

#### ClassNotFoundException: net.md_5.bungee.api...
You are likely running CraftBukkit, which is not supported by this plugin. Please migrate to Spigot instead.

#### %weapon_name%, %weapon% display ???
This happens if you are using them in unsupported messages. Out of PVP messages, only PlayerCustom, PlayerTridentCustom and PlayerProjectileCustom support these two placeholders. The rest are FallKillWeapon, FallFinishKillWeapon, and under mob messages everything ending in Custom.

These Custom messages are used whenever the weapon used for the kill has a custom name, while the messages without %weapon% are used for hand kills or kills made with weapons without custom names.

You can make the plugin use the Custom messages, with %weapon%, for the latter by enabling show-custom-death-msg-on-all-weapons. Hand kills will still use the messages without %weapon%.
