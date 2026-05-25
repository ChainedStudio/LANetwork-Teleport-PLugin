package lanetwork.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ConfigMenu implements InventoryHolder {

    private final JavaPlugin plugin;
    private final String configScreenType;
    private Inventory inventory;

    public static final String MAIN_TITLE = "§9§lTeleport Admin Settings";
    public static final String TPA_TITLE = "§b§lTPA Core Settings Config";
    public static final String RTP_TITLE = "§5§lRTP Core Settings Config";
    public static final String SOUNDS_TITLE = "§a§lSound Effects Config";

    public ConfigMenu(JavaPlugin plugin) {
        this(plugin, "main");
    }

    public ConfigMenu(JavaPlugin plugin, String configScreenType) {
        this.plugin = plugin;
        this.configScreenType = configScreenType;
    }

    public String getConfigScreenType() {
        return configScreenType;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory != null ? this.inventory : Bukkit.createInventory(this, 54, Component.text(""));
    }

    public void openMainMenu(Player player) {
        ConfigMenu holder = new ConfigMenu(plugin, "main");
        holder.inventory = Bukkit.createInventory(holder, 54, Component.text(MAIN_TITLE));
        addBackgroundFiller(holder.inventory);

        holder.inventory.setItem(19, createGuiItem(Material.CLOCK, "§b§lTPA Subsystem Settings",
                List.of("§7Click to modify request expiry timeouts", "§7and behavior attributes.")));
        holder.inventory.setItem(22, createGuiItem(Material.COMPASS, "§5§lRTP Subsystem Settings",
                List.of("§7Click to change minimum/maximum boundaries", "§7or adjust layout interface paths.")));
        holder.inventory.setItem(25, createGuiItem(Material.JUKEBOX, "§a§lSound Profiles Settings",
                List.of("§7Click to map fully custom plugin audio triggers", "§7live through in-game click prompts.")));

        player.openInventory(holder.inventory);
    }

    public void openTpaMenu(Player player) {
        ConfigMenu holder = new ConfigMenu(plugin, "tpa");
        holder.inventory = Bukkit.createInventory(holder, 54, Component.text(TPA_TITLE));
        addBackgroundFiller(holder.inventory);

        FileConfiguration config = plugin.getConfig();
        int currentTimeout = config.getInt("tpa.timeout", 60);

        holder.inventory.setItem(22, createGuiItem(Material.OAK_SIGN, "§b§lTPA Request Expiry Timeout", List.of(
                "§7Adjust total duration in seconds before active",
                "§7teleport requests expire automatically.",
                "",
                "§7Current Setting: §e" + currentTimeout + " seconds",
                "",
                "§a§lLeft-Click §7to add §a+5s",
                "§c§lRight-Click §7to remove §c-5s"
        )));

        holder.inventory.setItem(49, createGuiItem(Material.BARRIER, "§c§lBack to Main Menu", List.of("§7Return to core control module.")));
        player.openInventory(holder.inventory);
    }

    public void openRtpMenu(Player player) {
        ConfigMenu holder = new ConfigMenu(plugin, "rtp");
        holder.inventory = Bukkit.createInventory(holder, 54, Component.text(RTP_TITLE));
        addBackgroundFiller(holder.inventory);

        FileConfiguration config = plugin.getConfig();
        int min = config.getInt("rtp.min-radius", 1000);
        int max = config.getInt("rtp.max-radius", 5000);
        int attempts = config.getInt("rtp.max-attempts", 15);

        holder.inventory.setItem(19, createGuiItem(Material.MAP, "§a§lMinimum Boundary Radius", List.of(
                "§7Controls inner safe-zone boundary radius bounds.",
                "§7RTP won't place players closer than this to spawn.",
                "",
                "§7Current Setting: §e" + min + " blocks",
                "",
                "§a§lLeft-Click §7to increase §a+250",
                "§c§lRight-Click §7to decrease §c-250"
        )));

        holder.inventory.setItem(22, createGuiItem(Material.ENDER_PEARL, "§5§lMaximum Boundary Radius", List.of(
                "§7Controls outer threshold radius bounds.",
                "",
                "§7Current Setting: §e" + max + " blocks",
                "",
                "§a§lLeft-Click §7to increase §a+500",
                "§c§lRight-Click §7to decrease §c-500"
        )));

        holder.inventory.setItem(25, createGuiItem(Material.ANVIL, "§d§lSafe Zone Search Attempts", List.of(
                "§7Maximum structural safe location checks completed",
                "§7asynchronously before throwing a validation failure.",
                "",
                "§7Current Setting: §e" + attempts + " cycles",
                "",
                "§a§lLeft-Click §7to add §a+1",
                "§c§lRight-Click §7to remove §c-1"
        )));

        holder.inventory.setItem(31, createGuiItem(Material.NETHER_STAR, "§b§lOpen Layout Button Editor", List.of(
                "§7Directly re-route menu item paths,",
                "§7slot allocations, custom lore displays,",
                "§7and namespaced materials."
        )));

        holder.inventory.setItem(49, createGuiItem(Material.BARRIER, "§c§lBack to Main Menu", List.of("§7Return to core control module.")));
        player.openInventory(holder.inventory);
    }

    public void openSoundsMenu(Player player) {
        ConfigMenu holder = new ConfigMenu(plugin, "sounds");
        holder.inventory = Bukkit.createInventory(holder, 54, Component.text(SOUNDS_TITLE));
        addBackgroundFiller(holder.inventory);

        FileConfiguration config = plugin.getConfig();
        String sendSound = config.getString("sounds.send-request", "ENTITY_EXPERIENCE_ORB_PICKUP");
        String receiveSound = config.getString("sounds.receive-request", "BLOCK_NOTE_BLOCK_CHIME");
        String tpSound = config.getString("sounds.teleport", "ENTITY_ENDERMAN_TELEPORT");
        String denySound = config.getString("sounds.deny", "BLOCK_ANVIL_LAND");

        holder.inventory.setItem(19, createGuiItem(Material.GOLDEN_APPLE, "§6§lOutbound Request Audio Chime", List.of(
                "§7Audio alert heard when sending requests.",
                "§7Current: §e" + sendSound,
                "",
                "§d§lClick §7to update via Chat Prompt"
        )));
        holder.inventory.setItem(21, createGuiItem(Material.ENCHANTED_GOLDEN_APPLE, "§b§lInbound Request Audio Chime", List.of(
                "§7Audio alert heard by recipients of active requests.",
                "§7Current: §e" + receiveSound,
                "",
                "§d§lClick §7to update via Chat Prompt"
        )));
        holder.inventory.setItem(23, createGuiItem(Material.ENDER_PEARL, "§5§lTeleportation Finish Audio Warp", List.of(
                "§7Audio trigger executed upon safe landing success.",
                "§7Current: §e" + tpSound,
                "",
                "§d§lClick §7to update via Chat Prompt"
        )));
        holder.inventory.setItem(25, createGuiItem(Material.ANVIL, "§c§lRequest Cancel/Deny Warning Sound", List.of(
                "§7Audio feedback played when requests are dropped.",
                "§7Current: §e" + denySound,
                "",
                "§d§lClick §7to update via Chat Prompt"
        )));

        holder.inventory.setItem(40, createGuiItem(Material.WRITABLE_BOOK, "§a§lOpen Official Sounds Wiki List", List.of(
                "§7Prints a clickable link in chat directing to",
                "§7the official Javadocs sound path definitions."
        )));

        holder.inventory.setItem(49, createGuiItem(Material.BARRIER, "§c§lBack to Main Menu", List.of("§7Return to core control module.")));
        player.openInventory(holder.inventory);
    }

    private void addBackgroundFiller(Inventory inv) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
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