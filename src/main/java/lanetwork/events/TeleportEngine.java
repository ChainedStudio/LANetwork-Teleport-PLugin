package lanetwork.events;

import lanetwork.gui.ConfigMenu;
import lanetwork.gui.RtpMenu;
import lanetwork.teleport.Teleport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class TeleportEngine implements CommandExecutor, TabCompleter, Listener {

    private final Teleport plugin;

    public TeleportEngine(Teleport plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("teleportconfig")) {
            if (!sender.hasPermission("lanetwork.admin")) {
                sender.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfig().getString("messages.no-permission", "<red>No permission.</red>")));
                return true;
            }

            // RESTORED: Argument context checks to support '/teleportconfig reload'
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                plugin.reloadConfig();
                plugin.loadMessagesConfig(); // Re-read our messages framework file as well
                sender.sendMessage(Component.text("LANetwork Teleport system configuration files reloaded successfully!", NamedTextColor.GREEN));
                return true;
            }

            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Core configuration menu dashboards must be viewed in-game.", NamedTextColor.RED));
                return true;
            }

            new ConfigMenu(plugin).openMainMenu(player);
            return true;
        }

        // Handle other console-restricted operations below safely
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only active in-game players can trigger spatial warping matrices.", NamedTextColor.RED));
            return true;
        }

        if (cmd.equals("rtpmenu")) {
            new RtpMenu(plugin).openMenu(player);
            return true;
        }

        if (cmd.equals("rtp")) {
            executeRandomTeleport(player, player.getWorld(), plugin.getConfig().getInt("rtp.max-radius", 5000));
            return true;
        }

        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("teleportconfig") && args.length == 1) {
            return List.of("reload");
        }
        return Collections.emptyList();
    }

    /**
     * MASTER INVENTORY CLICK GUARD
     * Intercepts and completely freezes any attempts to drag, take,
     * or manipulate items inside plugin-owned inventory menus.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inventory = event.getInventory();
        Object holder = inventory.getHolder();

        // Check if the holder is an active instance of our GUI modules
        if (holder instanceof ConfigMenu || holder instanceof RtpMenu) {
            event.setCancelled(true); // Stop all item stealing/moving completely

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            int rawSlot = event.getRawSlot();
            if (rawSlot >= inventory.getSize()) return; // Clicks targeting down inside player inventory

            if (holder instanceof ConfigMenu configHolder) {
                handleConfigMenuClicks(player, configHolder, clicked, rawSlot, event.getClick());
            }
            else if (holder instanceof RtpMenu rtpHolder) {
                handleRtpMenuClicks(player, rtpHolder, event.getClick(), clicked, rawSlot);
            }
        }
    }

    private void handleConfigMenuClicks(Player player, ConfigMenu holder, ItemStack clicked, int slot, ClickType clickType) {
        String screenType = holder.getConfigScreenType();
        FileConfiguration config = plugin.getConfig();

        if (screenType.equals("main")) {
            // FIXED: Synchronized slot alignments to exactly mirror ConfigMenu positions
            if (slot == 19) {
                holder.openTpaMenu(player);
            } else if (slot == 22) {
                holder.openRtpMenu(player);
            } else if (slot == 25) {
                holder.openSoundsMenu(player);
            }
        }
        else if (screenType.equals("tpa")) {
            if (slot == 49) {
                holder.openMainMenu(player);
                return;
            }
            if (slot == 22) { // TPA Timeout item adjustment click hooks
                int currentTimeout = config.getInt("tpa.timeout", 60);
                if (clickType.isLeftClick()) {
                    config.set("tpa.timeout", currentTimeout + 5);
                } else if (clickType.isRightClick()) {
                    config.set("tpa.timeout", Math.max(5, currentTimeout - 5));
                }
                plugin.saveConfig();
                holder.openTpaMenu(player); // Hot-reload item lore instantly
            }
        }
        else if (screenType.equals("rtp")) {
            if (slot == 49) {
                holder.openMainMenu(player);
                return;
            }
            // Optional: You can place your adjustment click logic handlers here for slots 19, 22, 25, 31
            if (slot == 31) {
                new RtpMenu(plugin).openEditorMenu(player);
            }
        }
        else if (screenType.equals("sounds")) {
            if (slot == 49) {
                holder.openMainMenu(player);
                return;
            }
            // Optional: Handle sounds menu configuration triggers for slots 19, 21, 23, 25, 40
        }
    }

    private void handleRtpMenuClicks(Player player, RtpMenu holder, ClickType clickType, ItemStack clicked, int slot) {
        FileConfiguration config = plugin.getConfig();

        if (!holder.isEditorMode()) {
            ConfigurationSection sec = config.getConfigurationSection("gui.items");
            if (sec == null) return;

            for (String key : sec.getKeys(false)) {
                if (config.getInt("gui.items." + key + ".slot") == slot) {
                    String actionType = config.getString("gui.items." + key + ".action-type", "WORLD");
                    String actionTarget = config.getString("gui.items." + key + ".action-target", "world");

                    player.closeInventory();

                    if (actionType.equalsIgnoreCase("WORLD")) {
                        World targetWorld = Bukkit.getWorld(actionTarget);
                        if (targetWorld == null) {
                            player.sendMessage(Component.text("Target world configuration destination could not be validated.", NamedTextColor.RED));
                            return;
                        }
                        executeRandomTeleport(player, targetWorld, config.getInt("rtp.max-radius", 5000));
                    }
                    return;
                }
            }
        } else {
            ConfigurationSection sec = config.getConfigurationSection("gui.items");
            if (sec == null) return;

            String matchedKey = null;
            for (String key : sec.getKeys(false)) {
                if (config.getInt("gui.items." + key + ".slot") == slot) {
                    matchedKey = key;
                    break;
                }
            }

            if (matchedKey == null) return;

            if (clickType == ClickType.LEFT) {
                player.setMetadata("editing_rtp_key", new FixedMetadataValue(plugin, matchedKey));
                player.setMetadata("editing_rtp_type", new FixedMetadataValue(plugin, "editing_rtp_world"));
                player.closeInventory();
                player.sendMessage(Component.text("Type the exact target World Name in chat for this button, or 'cancel' to abort:", NamedTextColor.LIGHT_PURPLE));
            }
            else if (clickType == ClickType.RIGHT) {
                player.setMetadata("editing_rtp_key", new FixedMetadataValue(plugin, matchedKey));
                player.setMetadata("editing_rtp_type", new FixedMetadataValue(plugin, "editing_rtp_material"));
                player.closeInventory();
                player.sendMessage(Component.text("Type a valid Bukkit Material Key (e.g. GRASS_BLOCK) in chat, or 'cancel' to abort:", NamedTextColor.LIGHT_PURPLE));
            }
            else if (clickType == ClickType.SHIFT_LEFT) {
                player.setMetadata("editing_rtp_key", new FixedMetadataValue(plugin, matchedKey));
                player.setMetadata("editing_rtp_type", new FixedMetadataValue(plugin, "editing_rtp_name"));
                player.closeInventory();
                player.sendMessage(Component.text("Type the new Button Display Name in chat (Supports section § codes), or 'cancel' to abort:", NamedTextColor.LIGHT_PURPLE));
            }
            else if (clickType == ClickType.SHIFT_RIGHT) {
                player.setMetadata("editing_rtp_key", new FixedMetadataValue(plugin, matchedKey));
                player.setMetadata("editing_rtp_type", new FixedMetadataValue(plugin, "editing_rtp_slot"));
                player.closeInventory();
                player.sendMessage(Component.text("Type a new Grid Slot Index (0-53) in chat, or 'cancel' to abort:", NamedTextColor.LIGHT_PURPLE));
            }
            else if (clickType == ClickType.MIDDLE || clickType == ClickType.DROP) {
                player.setMetadata("editing_rtp_key", new FixedMetadataValue(plugin, matchedKey));
                player.setMetadata("editing_rtp_type", new FixedMetadataValue(plugin, "editing_rtp_lore"));
                player.closeInventory();
                player.sendMessage(Component.text("Type the new lore rows in chat split with a pipe character '|' (e.g. Row 1 | Row 2), or 'cancel':", NamedTextColor.LIGHT_PURPLE));
            }
        }
    }

    private void executeRandomTeleport(Player player, World world, int maxRadius) {
        FileConfiguration config = plugin.getConfig();
        int minRadius = config.getInt("rtp.min-radius", 1000);
        int maxAttempts = config.getInt("rtp.max-attempts", 15);

        player.sendMessage(MiniMessage.miniMessage().deserialize(config.getString("messages.rtp-searching", "<yellow>Locating safe landing zone...</yellow>")));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (int i = 0; i < maxAttempts; i++) {
                double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
                double radius = minRadius + (ThreadLocalRandom.current().nextDouble() * (maxRadius - minRadius));

                int x = (int) (radius * Math.cos(angle));
                int z = (int) (radius * Math.sin(angle));

                int y = world.getHighestBlockYAt(x, z);
                Location checkLoc = new Location(world, x, y, z);

                if (!checkLoc.getChunk().isLoaded()) {
                    checkLoc.getChunk().load(true);
                }

                Block feet = world.getBlockAt(x, y, z);
                Block head = world.getBlockAt(x, y + 1, z);
                Block ground = world.getBlockAt(x, y - 1, z);

                if (feet.getType().isAir() && head.getType().isAir() && ground.getType().isSolid()) {
                    if (ground.getType() != Material.LAVA && ground.getType() != Material.WATER) {
                        Biome biome = world.getBiome(x, y, z);
                        if (biome != Biome.OCEAN && biome != Biome.DEEP_OCEAN) {

                            Location finalLoc = new Location(world, x + 0.5, y, z + 0.5, player.getLocation().getYaw(), player.getLocation().getPitch());

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                player.teleportAsync(finalLoc).thenAccept(success -> {
                                    if (success) {
                                        plugin.getSoundEngine().playSoundProfile(player, "sounds.teleport");
                                        String successMsg = config.getString("messages.rtp-success", "<green>Warp complete!</green>")
                                                .replace("%x%", String.valueOf(x))
                                                .replace("%y%", String.valueOf(y))
                                                .replace("%z%", String.valueOf(z));
                                        player.sendMessage(MiniMessage.miniMessage().deserialize(successMsg));
                                    }
                                });
                            });
                            return;
                        }
                    }
                }
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(Component.text("Could not find a safe wilderness anchor location after maximum attempts. Please try again.", NamedTextColor.RED));
            });
        });
    }

    @EventHandler
    public void onAsyncPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!player.hasMetadata("editing_rtp_key") || !player.hasMetadata("editing_rtp_type")) return;

        event.setCancelled(true);

        String input = event.getMessage();
        String matchedKey = player.getMetadata("editing_rtp_key").get(0).asString();
        String editType = player.getMetadata("editing_rtp_type").get(0).asString();

        player.removeMetadata("editing_rtp_key", plugin);
        player.removeMetadata("editing_rtp_type", plugin);

        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(Component.text("Modification cycle aborted.", NamedTextColor.RED));
            Bukkit.getScheduler().runTask(plugin, () -> new RtpMenu(plugin).openEditorMenu(player));
            return;
        }

        FileConfiguration config = plugin.getConfig();

        switch (editType) {
            case "editing_rtp_world" -> {
                config.set("gui.items." + matchedKey + ".action-target", input.trim());
                plugin.saveConfig();
                player.sendMessage(Component.text("Successfully re-linked destination world path context.", NamedTextColor.GREEN));
            }
            case "editing_rtp_material" -> {
                String format = input.trim().toLowerCase();
                if (!format.contains(":")) format = "minecraft:" + format;
                config.set("gui.items." + matchedKey + ".material", format);
                plugin.saveConfig();
                player.sendMessage(Component.text("Successfully adjusted visual material icon mapping key.", NamedTextColor.GREEN));
            }
            case "editing_rtp_name" -> {
                config.set("gui.items." + matchedKey + ".name", input.replace("&", "§"));
                plugin.saveConfig();
                player.sendMessage(Component.text("Successfully modified button visibility display name.", NamedTextColor.GREEN));
            }
            case "editing_rtp_slot" -> {
                try {
                    int slotIndex = Integer.parseInt(input);
                    if (slotIndex < 0 || slotIndex > 53) {
                        player.sendMessage(Component.text("Slot bounds must be between 0 and 53. Aborted.", NamedTextColor.RED));
                    } else {
                        config.set("gui.items." + matchedKey + ".slot", slotIndex);
                        plugin.saveConfig();
                        player.sendMessage(Component.text("Successfully reassigned slot layout index to: " + slotIndex, NamedTextColor.GREEN));
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Invalid integer format template. Aborted.", NamedTextColor.RED));
                }
            }
            case "editing_rtp_lore" -> {
                String[] items = input.split("\\|");
                List<String> textRows = new ArrayList<>();
                for (String line : items) {
                    textRows.add(line.trim().replace("&", "§"));
                }
                config.set("gui.items." + matchedKey + ".lore", textRows);
                plugin.saveConfig();
                player.sendMessage(Component.text("Successfully updated custom layout lore rows.", NamedTextColor.GREEN));
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> new RtpMenu(plugin).openEditorMenu(player));
    }
}