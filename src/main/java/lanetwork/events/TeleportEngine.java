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
import org.bukkit.Sound;
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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class TeleportEngine implements CommandExecutor, TabCompleter, Listener {

    private final Teleport plugin;

    public TeleportEngine(Teleport plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Console cannot utilize execution teleports.", NamedTextColor.RED));
            return true;
        }

        String cmd = command.getName().toLowerCase();
        FileConfiguration config = plugin.getConfig();
        MiniMessage mm = MiniMessage.miniMessage();
        String prefix = config.getString("messages.prefix", "");

        if (cmd.equals("teleportconfig")) {
            if (!player.hasPermission("lanetwork.admin")) {
                player.sendMessage(mm.deserialize(prefix + config.getString("messages.no-permission")));
                return true;
            }
            if (args.length == 0) {
                new ConfigMenu(plugin).openMainMenu(player);
                return true;
            }
            if (args[0].equalsIgnoreCase("reload")) {
                plugin.reloadConfig();
                player.sendMessage(Component.text("Configuration file updated successfully live!", NamedTextColor.GREEN));
                return true;
            }
            player.sendMessage(Component.text("Usage: /teleportconfig [reload]", NamedTextColor.YELLOW));
            return true;
        }

        if (cmd.equals("rtpmenu")) {
            new RtpMenu(plugin).openMenu(player);
            return true;
        }

        if (cmd.equals("rtp")) {
            World targetWorld = player.getWorld();
            int maxRadius = config.getInt("rtp.max-radius", 5000);

            if (args.length > 0) {
                World parsedWorld = Bukkit.getWorld(args[0]);
                if (parsedWorld != null) {
                    targetWorld = parsedWorld;
                    if (args.length > 1) {
                        try { maxRadius = Integer.parseInt(args[1]); } catch (NumberFormatException e) {
                            player.sendMessage(Component.text("Invalid radius format. Using default.", NamedTextColor.YELLOW));
                        }
                    }
                } else {
                    try { maxRadius = Integer.parseInt(args[0]); } catch (NumberFormatException e) {
                        new RtpMenu(plugin).openMenu(player);
                        return true;
                    }
                }
            }

            executeRandomTeleport(player, targetWorld, null, maxRadius);
            return true;
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }

    private void executeRandomTeleport(Player player, World world, Biome preferredBiome, int maxRadius) {
        if (world == null) {
            player.sendMessage(Component.text("Target world environment configuration unavailable.", NamedTextColor.RED));
            return;
        }

        FileConfiguration config = plugin.getConfig();
        MiniMessage mm = MiniMessage.miniMessage();
        String prefix = config.getString("messages.prefix", "");

        player.sendMessage(mm.deserialize(prefix + config.getString("messages.rtp-searching")));

        int min = config.getInt("rtp.min-radius", 1000);
        int maxAttempts = config.getInt("rtp.max-attempts", 15);
        final int finalMaxRadius = Math.max(min + 100, maxRadius);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final List<int[]> coordinatesList = new ArrayList<>();
            for (int i = 0; i < maxAttempts; i++) {
                int x = ThreadLocalRandom.current().nextInt(min, finalMaxRadius) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
                int z = ThreadLocalRandom.current().nextInt(min, finalMaxRadius) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
                coordinatesList.add(new int[]{x, z});
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                Location safeLoc = null;

                for (int[] coords : coordinatesList) {
                    int x = coords[0];
                    int z = coords[1];

                    int y = world.getHighestBlockYAt(x, z);
                    Location checkLoc = new Location(world, x, y, z);

                    if (preferredBiome != null && world.getBiome(checkLoc) != preferredBiome) {
                        continue;
                    }

                    Block block = checkLoc.getBlock();
                    Block below = block.getRelative(0, -1, 0);

                    if (below.getType() != Material.LAVA && below.getType() != Material.WATER && below.getType() != Material.AIR) {
                        safeLoc = checkLoc.add(0.5, 1.0, 0.5);
                        break;
                    }
                }

                if (safeLoc != null) {
                    final Location finalLoc = safeLoc;
                    player.teleportAsync(finalLoc).thenAccept(success -> {
                        if (!success) return;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            String successMsg = config.getString("messages.rtp-success", "")
                                    .replace("%x%", String.valueOf(finalLoc.getBlockX()))
                                    .replace("%y%", String.valueOf(finalLoc.getBlockY()))
                                    .replace("%z%", String.valueOf(finalLoc.getBlockZ()));
                            player.sendMessage(mm.deserialize(prefix + successMsg));
                            plugin.getSoundEngine().playSoundProfile(player, "sounds.teleport");
                        });
                    });
                } else {
                    player.sendMessage(Component.text("Could not secure safe zone boundaries. Try again.", NamedTextColor.RED));
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory == null) return;

        // Secure type-safe custom inventory container verification
        InventoryHolder holder = topInventory.getHolder();
        if (holder == null) return;

        // 1. EVALUATE RTP USER OR EDITOR SELECTION PANEL
        if (holder instanceof RtpMenu rtpMenu) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player player)) return;
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

            int clickedSlot = event.getRawSlot();
            FileConfiguration config = plugin.getConfig();

            if (!rtpMenu.isEditorMode()) {
                ConfigurationSection section = config.getConfigurationSection("gui.items");
                if (section == null) return;

                for (String key : section.getKeys(false)) {
                    if (clickedSlot == section.getInt(key + ".slot", -1)) {
                        player.closeInventory();
                        String actionType = section.getString(key + ".action-type", "WORLD").toUpperCase();
                        String targetTarget = section.getString(key + ".action-target", "world");
                        int defaultMax = config.getInt("rtp.max-radius", 5000);

                        if (actionType.equals("WORLD")) {
                            executeRandomTeleport(player, Bukkit.getWorld(targetTarget), null, defaultMax);
                        } else if (actionType.equals("BIOME")) {
                            try {
                                Biome biomeTarget = Biome.valueOf(targetTarget.toUpperCase());
                                executeRandomTeleport(player, player.getWorld(), biomeTarget, defaultMax);
                            } catch (IllegalArgumentException ex) {
                                player.sendMessage(Component.text("Invalid configuration: Biome mismatch profile error.", NamedTextColor.RED));
                            }
                        }
                        return;
                    }
                }
                return;
            }

            if (rtpMenu.isEditorMode()) {
                if (clicked.getType() == Material.BARRIER) {
                    new ConfigMenu(plugin).openRtpMenu(player);
                    return;
                }

                ConfigurationSection section = config.getConfigurationSection("gui.items");
                if (section == null) return;

                for (String key : section.getKeys(false)) {
                    if (clickedSlot == section.getInt(key + ".slot", -1)) {
                        player.closeInventory();
                        ClickType click = event.getClick();

                        if (click == ClickType.SHIFT_LEFT) {
                            player.setMetadata("editing_rtp_name", new FixedMetadataValue(plugin, key));
                            player.sendMessage(Component.text("\n[RTP Editor] Enter the new DISPLAY NAME in chat.", NamedTextColor.GREEN));
                        } else if (click == ClickType.SHIFT_RIGHT) {
                            player.setMetadata("editing_rtp_slot", new FixedMetadataValue(plugin, key));
                            player.sendMessage(Component.text("\n[RTP Editor] Enter the raw inventory target SLOT integer (0 to 53) in chat.", NamedTextColor.GOLD));
                        } else if (click == ClickType.MIDDLE || click == ClickType.DROP) {
                            player.setMetadata("editing_rtp_lore", new FixedMetadataValue(plugin, key));
                            player.sendMessage(Component.text("\n[RTP Editor] Enter LORE text rows in chat. Split lines using '|'.", NamedTextColor.LIGHT_PURPLE));
                        } else if (click == ClickType.LEFT) {
                            player.setMetadata("editing_rtp_world", new FixedMetadataValue(plugin, key));
                            player.sendMessage(Component.text("\n[RTP Editor] Please type the exact name of the target WORLD in chat.", NamedTextColor.YELLOW));
                        } else if (click == ClickType.RIGHT) {
                            player.setMetadata("editing_rtp_material", new FixedMetadataValue(plugin, key));
                            player.sendMessage(Component.text("\n[RTP Editor] Please type the namespaced item identifier in chat.", NamedTextColor.AQUA));
                        }
                        return;
                    }
                }
            }
            return;
        }

        // 2. EVALUATE ADMINISTRATIVE DASHBOARD SCREENS
        if (holder instanceof ConfigMenu configMenu) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player player)) return;
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

            String screenType = configMenu.getConfigScreenType();
            ConfigMenu menu = new ConfigMenu(plugin);
            FileConfiguration config = plugin.getConfig();
            boolean isLeftClick = event.getClick().isLeftClick();

            if (screenType.equals("main")) {
                if (clicked.getType() == Material.CLOCK) menu.openTpaMenu(player);
                else if (clicked.getType() == Material.COMPASS) menu.openRtpMenu(player);
                else if (clicked.getType() == Material.JUKEBOX) menu.openSoundsMenu(player);
                return;
            }

            if (clicked.getType() == Material.BARRIER) {
                menu.openMainMenu(player);
                return;
            }

            if (screenType.equals("tpa")) {
                if (clicked.getType() == Material.OAK_SIGN) {
                    int timeout = config.getInt("tpa.timeout", 60);
                    timeout += isLeftClick ? 5 : -5;
                    if (timeout < 5) timeout = 5;
                    config.set("tpa.timeout", timeout);
                    plugin.saveConfig();
                    menu.openTpaMenu(player);
                }
                return;
            }

            if (screenType.equals("sounds")) {
                if (clicked.getType() == Material.WRITABLE_BOOK) {
                    player.closeInventory();
                    player.sendMessage(Component.text("\n=============================================", NamedTextColor.GRAY));
                    player.sendMessage(Component.text("Bukkit Javadocs Sound Identifier Directory Link:", NamedTextColor.GOLD));
                    Component wikiLink = MiniMessage.miniMessage().deserialize("<green><underlined><bold>🔗 CLICK HERE FOR BUKKIT SOUNDS LIST 🔗</bold></underlined></green>")
                            .clickEvent(ClickEvent.openUrl("https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html"));
                    player.sendMessage(wikiLink);
                    player.sendMessage(Component.text("=============================================", NamedTextColor.GRAY));
                    return;
                }

                switch (clicked.getType()) {
                    case GOLDEN_APPLE -> {
                        player.closeInventory();
                        player.setMetadata("editing_sound_path", new FixedMetadataValue(plugin, "sounds.send-request"));
                        player.sendMessage(Component.text("\n[Sound Profile Editor] Enter name for SENDING REQUESTS:", NamedTextColor.GOLD));
                    }
                    case ENCHANTED_GOLDEN_APPLE -> {
                        player.closeInventory();
                        player.setMetadata("editing_sound_path", new FixedMetadataValue(plugin, "sounds.receive-request"));
                        player.sendMessage(Component.text("\n[Sound Profile Editor] Enter name for RECEIVING REQUESTS:", NamedTextColor.GOLD));
                    }
                    case ENDER_PEARL -> {
                        player.closeInventory();
                        player.setMetadata("editing_sound_path", new FixedMetadataValue(plugin, "sounds.teleport"));
                        player.sendMessage(Component.text("\n[Sound Profile Editor] Enter name for TELEPORTATION:", NamedTextColor.GOLD));
                    }
                    case ANVIL -> {
                        player.closeInventory();
                        player.setMetadata("editing_sound_path", new FixedMetadataValue(plugin, "sounds.deny"));
                        player.sendMessage(Component.text("\n[Sound Profile Editor] Enter name for CANCELLING/DENYING:", NamedTextColor.GOLD));
                    }
                    default -> {}
                }
                return;
            }

            if (screenType.equals("rtp")) {
                if (clicked.getType() == Material.NETHER_STAR) {
                    new RtpMenu(plugin).openEditorMenu(player);
                    return;
                }

                switch (clicked.getType()) {
                    case MAP -> {
                        int min = config.getInt("rtp.min-radius", 1000);
                        min += isLeftClick ? 250 : -250;
                        if (min < 0) min = 0;
                        config.set("rtp.min-radius", min);
                    }
                    case ENDER_PEARL -> {
                        int max = config.getInt("rtp.max-radius", 5000);
                        max += isLeftClick ? 500 : -500;
                        int minBound = config.getInt("rtp.min-radius", 1000);
                        if (max <= minBound) max = minBound + 500;
                        config.set("rtp.max-radius", max);
                    }
                    case ANVIL -> {
                        int attempts = config.getInt("rtp.max-attempts", 15);
                        attempts += isLeftClick ? 1 : -1;
                        if (attempts < 1) attempts = 1;
                        config.set("rtp.max-attempts", attempts);
                    }
                    default -> {}
                }
                plugin.saveConfig();
                menu.openRtpMenu(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChatPrompt(io.papermc.paper.event.player.AsyncChatEvent event) {
        Player player = event.getPlayer();
        String matchedKey = null;
        String fieldType = null;

        String[] metadataFields = {
                "editing_rtp_world", "editing_rtp_material", "editing_rtp_name",
                "editing_rtp_slot", "editing_rtp_lore", "editing_sound_path"
        };
        for (String field : metadataFields) {
            if (player.hasMetadata(field)) {
                matchedKey = player.getMetadata(field).get(0).asString();
                fieldType = field;
                player.removeMetadata(field, plugin);
                break;
            }
        }

        if (fieldType == null) return;
        event.setCancelled(true);

        String input = MiniMessage.miniMessage().serialize(event.message()).trim();

        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(Component.text("Configuration sequence cancelled.", NamedTextColor.RED));
            if (fieldType.equals("editing_sound_path")) {
                Bukkit.getScheduler().runTask(plugin, () -> new ConfigMenu(plugin).openSoundsMenu(player));
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> new RtpMenu(plugin).openEditorMenu(player));
            }
            return;
        }

        FileConfiguration config = plugin.getConfig();

        switch (fieldType) {
            case "editing_sound_path" -> {
                String soundInput = input.toUpperCase().replace("§", "");
                config.set(matchedKey, soundInput);
                plugin.saveConfig();
                player.sendMessage(Component.text("Sound setting updated to: " + soundInput, NamedTextColor.GREEN));
                Bukkit.getScheduler().runTask(plugin, () -> new ConfigMenu(plugin).openSoundsMenu(player));
                return;
            }
            case "editing_rtp_world" -> {
                config.set("gui.items." + matchedKey + ".action-target", input);
                plugin.saveConfig();
                player.sendMessage(Component.text("Successfully bound target world to: " + input, NamedTextColor.GREEN));
            }
            case "editing_rtp_material" -> {
                String formatInput = input.contains(":") ? input.toLowerCase() : "minecraft:" + input.toLowerCase();
                NamespacedKey namespacedKey = NamespacedKey.fromString(formatInput);
                Material mat = (namespacedKey != null) ? Registry.MATERIAL.get(namespacedKey) : null;
                if (mat == null || mat.isAir()) {
                    player.sendMessage(Component.text("Error: '" + input + "' is not a valid Minecraft item. Aborted.", NamedTextColor.RED));
                } else {
                    config.set("gui.items." + matchedKey + ".material", formatInput);
                    plugin.saveConfig();
                    player.sendMessage(Component.text("Updated material identifier to: " + formatInput, NamedTextColor.GREEN));
                }
            }
            case "editing_rtp_name" -> {
                String formattedName = input.replace("&", "§");
                config.set("gui.items." + matchedKey + ".name", formattedName);
                plugin.saveConfig();
                player.sendMessage(Component.text("Updated title name to: " + formattedName, NamedTextColor.GREEN));
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
