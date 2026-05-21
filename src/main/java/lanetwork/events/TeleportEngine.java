package lanetwork.events;

import lanetwork.gui.ConfigMenu;
import lanetwork.gui.RtpMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class TeleportEngine implements CommandExecutor, Listener {

    private final JavaPlugin plugin;
    private final HashMap<UUID, TpaRequest> activeRequests = new HashMap<>();
    private final HashSet<UUID> disabledTpa = new HashSet<>();
    private final HashMap<UUID, HashSet<UUID>> ignoreLists = new HashMap<>();

    private record TpaRequest(UUID senderId, boolean isHereRequest) {}

    public TeleportEngine(JavaPlugin plugin) {
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

        if (cmd.equals("tpatoggle")) {
            if (disabledTpa.contains(player.getUniqueId())) {
                disabledTpa.remove(player.getUniqueId());
                player.sendMessage(Component.text("TPA requests enabled.", NamedTextColor.GREEN));
            } else {
                disabledTpa.add(player.getUniqueId());
                player.sendMessage(Component.text("TPA requests disabled.", NamedTextColor.RED));
            }
            return true;
        }

        if (cmd.equals("tpaignore")) {
            if (args.length == 0) {
                player.sendMessage(Component.text("Usage: /tpaignore <player>", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
            ignoreLists.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(target.getUniqueId());
            player.sendMessage(Component.text("Ignoring requests from " + target.getName() + ".", NamedTextColor.GREEN));
            return true;
        }

        if (cmd.equals("tpaunignore")) {
            if (args.length == 0) {
                player.sendMessage(Component.text("Usage: /tpaunignore <player>", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
            if (ignoreLists.containsKey(player.getUniqueId())) {
                ignoreLists.get(player.getUniqueId()).remove(target.getUniqueId());
            }
            player.sendMessage(Component.text("You unignored " + target.getName() + ".", NamedTextColor.GREEN));
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

        if (cmd.equals("tpaccept") || (cmd.equals("tpa") && args.length > 0 && args[0].equalsIgnoreCase("accept"))) {
            handleResolve(player, true);
            return true;
        }

        if (cmd.equals("tpadeny") || (cmd.equals("tpa") && args.length > 0 && args[0].equalsIgnoreCase("deny"))) {
            handleResolve(player, false);
            return true;
        }

        if (cmd.equals("tpahereall")) {
            int sentCount = 0;
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.getUniqueId().equals(player.getUniqueId())) continue;
                if (sendRequest(player, onlinePlayer, true)) sentCount++;
            }
            player.sendMessage(Component.text("Sent a /tpahere request to all " + sentCount + " online players.", NamedTextColor.GREEN));
            return true;
        }

        if (cmd.equals("tpa") || cmd.equals("tpahere")) {
            if (args.length == 0) {
                player.sendMessage(Component.text("Usage: /" + cmd + " <player>", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(Component.text("Player not found or offline.", NamedTextColor.RED));
                return true;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(Component.text("You cannot request yourself!", NamedTextColor.RED));
                return true;
            }
            sendRequest(player, target, cmd.equals("tpahere"));
            return true;
        }

        return false;
    }

    private boolean sendRequest(Player sender, Player target, boolean isHereRequest) {
        if (disabledTpa.contains(target.getUniqueId())) {
            sender.sendMessage(Component.text(target.getName() + " has TPA requests disabled.", NamedTextColor.RED));
            return false;
        }
        if (ignoreLists.containsKey(target.getUniqueId()) && ignoreLists.get(target.getUniqueId()).contains(sender.getUniqueId())) {
            sender.sendMessage(Component.text("You cannot send requests to this player.", NamedTextColor.RED));
            return false;
        }

        activeRequests.put(target.getUniqueId(), new TpaRequest(sender.getUniqueId(), isHereRequest));
        sender.sendMessage(Component.text("Request sent to " + target.getName() + ".", NamedTextColor.GREEN));

        String requestString = isHereRequest ? " wants you to teleport to them!\n" : " wants to teleport to you!\n";
        Component message = Component.text()
                .append(Component.text(sender.getName(), NamedTextColor.GOLD))
                .append(Component.text(requestString, NamedTextColor.YELLOW))
                .append(Component.text("[ACCEPT] ", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/tpaccept"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to accept", NamedTextColor.GRAY))))
                .append(Component.text("   "))
                .append(Component.text("[DENY]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/tpadeny"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to deny", NamedTextColor.GRAY))))
                .build();

        target.sendMessage(message);

        long timeoutTicks = plugin.getConfig().getLong("tpa.timeout", 60L) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TpaRequest current = activeRequests.get(target.getUniqueId());
            if (current != null && current.senderId().equals(sender.getUniqueId())) {
                activeRequests.remove(target.getUniqueId());
                sender.sendMessage(Component.text("Your request to " + target.getName() + " has expired.", NamedTextColor.RED));
                target.sendMessage(Component.text("Teleport request from " + sender.getName() + " has expired.", NamedTextColor.RED));
            }
        }, timeoutTicks);

        return true;
    }

    private void handleResolve(Player target, boolean accept) {
        TpaRequest req = activeRequests.remove(target.getUniqueId());
        if (req == null) {
            target.sendMessage(Component.text("You have no pending requests.", NamedTextColor.RED));
            return;
        }

        Player sender = Bukkit.getPlayer(req.senderId());
        if (sender == null || !sender.isOnline()) {
            target.sendMessage(Component.text("The player associated with this request is offline.", NamedTextColor.RED));
            return;
        }

        if (!accept) {
            sender.sendMessage(Component.text(target.getName() + " denied your request.", NamedTextColor.RED));
            target.sendMessage(Component.text("Request denied successfully.", NamedTextColor.YELLOW));
            return;
        }

        Player entityToMove = req.isHereRequest() ? target : sender;
        Player destination = req.isHereRequest() ? sender : target;

        entityToMove.teleportAsync(destination.getLocation()).thenAccept(success -> {
            if (success) {
                entityToMove.sendMessage(Component.text("Teleporting...", NamedTextColor.GREEN));
                destination.sendMessage(Component.text(entityToMove.getName() + " has been teleported to you.", NamedTextColor.GREEN));
            } else {
                target.sendMessage(Component.text("Teleport sequence failed.", NamedTextColor.RED));
                sender.sendMessage(Component.text("Teleport sequence failed.", NamedTextColor.RED));
            }
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        FileConfiguration config = plugin.getConfig();
        String titleStr = config.getString("gui.title", RtpMenu.DEFAULT_TITLE);

        if (!event.getView().getTitle().equals(titleStr)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int clickedSlot = event.getRawSlot();
        ConfigurationSection section = config.getConfigurationSection("gui.items");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            int targetSlot = section.getInt(key + ".slot", -1);
            if (clickedSlot == targetSlot) {
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
    }

    // --- NEW TEXT CHAT PROMPT LAYOUT BUTTON ENGINE LISTENER ---
    @EventHandler
    public void onLayoutEditorClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(RtpMenu.EDIT_TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int clickedSlot = event.getRawSlot();

        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("gui.items");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            int targetSlot = section.getInt(key + ".slot", -1);
            if (clickedSlot == targetSlot) {
                player.closeInventory();

                boolean editingWorld = event.getClick().isLeftClick();

                if (editingWorld) {
                    player.setMetadata("editing_rtp_world", new FixedMetadataValue(plugin, key));
                    player.sendMessage(Component.text("\n[RTP Editor] Please type the exact name of the target WORLD in chat to confirm.", NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("Example: world_nether or srv_world (Type 'cancel' to exit)", NamedTextColor.GRAY));
                } else {
                    player.setMetadata("editing_rtp_material", new FixedMetadataValue(plugin, key));
                    player.sendMessage(Component.text("\n[RTP Editor] Please type the official MATERIAL enum identifier in chat to confirm.", NamedTextColor.AQUA));
                    player.sendMessage(Component.text("Example: DIAMOND_BLOCK or GRASS_BLOCK (Type 'cancel' to exit)", NamedTextColor.GRAY));
                }
                return;
            }
        }
    }

    // --- ASYNC CHAT INTERCEPTOR FOR GUI CHANGES ---
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChatPrompt(AsyncChatEvent event) {
        Player player = event.getPlayer();
        boolean clearWorld = player.hasMetadata("editing_rtp_world");
        boolean clearMat = player.hasMetadata("editing_rtp_material");

        if (!clearWorld && !clearMat) return;

        event.setCancelled(true); // Stop message from leaking into public game channels

        String input = MiniMessage.miniMessage().serialize(event.message()).trim();

        if (input.equalsIgnoreCase("cancel")) {
            player.removeMetadata("editing_rtp_world", plugin);
            player.removeMetadata("editing_rtp_material", plugin);
            player.sendMessage(Component.text("Configuration sequence cancelled.", NamedTextColor.RED));
            Bukkit.getScheduler().runTask(plugin, () -> new RtpMenu(plugin).openEditorMenu(player));
            return;
        }

        FileConfiguration config = plugin.getConfig();

        if (clearWorld) {
            String buttonKey = player.getMetadata("editing_rtp_world").get(0).asString();
            player.removeMetadata("editing_rtp_world", plugin);

            config.set("gui.items." + buttonKey + ".action-target", input);
            config.set("gui.items." + buttonKey + ".action-type", "WORLD");
            plugin.saveConfig();

            player.sendMessage(Component.text("Successfully bound target world for '" + buttonKey + "' to: " + input, NamedTextColor.GREEN));
        } else {
            String buttonKey = player.getMetadata("editing_rtp_material").get(0).asString();
            player.removeMetadata("editing_rtp_material", plugin);

            Material mat = Material.matchMaterial(input.toUpperCase());
            if (mat == null) {
                player.sendMessage(Component.text("Error: '" + input + "' is not a valid Minecraft material enum key. Task aborted.", NamedTextColor.RED));
            } else {
                config.set("gui.items." + buttonKey + ".material", mat.name());
                plugin.saveConfig();
                player.sendMessage(Component.text("Successfully updated material icon for '" + buttonKey + "' to: " + mat.name(), NamedTextColor.GREEN));
            }
        }

        // Return player cleanly right back into the visual Menu frame inside the main server thread safely
        Bukkit.getScheduler().runTask(plugin, () -> new RtpMenu(plugin).openEditorMenu(player));
    }

    @EventHandler
    public void onAdminConfigClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(ConfigMenu.MAIN_TITLE) && !title.equals(ConfigMenu.TPA_TITLE) && !title.equals(ConfigMenu.RTP_TITLE)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        FileConfiguration config = plugin.getConfig();
        ConfigMenu menu = new ConfigMenu(plugin);
        boolean isLeftClick = event.getClick().isLeftClick();

        if (title.equals(ConfigMenu.MAIN_TITLE)) {
            if (clicked.getType() == Material.CLOCK) menu.openTpaMenu(player);
            else if (clicked.getType() == Material.COMPASS) menu.openRtpMenu(player);
            return;
        }

        if (clicked.getType() == Material.BARRIER) {
            menu.openMainMenu(player);
            return;
        }

        if (title.equals(ConfigMenu.TPA_TITLE)) {
            if (clicked.getType() == Material.OAK_SIGN) {
                int timeout = config.getInt("tpa.timeout", 60);
                timeout += isLeftClick ? 5 : -5;
                if (timeout < 5) timeout = 5;
                config.set("tpa.timeout", timeout);
            }
            plugin.saveConfig();
            menu.openTpaMenu(player);
            return;
        }

        if (title.equals(ConfigMenu.RTP_TITLE)) {
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
                        String successMsg = config.getString("messages.rtp-success", "")
                                .replace("%x%", String.valueOf(finalLoc.getBlockX()))
                                .replace("%y%", String.valueOf(finalLoc.getBlockY()))
                                .replace("%z%", String.valueOf(finalLoc.getBlockZ()));
                        player.sendMessage(mm.deserialize(prefix + successMsg));
                    });
                } else {
                    player.sendMessage(Component.text("Could not secure safe zone boundaries. Try again.", NamedTextColor.RED));
                }
            });
        });
    }
}