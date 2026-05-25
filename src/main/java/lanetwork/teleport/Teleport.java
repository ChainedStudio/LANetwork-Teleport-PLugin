package lanetwork.teleport;

import lanetwork.commands.TpaCommand;
import lanetwork.events.SoundEngine;
import lanetwork.events.TeleportEngine;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class Teleport extends JavaPlugin {

    private SoundEngine soundEngine;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        validateAndInjectConfigDefaults();

        // 1. Initialize the Sound Engine Framework
        this.soundEngine = new SoundEngine(this);

        // 2. Instantiate handlers
        TpaCommand tpaEngine = new TpaCommand(this);
        TeleportEngine engine = new TeleportEngine(this);

        // 3. Register TPA Command Executors
        String[] tpaCommands = {
                "tpa", "tpaauto", "tpahere", "tpahereall",
                "tpaccept", "tpadeny", "tpatoggle", "tpaignore", "tpaunignore", "debug"
        };

        for (String cmd : tpaCommands) {
            var pluginCmd = this.getCommand(cmd);
            if (pluginCmd != null) {
                pluginCmd.setExecutor(tpaEngine);
            }
        }

        // 4. Register Engine & GUI Menu Core Commands directly to our unified handler
        String[] engineCommands = {"rtp", "rtpmenu", "teleportconfig", "teleportadmin", "ta"};
        for (String cmd : engineCommands) {
            var pluginCmd = this.getCommand(cmd);
            if (pluginCmd != null) {
                pluginCmd.setExecutor(engine);
                pluginCmd.setTabCompleter(engine);
            } else {
                this.getLogger().warning("Command '" + cmd + "' is missing from plugin.yml!");
            }
        }

        // 5. Register GUI event listener safely
        this.getServer().getPluginManager().registerEvents(engine, this);
        this.getLogger().info("LanetworkTeleport engine systems linked successfully.");
    }

    public SoundEngine getSoundEngine() {
        return this.soundEngine;
    }

    private void validateAndInjectConfigDefaults() {
        FileConfiguration config = this.getConfig();
        boolean modified = false;

        if (!config.contains("sounds.send-request")) { config.set("sounds.send-request", "ENTITY_EXPERIENCE_ORB_PICKUP"); modified = true; }
        if (!config.contains("sounds.receive-request")) { config.set("sounds.receive-request", "BLOCK_NOTE_BLOCK_CHIME"); modified = true; }
        if (!config.contains("sounds.teleport")) { config.set("sounds.teleport", "ENTITY_ENDERMAN_TELEPORT"); modified = true; }
        if (!config.contains("sounds.deny")) { config.set("sounds.deny", "BLOCK_ANVIL_LAND"); modified = true; }

        if (!config.contains("gui.items") || config.getConfigurationSection("gui.items") == null || config.getConfigurationSection("gui.items").getKeys(false).isEmpty()) {
            String owPath = "gui.items.overworld_button.";
            config.set(owPath + "material", "minecraft:grass_block");
            config.set(owPath + "slot", 11);
            config.set(owPath + "name", "§a§lOverworld Dimension");
            config.set(owPath + "lore", List.of("§7Click to execute a random teleport"));
            config.set(owPath + "action-type", "WORLD");
            config.set(owPath + "action-target", "world");

            String netherPath = "gui.items.nether_button.";
            config.set(netherPath + "material", "minecraft:netherrack");
            config.set(netherPath + "slot", 15);
            config.set(netherPath + "name", "§c§lNether Wastes");
            config.set(netherPath + "lore", List.of("§7Click to execute a random teleport"));
            config.set(netherPath + "action-type", "WORLD");
            config.set(netherPath + "action-target", "world_nether");

            modified = true;
        }

        if (modified) {
            this.saveConfig();
        }
    }
}