package lanetwork.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class ConfigMenu {

    private final JavaPlugin plugin;
    public static final String MAIN_TITLE = "§9§lTeleport Admin Settings";
    public static final String TPA_TITLE = "§b§lTPA Core Settings Config";
    public static final String RTP_TITLE = "§5§lRTP Core Settings Config";
    public static final String SOUNDS_TITLE = "§a§lSound Effects Config";

    public ConfigMenu(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(MAIN_TITLE));
        addBackgroundFiller(inv);

        inv.setItem(19, createGuiItem(Material.CLOCK, "§b§lTPA Subsystem Settings",
                List.of("§7Click to modify request expiry timeouts", "§7and behavior attributes.")));
        inv.setItem(22, createGuiItem(Material.COMPASS, "§5§lRTP Subsystem Settings",
                List.of("§7Click to change minimum/maximum boundaries", "§7or adjust layout interface paths.")));
        inv.setItem(25, createGuiItem(Material.JUKEBOX, "§a§lSound Profile Settings",
                List.of("§7Click to configure interactive sound effects", "§7played during teleportation and requests.")));

        player.openInventory(inv);
    }

    public void openTpaMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(TPA_TITLE));
        addBackgroundFiller(inv);

        FileConfiguration config = plugin.getConfig();
        int timeout = config.getInt("tpa.timeout", 60);

        inv.setItem(22, createGuiItem(Material.OAK_SIGN, "§b§lRequest Expiry Timeout", List.of(
                "§7Current Setting: §e" + timeout + " seconds",
                "",
                "§a§lLeft-Click §7to add 5 seconds",
                "§c§lRight-Click §7to subtract 5 seconds"
        )));

        inv.setItem(49, createGuiItem(Material.BARRIER, "§c§lBack to Main Menu", List.of("§7Return to core control module.")));
        player.openInventory(inv);
    }

    public void openRtpMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(RTP_TITLE));
        addBackgroundFiller(inv);

        FileConfiguration config = plugin.getConfig();
        int min = config.getInt("rtp.min-radius", 1000);
        int max = config.getInt("rtp.max-radius", 5000);
        int attempts = config.getInt("rtp.max-attempts", 15);

        inv.setItem(19, createGuiItem(Material.MAP, "§a§lMinimum Target Radius", List.of(
                "§7Current Bound: §e" + min + " blocks",
                "",
                "§a§lLeft-Click §7to add 250 blocks",
                "§c§lRight-Click §7to subtract 250 blocks"
        )));

        inv.setItem(22, createGuiItem(Material.ENDER_PEARL, "§d§lMaximum Target Radius", List.of(
                "§7Current Bound: §e" + max + " blocks",
                "",
                "§a§lLeft-Click §7to add 500 blocks",
                "§c§lRight-Click §7to subtract 500 blocks"
        )));

        inv.setItem(25, createGuiItem(Material.ANVIL, "§c§lMax Structural Generation Attempts", List.of(
                "§7Current Tries: §e" + attempts + " retries",
                "",
                "§a§lLeft-Click §7to increase by 1",
                "§c§lRight-Click §7to decrease by 1"
        )));

        inv.setItem(40, createGuiItem(Material.NETHER_STAR, "§d§lOpen In-Game Button Layout Editor", List.of(
                "§7Clicking here switches the main RTP",
                "§7menu view to let you customize slot numbers,",
                "§7materials, world scopes, names, and lore."
        )));

        inv.setItem(49, createGuiItem(Material.BARRIER, "§c§lBack to Main Menu", List.of("§7Return to core control module.")));
        player.openInventory(inv);
    }

    public void openSoundsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(SOUNDS_TITLE));
        addBackgroundFiller(inv);

        FileConfiguration config = plugin.getConfig();
        String tpSound = config.getString("sounds.teleport", "ENTITY_ENDERMAN_TELEPORT");
        String sendSound = config.getString("sounds.send-request", "ENTITY_EXPERIENCE_ORB_PICKUP");
        String receiveSound = config.getString("sounds.receive-request", "BLOCK_NOTE_BLOCK_CHIME");

        inv.setItem(19, createGuiItem(Material.CHORUS_FRUIT, "§b§lTeleport Sound Effect", List.of(
                "§7Played when a player successfully teleports.",
                "§7Current: §e" + tpSound,
                "",
                "§d§lLeft-Click §7to update via Chat Prompt"
        )));

        inv.setItem(22, createGuiItem(Material.GOLD_NUGGET, "§6§lSending Request Sound Effect", List.of(
                "§7Played for the player who initiates a request.",
                "§7Current: §e" + sendSound,
                "",
                "§d§lLeft-Click §7to update via Chat Prompt"
        )));

        inv.setItem(25, createGuiItem(Material.BELL, "§d§lReceiving Request Sound Effect", List.of(
                "§7Played for the recipient of an active request.",
                "§7Current: §e" + receiveSound,
                "",
                "§d§lLeft-Click §7to update via Chat Prompt"
        )));

        // NEW: Wiki Link Helper Button
        inv.setItem(40, createGuiItem(Material.WRITABLE_BOOK, "§a§lOpen Official Sounds Wiki List", List.of(
                "§7Clicking prints a clickable link in chat",
                "§7directing to the official Spigot/Bukkit Javadocs",
                "§7containing all hardcoded sound path keys."
        )));

        inv.setItem(49, createGuiItem(Material.BARRIER, "§c§lBack to Main Menu", List.of("§7Return to core control module.")));
        player.openInventory(inv);
    }

    private void addBackgroundFiller(Inventory inv) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }
    }

    private ItemStack createGuiItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            meta.lore(lore.stream().map(Component::text).toList());
            item.setItemMeta(meta);
        }
        return item;
    }
}