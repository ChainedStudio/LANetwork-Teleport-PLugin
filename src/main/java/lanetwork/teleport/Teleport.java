package lanetwork.teleport;

import lanetwork.commands.TpaCommand;
import lanetwork.events.SoundEngine;
import lanetwork.events.TeleportEngine;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public final class Teleport extends JavaPlugin {

    private SoundEngine soundEngine;
    private FileConfiguration messagesConfig;
    private File messagesFile;

    @Override
    public void onEnable() {
        // Save and update standard configuration layers
        this.saveDefaultConfig();
        validateAndInjectConfigDefaults();

        // Initialize dynamic localization configurations
        loadMessagesConfig();

        // 1. Initialize the Sound Engine
        this.soundEngine = new SoundEngine(this);

        // 2. Instantiate Handlers
        TpaCommand tpaEngine = new TpaCommand(this);
        TeleportEngine engine = new TeleportEngine(this);

        // 3. Register TPA Subsystem Commands
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

        // 4. Register Main Teleportation and GUI Engine Commands
        // FIX: Added "teleportconfig" and "rtpmenu" to ensure they map back to TeleportEngine correctly
        String[] engineCommands = {
                "rtp", "rtpmenu", "teleportconfig", "teleportadmin", "ta"
        };

        for (String cmd : engineCommands) {
            var pluginCmd = this.getCommand(cmd);
            if (pluginCmd != null) {
                pluginCmd.setExecutor(engine);
                pluginCmd.setTabCompleter(engine);
            }
        }
    }

    public SoundEngine getSoundEngine() {
        return this.soundEngine;
    }

    public FileConfiguration getMessagesConfig() {
        return this.messagesConfig;
    }

    public void loadMessagesConfig() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
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
            modified = true;
        }

        if (modified) {
            this.saveConfig();
        }
    }
}