package lanetwork.commands;

import lanetwork.teleport.Teleport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class TpaCommand implements CommandExecutor {

    private final Teleport plugin;

    private final HashMap<UUID, TpaRequest> activeRequests = new HashMap<>();
    private final HashSet<UUID> outboundTrackers = new HashSet<>();
    private final HashSet<UUID> disabledTpa = new HashSet<>();
    private final HashSet<UUID> autoAcceptTpa = new HashSet<>();
    private final HashMap<UUID, HashSet<UUID>> ignoreLists = new HashMap<>();
    private final HashMap<UUID, BukkitTask> activeCountdowns = new HashMap<>();

    private boolean debugMode = false;

    private record TpaRequest(UUID senderId, boolean isHereRequest) {}

    public TpaCommand(Teleport plugin) {
        this.plugin = plugin;
    }

    private Component getMessage(String path) {
        String msg = plugin.getMessagesConfig().getString(path, "");
        return parseColorString(msg);
    }

    private Component getMessage(String path, String placeholder, String replacement) {
        String msg = plugin.getMessagesConfig().getString(path, "");
        msg = msg.replace(placeholder, replacement);
        return parseColorString(msg);
    }

    private Component parseColorString(String input) {
        if (input == null) return Component.text("");

        // Convert hex format (&#FFFFFF) to standard MiniMessage syntax (<#FFFFFF>)
        java.util.regex.Pattern hexPattern = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})");
        java.util.regex.Matcher matcher = hexPattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<#" + matcher.group(1) + ">");
        }
        matcher.appendTail(sb);
        String processed = sb.toString();

        // Compatibility fallback layer mapping legacy configurations natively
        processed = processed
                .replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
                .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
                .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
                .replace("&f", "<white>").replace("&l", "<bold>").replace("&m", "<strikethrough>")
                .replace("&n", "<underlined>").replace("&o", "<italic>").replace("&r", "<reset>")
                .replace("§", "&");

        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(processed);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can execute TPA subsystem modules.", NamedTextColor.RED));
            return true;
        }

        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "tpa" -> { return handleTpaRequest(player, args, false, false); }
            case "tpahere" -> { return handleTpaRequest(player, args, true, false); }
            case "tpahereall" -> { return handleTpaHereAll(player); }
            case "tpaccept" -> { handleAcceptDeny(player, true); return true; }
            case "tpadeny" -> { handleAcceptDeny(player, false); return true; }
            case "tpatoggle" -> {
                if (disabledTpa.contains(player.getUniqueId())) {
                    disabledTpa.remove(player.getUniqueId());
                    player.sendMessage(Component.text("TPA requests enabled.", NamedTextColor.GREEN));
                } else {
                    disabledTpa.add(player.getUniqueId());
                    player.sendMessage(Component.text("TPA requests disabled.", NamedTextColor.RED));
                }
                return true;
            }
            case "tpaauto" -> {
                if (autoAcceptTpa.contains(player.getUniqueId())) {
                    autoAcceptTpa.remove(player.getUniqueId());
                    player.sendMessage(Component.text("Auto-Accept disabled.", NamedTextColor.RED));
                } else {
                    autoAcceptTpa.add(player.getUniqueId());
                    player.sendMessage(Component.text("Auto-Accept enabled.", NamedTextColor.GREEN));
                }
                return true;
            }
            case "tpaignore" -> {
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
                player.sendMessage(Component.text("You are now ignoring TPA requests from " + target.getName(), NamedTextColor.GOLD));
                return true;
            }
            case "tpaunignore" -> {
                if (args.length == 0) {
                    player.sendMessage(Component.text("Usage: /tpaunignore <player>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                HashSet<UUID> list = ignoreLists.get(player.getUniqueId());
                if (list != null) {
                    list.remove(target.getUniqueId());
                    player.sendMessage(Component.text("You are no longer ignoring requests from " + target.getName(), NamedTextColor.GREEN));
                }
                return true;
            }
            case "debug" -> {
                if (!player.hasPermission("lanetwork.admin")) {
                    player.sendMessage(getMessage("no-permission"));
                    return true;
                }
                debugMode = !debugMode;
                player.sendMessage(getMessage(debugMode ? "debug.toggle-on" : "debug.toggle-off"));
                return true;
            }
        }
        return true;
    }

    private boolean handleTpaHereAll(Player sender) {
        if (!sender.hasPermission("lanetwork.tpahereall")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (outboundTrackers.contains(sender.getUniqueId())) {
            sender.sendMessage(getMessage("tpa.spam-guard"));
            return true;
        }

        int targetCount = 0;

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().equals(sender.getUniqueId())) continue;
            if (!sender.canSee(target) && !debugMode) continue;
            if (disabledTpa.contains(target.getUniqueId()) && !debugMode) continue;

            HashSet<UUID> targetIgnoreList = ignoreLists.get(target.getUniqueId());
            if (targetIgnoreList != null && targetIgnoreList.contains(sender.getUniqueId()) && !debugMode) continue;

            String[] targetArgs = new String[]{target.getName()};
            handleTpaRequest(sender, targetArgs, true, true);
            targetCount++;
        }

        if (targetCount > 0) {
            outboundTrackers.add(sender.getUniqueId());
            sender.sendMessage(Component.text("Sent a mass tpahere request to all " + targetCount + " online players!", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("No valid active players found to receive your request.", NamedTextColor.RED));
        }

        return true;
    }

    private boolean handleTpaRequest(Player sender, String[] args, boolean isHereRequest, boolean bypassBlocker) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + (isHereRequest ? "tpahere" : "tpa") + " <player>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || (!sender.canSee(target) && !debugMode)) {
            if (!bypassBlocker) sender.sendMessage(getMessage("tpa.player-not-found"));
            return true;
        }

        if (sender.getUniqueId().equals(target.getUniqueId()) && !debugMode) {
            if (!bypassBlocker) sender.sendMessage(getMessage("tpa.cannot-request-self"));
            return true;
        }

        if (!bypassBlocker && outboundTrackers.contains(sender.getUniqueId())) {
            sender.sendMessage(getMessage("tpa.spam-guard"));
            return true;
        }

        if (disabledTpa.contains(target.getUniqueId()) && !debugMode) {
            if (!bypassBlocker) sender.sendMessage(getMessage("tpa.disabled-target", "%target%", target.getName()));
            return true;
        }

        activeRequests.put(target.getUniqueId(), new TpaRequest(sender.getUniqueId(), isHereRequest));

        if (!bypassBlocker) {
            outboundTrackers.add(sender.getUniqueId());
            sender.sendMessage(getMessage("tpa.sent-confirmation", "%target%", target.getName()));
        }

        plugin.getSoundEngine().playSoundProfile(sender, "sounds.send-request");

        if (autoAcceptTpa.contains(target.getUniqueId())) {
            handleAcceptDeny(target, true);
            return true;
        }

        Component baseHeader = isHereRequest ?
                getMessage("tpa.incoming-request-here", "%sender%", sender.getName()) :
                getMessage("tpa.incoming-request-to", "%sender%", sender.getName());

        Component acceptBtn = getMessage("tpa.button-accept")
                .hoverEvent(HoverEvent.showText(getMessage("tpa.button-accept-hover")))
                .clickEvent(ClickEvent.runCommand("/tpaccept"));

        Component denyBtn = getMessage("tpa.button-deny")
                .hoverEvent(HoverEvent.showText(getMessage("tpa.button-deny-hover")))
                .clickEvent(ClickEvent.runCommand("/tpadeny"));

        target.sendMessage(baseHeader);
        target.sendMessage(Component.text("   ").append(acceptBtn).append(Component.text("   ")).append(denyBtn));
        target.sendMessage(getMessage("tpa.request-footer"));

        plugin.getSoundEngine().playSoundProfile(target, "sounds.receive-request");

        int expiryDuration = plugin.getConfig().getInt("tpa.timeout", 60);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TpaRequest currentReq = activeRequests.get(target.getUniqueId());
            if (currentReq != null && currentReq.senderId().equals(sender.getUniqueId())) {
                activeRequests.remove(target.getUniqueId());
                outboundTrackers.remove(sender.getUniqueId());
                sender.sendMessage(getMessage("tpa.request-expired-sender", "%target%", target.getName()));
                target.sendMessage(getMessage("tpa.request-expired-receiver", "%sender%", sender.getName()));
            }
        }, expiryDuration * 20L);

        return true;
    }

    private void handleAcceptDeny(Player target, boolean accept) {
        TpaRequest req = activeRequests.remove(target.getUniqueId());
        if (req == null) {
            target.sendMessage(Component.text("You do not have any pending incoming teleport requests.", NamedTextColor.RED));
            return;
        }

        Player sender = Bukkit.getPlayer(req.senderId());
        outboundTrackers.remove(req.senderId());

        if (sender == null) {
            target.sendMessage(Component.text("The player who sent this request is no longer online.", NamedTextColor.RED));
            return;
        }

        if (!accept) {
            sender.sendMessage(getMessage("tpa.sender-denied", "%target%", target.getName()));
            target.sendMessage(getMessage("tpa.receiver-denied"));
            plugin.getSoundEngine().playSoundProfile(target, "sounds.deny");
            plugin.getSoundEngine().playSoundProfile(sender, "sounds.deny");
            return;
        }

        Player traveller = req.isHereRequest() ? target : sender;
        Player destination = req.isHereRequest() ? sender : target;

        sender.sendMessage(getMessage("tpa.sender-accepted", "%target%", target.getName()));
        target.sendMessage(getMessage("tpa.receiver-accepted"));

        startTeleportCountdown(traveller, destination);
    }

    private void startTeleportCountdown(Player traveller, Player destination) {
        if (activeCountdowns.containsKey(traveller.getUniqueId())) {
            activeCountdowns.get(traveller.getUniqueId()).cancel();
        }

        final Location startLocTraveller = traveller.getLocation().getBlock().getLocation();
        traveller.playSound(traveller.getLocation(), Sound.ENTITY_CREEPER_PRIMED, SoundCategory.MASTER, 1.0f, 0.5f);

        BukkitTask task = new BukkitRunnable() {
            int secondsRemaining = 3;

            @Override
            public void run() {
                if (!traveller.isOnline() || !destination.isOnline()) {
                    cancelCountdown(traveller.getUniqueId(), null);
                    this.cancel();
                    return;
                }

                Location currentLocTraveller = traveller.getLocation().getBlock().getLocation();

                if (!currentLocTraveller.equals(startLocTraveller)) {
                    cancelCountdown(traveller.getUniqueId(), traveller);
                    destination.sendMessage(getMessage("tpa.countdown-traveller-moved"));
                    this.cancel();
                    return;
                }

                if (secondsRemaining > 0) {
                    Component titleText = getMessage("tpa.countdown-title", "%seconds%", String.valueOf(secondsRemaining));
                    Component subtitleText = getMessage("tpa.countdown-subtitle");
                    Title title = Title.title(titleText, subtitleText, Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO));

                    traveller.showTitle(title);
                    traveller.playSound(traveller.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 0.6f, 1.0f);

                    secondsRemaining--;
                } else {
                    Title endTitle = Title.title(getMessage("tpa.warped-title"), Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(250)));
                    traveller.showTitle(endTitle);

                    traveller.teleportAsync(destination.getLocation()).thenAccept(success -> {
                        if (success) {
                            plugin.getSoundEngine().playSoundProfile(traveller, "sounds.teleport");
                            if (!traveller.getUniqueId().equals(destination.getUniqueId())) {
                                plugin.getSoundEngine().playSoundProfile(destination, "sounds.teleport");
                            }
                        }
                    });

                    activeCountdowns.remove(traveller.getUniqueId());
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        activeCountdowns.put(traveller.getUniqueId(), task);
    }

    private void cancelCountdown(UUID travellerId, Player traveller) {
        activeCountdowns.remove(travellerId);
        if (traveller != null) {
            traveller.sendMessage(getMessage("tpa.countdown-cancelled"));
            traveller.playSound(traveller.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.MASTER, 0.8f, 0.5f);
            traveller.clearTitle();
        }
    }
}