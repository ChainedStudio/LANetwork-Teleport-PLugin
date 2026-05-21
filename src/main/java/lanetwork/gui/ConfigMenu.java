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
    public static final String MAIN_TITLE = "§c§lAdmin Config Dashboard";
    public static final String TPA_TITLE = "§e§lTPA Configuration System";
    public static final String RTP_TITLE = "§b§lRTP Configuration System";

    public ConfigMenu(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(MAIN_TITLE));
        fillBackground(inv);

        inv.setItem(11, createGuiItem(Material.CLOCK, "§e§lTPA Settings Panel",
                List.of("§7Click to manage interval limits,", "§7timeouts, and confirmation flags.")));

        inv.setItem(15, createGuiItem(Material.COMPASS, "§b§lRTP Settings Panel",
                List.of("§7Click to manage distance radii,", "§7safety evaluations, and parameters.")));

        player.openInventory(inv);
    }

    public void openTpaMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(TPA_TITLE));
        fillBackground(inv);

        int currentTimeout = plugin.getConfig().getInt("tpa.timeout", 60);

        inv.setItem(13, createGuiItem(Material.OAK_SIGN, "§eRequest Timeout Duration",
                List.of("§7Current: §a" + currentTimeout + "s", "", "§eLeft-Click §7to add 5s", "§bRight-Click §7to subtract 5s")));

        inv.setItem(22, createGuiItem(Material.BARRIER, "§cBack to Dashboard", List.of("§7Return to main layout grid")));

        player.openInventory(inv);
    }

    public void openRtpMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(RTP_TITLE));
        fillBackground(inv);

        FileConfiguration config = plugin.getConfig();
        int min = config.getInt("rtp.min-radius", 1000);
        int max = config.getInt("rtp.max-radius", 5000);
        int attempts = config.getInt("rtp.max-attempts", 15);

        inv.setItem(10, createGuiItem(Material.MAP, "§bMinimum Bound Radius",
                List.of("§7Current: §a" + min + " blocks", "", "§eLeft-Click §7to add 250", "§bRight-Click §7to subtract 250")));

        inv.setItem(12, createGuiItem(Material.ENDER_PEARL, "§bMaximum Bound Radius",
                List.of("§7Current: §a" + max + " blocks", "", "§eLeft-Click §7to add 500", "§bRight-Click §7to subtract 500")));

        inv.setItem(14, createGuiItem(Material.ANVIL, "§bMaximum Generation Attempts",
                List.of("§7Current: §a" + attempts + " cycles", "", "§eLeft-Click §7to add 1", "§bRight-Click §7to subtract 1")));

        inv.setItem(16, createGuiItem(Material.NETHER_STAR, "§d§lEdit Custom RTP Buttons",
                List.of("§7Click to open the button manager viewport.", "§7Allows you to visually reconfigure target", "§7worlds and material icons via text.", "", "§eOpens layout editor")));

        inv.setItem(22, createGuiItem(Material.BARRIER, "§cBack to Dashboard", List.of("§7Return to main layout grid")));

        player.openInventory(inv);
    }

    private void fillBackground(Inventory inv) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
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