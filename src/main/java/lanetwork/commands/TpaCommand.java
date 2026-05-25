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

    // Active task map to handle movement cancel drops
    private final HashMap<UUID, BukkitTask> activeCountdowns = new HashMap<>();

    private boolean debugMode = false;

    private record TpaRequest(UUID senderId, boolean isHereRequest) {}

    public TpaCommand(Teleport plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can execute TPA subsystem modules.", NamedTextColor.RED));
            return true;
        }

        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "tpa" -> { return handleTpaRequest(player, args, false); }
            case "tpahere" -> { return handleTpaRequest(player, args, true); }
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
                    player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                debugMode = !debugMode;
                player.sendMessage(Component.text("Debug override flag: " + debugMode, NamedTextColor.LIGHT_PURPLE));
                return true;
            }
        }
        return true;
    }

    private boolean handleTpaRequest(Player sender, String[] args, boolean isHereRequest) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + (isHereRequest ? "tpahere" : "tpa") + " <player>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || (!sender.canSee(target) && !debugMode)) {
            sender.sendMessage(Component.text("Player not found online.", NamedTextColor.RED));
            return true;
        }

        if (sender.getUniqueId().equals(target.getUniqueId()) && !debugMode) {
            sender.sendMessage(Component.text("You cannot send a teleport request to yourself.", NamedTextColor.RED));
            return true;
        }

        // SPAM GUARD: Prevent player from sending multiple ongoing active requests
        if (outboundTrackers.contains(sender.getUniqueId())) {
            sender.sendMessage(Component.text("You already have an active outbound request pending! Please wait until it finishes or expires.", NamedTextColor.RED));
            return true;
        }

        if (disabledTpa.contains(target.getUniqueId()) && !debugMode) {
            sender.sendMessage(Component.text(target.getName() + " has disabled incoming teleport requests.", NamedTextColor.RED));
            return true;
        }

        HashSet<UUID> targetIgnoreList = ignoreLists.get(target.getUniqueId());
        if (targetIgnoreList != null && targetIgnoreList.contains(sender.getUniqueId()) && !debugMode) {
            sender.sendMessage(Component.text("You cannot send requests to this player right now.", NamedTextColor.RED));
            return true;
        }

        // Lock global outbound tracker index maps
        activeRequests.put(target.getUniqueId(), new TpaRequest(sender.getUniqueId(), isHereRequest));
        outboundTrackers.add(sender.getUniqueId());

        plugin.getSoundEngine().playSoundProfile(sender, "sounds.send-request");

        if (autoAcceptTpa.contains(target.getUniqueId())) {
            sender.sendMessage(Component.text(target.getName() + " auto-accepted your request!", NamedTextColor.GREEN));
            handleAcceptDeny(target, true);
            return true;
        }

        target.sendMessage(Component.text("\n---------------------------------------------", NamedTextColor.GRAY));
        if (isHereRequest) {
            target.sendMessage(Component.text(sender.getName(), NamedTextColor.GOLD)
                    .append(Component.text(" requested that you teleport to them.", NamedTextColor.YELLOW)));
        } else {
            target.sendMessage(Component.text(sender.getName(), NamedTextColor.GOLD)
                    .append(Component.text(" wants to teleport to your location.", NamedTextColor.YELLOW)));
        }

        Component choiceButtons = Component.text("[ACCEPT] ", NamedTextColor.GREEN)
                .hoverEvent(HoverEvent.showText(Component.text("Click to accept request rules")))
                .clickEvent(ClickEvent.runCommand("/tpaccept"))
                .append(Component.text("   "))
                .append(Component.text("[DENY]", NamedTextColor.RED)
                        .hoverEvent(HoverEvent.showText(Component.text("Click to reject request rules")))
                        .clickEvent(ClickEvent.runCommand("/tpadeny")));

        target.sendMessage(choiceButtons);
        target.sendMessage(Component.text("---------------------------------------------\n", NamedTextColor.GRAY));
        plugin.getSoundEngine().playSoundProfile(target, "sounds.receive-request");

        sender.sendMessage(Component.text("Teleport request successfully delivered to " + target.getName() + ".", NamedTextColor.GREEN));

        int expiryDuration = plugin.getConfig().getInt("tpa.timeout", 60);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TpaRequest currentReq = activeRequests.get(target.getUniqueId());
            if (currentReq != null && currentReq.senderId().equals(sender.getUniqueId())) {
                activeRequests.remove(target.getUniqueId());
                outboundTrackers.remove(sender.getUniqueId());
                sender.sendMessage(Component.text("Your teleport request to " + target.getName() + " has expired.", NamedTextColor.RED));
                target.sendMessage(Component.text("Teleport request from " + sender.getName() + " has expired.", NamedTextColor.GRAY));
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
        outboundTrackers.remove(req.senderId()); // Unlock outbound tracker map constraints immediately

        if (sender == null) {
            target.sendMessage(Component.text("The player who sent this request is no longer online.", NamedTextColor.RED));
            return;
        }

        if (!accept) {
            sender.sendMessage(Component.text(target.getName() + " denied your request.", NamedTextColor.RED));
            target.sendMessage(Component.text("Request denied successfully.", NamedTextColor.YELLOW));
            plugin.getSoundEngine().playSoundProfile(target, "sounds.deny");
            plugin.getSoundEngine().playSoundProfile(sender, "sounds.deny");
            return;
        }

        Player traveller = req.isHereRequest() ? target : sender;
        Player destination = req.isHereRequest() ? sender : target;

        sender.sendMessage(Component.text(target.getName() + " accepted your request. Preparing teleportation warp...", NamedTextColor.GREEN));
        target.sendMessage(Component.text("Request accepted. Preparing warm-up...", NamedTextColor.GREEN));

        startTeleportCountdown(traveller, destination);
    }

    private void startTeleportCountdown(Player traveller, Player destination) {
        if (activeCountdowns.containsKey(traveller.getUniqueId())) {
            activeCountdowns.get(traveller.getUniqueId()).cancel();
        }

        // Cache absolute block grids to avoid cancellation via mouse look movements
        final Location startLocTraveller = traveller.getLocation().getBlock().getLocation();
        final Location startLocDestination = destination.getLocation().getBlock().getLocation();

        // Play Creeper warning fuse hiss sound triggers
        traveller.playSound(traveller.getLocation(), Sound.ENTITY_CREEPER_PRIMED, SoundCategory.MASTER, 1.0f, 0.5f);
        destination.playSound(destination.getLocation(), Sound.ENTITY_CREEPER_PRIMED, SoundCategory.MASTER, 1.0f, 0.5f);

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
                Location currentLocDestination = destination.getLocation().getBlock().getLocation();

                // STILLNESS DETECTORS: Cancel task iterations early if block maps fluctuate
                if (!currentLocTraveller.equals(startLocTraveller)) {
                    cancelCountdown(traveller.getUniqueId(), traveller);
                    destination.sendMessage(Component.text("Teleport cancelled: The moving player broke stillness rules.", NamedTextColor.RED));
                    this.cancel();
                    return;
                }

                if (!currentLocDestination.equals(startLocDestination)) {
                    cancelCountdown(traveller.getUniqueId(), destination);
                    traveller.sendMessage(Component.text("Teleport cancelled: The target destination moved.", NamedTextColor.RED));
                    this.cancel();
                    return;
                }

                if (secondsRemaining > 0) {
                    // FIX: Replaced direct .bold(boolean) with standard type-safe TextDecoration maps
                    Component titleText = Component.text(secondsRemaining + "...", TextColor.color(0xFF5555)).decoration(TextDecoration.BOLD, true);
                    Component subtitleText = Component.text("Do not move!", NamedTextColor.GRAY);
                    Title title = Title.title(titleText, subtitleText, Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO));

                    traveller.showTitle(title);
                    destination.showTitle(title);

                    traveller.playSound(traveller.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 0.6f, 1.0f);
                    destination.playSound(destination.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 0.6f, 1.0f);

                    secondsRemaining--;
                } else {
                    // FIX: Replaced direct .bold(boolean) here as well
                    Component wrapTitle = Component.text("WARPED!", TextColor.color(0x55FF55)).decoration(TextDecoration.BOLD, true);
                    Title endTitle = Title.title(wrapTitle, Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(250)));
                    traveller.showTitle(endTitle);
                    destination.showTitle(endTitle);

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
        }.runTaskTimer(plugin, 0L, 20L); // Execute once per second (every 20 ticks)

        activeCountdowns.put(traveller.getUniqueId(), task);
    }

    private void cancelCountdown(UUID travellerId, Player playerWhoMoved) {
        activeCountdowns.remove(travellerId);
        if (playerWhoMoved != null) {
            playerWhoMoved.sendMessage(Component.text("Teleportation cancelled because you moved your position!", NamedTextColor.RED));
            playerWhoMoved.playSound(playerWhoMoved.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.MASTER, 0.8f, 0.5f);
            playerWhoMoved.clearTitle();
        }
    }
}