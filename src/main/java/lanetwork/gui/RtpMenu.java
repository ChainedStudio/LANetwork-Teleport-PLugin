package lanetwork.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class RtpMenu {

    private final JavaPlugin plugin;
    public static final String DEFAULT_TITLE = "§6§lRTP Destination Selection";
    public static final String EDIT_TITLE = "§d§lRTP Layout Button Editor";

    public RtpMenu(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player) {
        openInventoryMenu(player, false);
    }

    public void openEditorMenu(Player player) {
        openInventoryMenu(player, true);
    }

    private void openInventoryMenu(Player player, boolean isEditorMode) {
        FileConfiguration config = plugin.getConfig();
        String titleStr = isEditorMode ? EDIT_TITLE : config.getString("gui.title", DEFAULT_TITLE);
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(titleStr));

        String fillMatStr = config.getString("gui.background-item", "minecraft:gray_stained_glass_pane").toLowerCase();
        Material fillMaterial = getNamespacedMaterial(fillMatStr);
        if (fillMaterial != null && fillMaterial != Material.AIR) {
            ItemStack filler = new ItemStack(fillMaterial);
            ItemMeta fillerMeta = filler.getItemMeta();
            if (fillerMeta != null) {
                fillerMeta.displayName(Component.text(" "));
                filler.setItemMeta(fillerMeta);
            }
            for (int i = 0; i < 54; i++) {
                inv.setItem(i, filler);
            }
        }

        ConfigurationSection section = config.getConfigurationSection("gui.items");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String matStr = section.getString(key + ".material", "minecraft:barrier").toLowerCase();
                int slot = section.getInt(key + ".slot", 0);
                String name = section.getString(key + ".name", "§cMissing Name");
                List<String> rawLore = section.getStringList(key + ".lore");
                String currentWorld = section.getString(key + ".action-target", "world");

                Material mat = getNamespacedMaterial(matStr);
                if (mat == null) mat = Material.BARRIER;

                List<String> finishedLore = new ArrayList<>(rawLore);
                if (isEditorMode) {
                    finishedLore.add("");
                    finishedLore.add("§d§l[LAYOUT EDITOR MODE]");
                    finishedLore.add("§7Slot Assignment Index: §6" + slot);
                    finishedLore.add("§7Target Destination World: §e" + currentWorld);
                    finishedLore.add("§7Current Material Key: §b" + matStr);
                    finishedLore.add("");
                    finishedLore.add("§e§lLeft-Click §7to change World");
                    finishedLore.add("§b§lRight-Click §7to change Material");
                    finishedLore.add("§a§lShift+Left-Click §7to change Title Name");
                    finishedLore.add("§6§lShift+Right-Click §7to change Slot Index");
                    finishedLore.add("§d§lMiddle-Click / Drop Key §7to rewrite Lore rows");
                }

                if (slot >= 0 && slot < 54) {
                    inv.setItem(slot, createGuiItem(mat, name, finishedLore));
                }
            }
        }
        player.openInventory(inv);
    }

    private Material getNamespacedMaterial(String input) {
        String format = input.contains(":") ? input : "minecraft:" + input;
        NamespacedKey key = NamespacedKey.fromString(format);
        return (key != null) ? Registry.MATERIAL.get(key) : null;
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