package lanetwork.teleport;

import lanetwork.commands.TpaCommand;
import lanetwork.events.TeleportEngine;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class Teleport extends JavaPlugin {

    @Override
    public void onEnable() {

        this.saveDefaultConfig();
        TpaCommand tpaEngine = new TpaCommand();
        TeleportEngine engine = new TeleportEngine(this);

        this.getCommand("tpa").setExecutor(tpaEngine);
        this.getCommand("tpahere").setExecutor(tpaEngine);
        this.getCommand("tpahereall").setExecutor(tpaEngine);
        this.getCommand("tpaccept").setExecutor(tpaEngine);
        this.getCommand("tpadeny").setExecutor(tpaEngine);
        this.getCommand("tpatoggle").setExecutor(tpaEngine);
        this.getCommand("tpaignore").setExecutor(tpaEngine);
        this.getCommand("tpaunignore").setExecutor(tpaEngine);
        this.getCommand("teleportconfig").setExecutor(engine);
        this.getCommand("rtp").setExecutor(engine);
        this.getCommand("rtpmenu").setExecutor(engine);

        this.getServer().getPluginManager().registerEvents(engine, this);
    }
    private void validateAndInjectConfigDefaults() {
        FileConfiguration config = this.getConfig();
        boolean modified = false;

        if (!config.contains("tpa.timeout")) { config.set("tpa.timeout", 60); modified = true; }
        if (!config.contains("rtp.min-radius")) { config.set("rtp.min-radius", 1000); modified = true; }
        if (!config.contains("rtp.max-radius")) { config.set("rtp.max-radius", 5000); modified = true; }
        if (!config.contains("rtp.max-attempts")) { config.set("rtp.max-attempts", 15); modified = true; }

        if (!config.contains("messages.prefix")) { config.set("messages.prefix", "<gold>[LANetwork]</gold> "); modified = true; }
        if (!config.contains("messages.no-permission")) { config.set("messages.no-permission", "<red>You do not have permission to execute this!</red>"); modified = true; }
        if (!config.contains("messages.rtp-searching")) { config.set("messages.rtp-searching", "<yellow>Locating a safe landing zone...</yellow>"); modified = true; }
        if (!config.contains("messages.rtp-success")) { config.set("messages.rtp-success", "<green>Successfully teleported to coordinates: <gold>%x%, %y%, %z%</gold>!</green>"); modified = true; }

        if (!config.contains("gui.title")) { config.set("gui.title", "§6§lRTP Destination Selection"); modified = true; }
        if (!config.contains("gui.background-item")) { config.set("gui.background-item", "GRAY_STAINED_GLASS_PANE"); modified = true; }

        if (!config.contains("gui.items") || config.getConfigurationSection("gui.items") == null || config.getConfigurationSection("gui.items").getKeys(false).isEmpty()) {
            String owPath = "gui.items.overworld_button.";
            config.set(owPath + "material", "GRASS_BLOCK");
            config.set(owPath + "slot", 11);
            config.set(owPath + "name", "§a§lOverworld Dimension");
            config.set(owPath + "lore", List.of(
                    "§7Click to execute a random teleport",
                    "§7within the native overworld surface area."
            ));
            config.set(owPath + "action-type", "WORLD");
            config.set(owPath + "action-target", "world");

            String netherPath = "gui.items.nether_button.";
            config.set(netherPath + "material", "NETHERRACK");
            config.set(netherPath + "slot", 15);
            config.set(netherPath + "name", "§c§lNether Wastes");
            config.set(netherPath + "lore", List.of(
                    "§7Click to execute a random teleport",
                    "§7safely through the nether ceiling."
            ));
            config.set(netherPath + "action-type", "WORLD");
            config.set(netherPath + "action-target", "world_nether");

            modified = true;
        }

        if (modified) {
            this.saveConfig();
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
