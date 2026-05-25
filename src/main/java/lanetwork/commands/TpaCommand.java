package lanetwork.commands;

import lanetwork.teleport.Teleport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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

    // Administrative master debug override loop flag
    private boolean debugMode = false;

    private record TpaRequest(UUID senderId, boolean isHereRequest) {}

    public TpaCommand(Teleport plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Only players can use teleportation commands!", NamedTextColor.RED));
            return true;
        }

        String cmd = command.getName().toLowerCase();

        // 1. Administrative Master Toggle Router: /debug
        if (cmd.equals("debug")) {
            if (!p.hasPermission("lanetwork.admin")) {
                p.sendMessage(Component.text("You lack administrative permission to toggle universal debugging options.", NamedTextColor.RED));
                return true;
            }

            debugMode = !debugMode;
            if (debugMode) {
                p.sendMessage(Component.text("\n=======================================", NamedTextColor.DARK_GRAY));
                p.sendMessage(Component.text(" TPA UNIVERSAL SOLO-TESTING: ACTIVE ", NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD));
                p.sendMessage(Component.text(" All multi-player validation checks are now bypassed.", NamedTextColor.GRAY));
                p.sendMessage(Component.text(" Target your own username to run any command loop solo.", NamedTextColor.YELLOW));
                p.sendMessage(Component.text("=======================================\n", NamedTextColor.DARK_GRAY));
            } else {
                p.sendMessage(Component.text("TPA Universal Solo-Testing has been deactivated.", NamedTextColor.RED));
            }
            return true;
        }

        // 2. Personal Toggle Options Verification Routing
        if (cmd.equals("tpatoggle")) {
            if (disabledTpa.contains(p.getUniqueId())) {
                disabledTpa.remove(p.getUniqueId());
                p.sendMessage(Component.text("TPA requests enabled.", NamedTextColor.GREEN));
            } else {
                disabledTpa.add(p.getUniqueId());
                p.sendMessage(Component.text("TPA requests disabled.", NamedTextColor.RED));
            }
            return true;
        }

        if (cmd.equals("tpaauto")) {
            if (autoAcceptTpa.contains(p.getUniqueId())) {
                autoAcceptTpa.remove(p.getUniqueId());
                p.sendMessage(Component.text("Auto-accept disabled.", NamedTextColor.RED));
            } else {
                autoAcceptTpa.add(p.getUniqueId());
                p.sendMessage(Component.text("Auto-accept enabled. (Test using /tpa while /debug is active!)", NamedTextColor.GREEN));
            }
            return true;
        }

        // 3. Response Processing Route Handlers
        if (cmd.equals("tpaccept") || cmd.equals("tpadeny")) {
            handleResponse(p, cmd.equals("tpaccept"));
            return true;
        }

        // 4. Blacklist Target System Logic Checking (/tpaignore and /tpaunignore)
        if (cmd.equals("tpaignore") || cmd.equals("tpaunignore")) {
            if (args.length == 0) {
                p.sendMessage(Component.text("Usage: /" + cmd + " <player>", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                p.sendMessage(Component.text("Target player profile not found online.", NamedTextColor.RED));
                return true;
            }

            if (target.getUniqueId().equals(p.getUniqueId()) && !debugMode) {
                p.sendMessage(Component.text("You cannot target yourself under standard conditions.", NamedTextColor.RED));
                return true;
            }

            HashSet<UUID> ignored = ignoreLists.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
            if (cmd.equals("tpaignore")) {
                ignored.add(target.getUniqueId());
                p.sendMessage(Component.text("You are now ignoring requests from: " + target.getName(), NamedTextColor.YELLOW));
            } else {
                ignored.remove(target.getUniqueId());
                p.sendMessage(Component.text("You are no longer ignoring requests from: " + target.getName(), NamedTextColor.GREEN));
            }
            return true;
        }

        // 5. Outbound Core Requests Pipeline (/tpa and /tpahere)
        if (cmd.equals("tpa") || cmd.equals("tpahere")) {
            if (args.length == 0) {
                p.sendMessage(Component.text("Usage: /" + cmd + " <player>", NamedTextColor.RED));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                p.sendMessage(Component.text("The target player you specified is currently offline.", NamedTextColor.RED));
                return true;
            }

            if (target.getUniqueId().equals(p.getUniqueId()) && !debugMode) {
                p.sendMessage(Component.text("You cannot teleport to yourself!", NamedTextColor.RED));
                return true;
            }

            boolean isHere = cmd.equals("tpahere");
            sendRequest(p, target, isHere);
            return true;
        }

        return false;
    }

    private void sendRequest(Player sender, Player target, boolean isHereRequest) {
        if (!debugMode) {
            HashSet<UUID> targetIgnoreList = ignoreLists.get(target.getUniqueId());
            if (targetIgnoreList != null && targetIgnoreList.contains(sender.getUniqueId())) {
                sender.sendMessage(Component.text("You cannot send requests to this user right now.", NamedTextColor.RED));
                return;
            }

            if (disabledTpa.contains(target.getUniqueId())) {
                sender.sendMessage(Component.text("This player has disabled inbound request interactions.", NamedTextColor.RED));
                return;
            }
        }

        // SOLO-TEST CASE 1: Testing /tpaauto features
        if (autoAcceptTpa.contains(target.getUniqueId())) {
            sender.sendMessage(Component.text("[DEBUG MATCH] Auto-accept evaluation logic successfully matched.", NamedTextColor.AQUA));
            sender.sendMessage(Component.text(target.getName() + " automatically accepted your request.", NamedTextColor.GREEN));

            Player traveller   = isHereRequest ? target  : sender;
            Player destination = isHereRequest ? sender  : target;

            // Execute automated teleport and track destination chunk load audio
            traveller.teleportAsync(destination.getLocation()).thenAccept(success -> {
                if (success) {
                    plugin.getSoundEngine().playSoundProfile(traveller, "sounds.teleport");
                }
            });
            return;
        }

        activeRequests.put(target.getUniqueId(), new TpaRequest(sender.getUniqueId(), isHereRequest));
        outboundTrackers.add(sender.getUniqueId());

        plugin.getSoundEngine().playSoundProfile(sender, "sounds.send-request");
        sender.sendMessage(Component.text("Teleport request sent to " + target.getName() + ".", NamedTextColor.GREEN));

        String typeString = isHereRequest ? " to teleport to them." : " to teleport to you.";
        Component message = Component.text("\n" + sender.getName(), NamedTextColor.GOLD)
                .append(Component.text(typeString + "\n", NamedTextColor.YELLOW))
                .append(Component.text("[ACCEPT] ", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/tpaccept"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to accept request", NamedTextColor.GREEN))))
                .append(Component.text("[DENY]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/tpadeny"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to deny request", NamedTextColor.RED))));

        target.sendMessage(message);

        if (!sender.getUniqueId().equals(target.getUniqueId())) {
            plugin.getSoundEngine().playSoundProfile(target, "sounds.receive-request");
        } else {
            plugin.getSoundEngine().playSoundProfile(sender, "sounds.receive-request");
            sender.sendMessage(Component.text("[DEBUG MATCH] Playing sounds.receive-request side-by-side with send trigger.", NamedTextColor.AQUA));
        }

        int timeoutTicks = plugin.getConfig().getInt("tpa.timeout", 60) * 20;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TpaRequest current = activeRequests.get(target.getUniqueId());
            if (current != null && current.senderId().equals(sender.getUniqueId())) {
                activeRequests.remove(target.getUniqueId());
                outboundTrackers.remove(sender.getUniqueId());
                sender.sendMessage(Component.text("Your teleport request to " + target.getName() + " has expired.", NamedTextColor.RED));
                target.sendMessage(Component.text("Teleport request from " + sender.getName() + " has expired.", NamedTextColor.RED));
            }
        }, timeoutTicks);
    }

    private void handleResponse(Player target, boolean accept) {
        TpaRequest req = activeRequests.remove(target.getUniqueId());
        if (req == null) {
            target.sendMessage(Component.text("You do not have any pending incoming requests.", NamedTextColor.RED));
            return;
        }

        outboundTrackers.remove(req.senderId());

        Player sender = Bukkit.getPlayer(req.senderId());
        if (sender == null || !sender.isOnline()) {
            target.sendMessage(Component.text("The player associated with this request is offline.", NamedTextColor.RED));
            return;
        }

        // FIX: Deny pathway trigger logic added here
        if (!accept) {
            sender.sendMessage(Component.text(target.getName() + " denied your request.", NamedTextColor.RED));
            target.sendMessage(Component.text("Request denied successfully.", NamedTextColor.YELLOW));

            // Play the deny sound directly to the player who canceled it, and the sender
            plugin.getSoundEngine().playSoundProfile(target, "sounds.deny");
            if (!sender.getUniqueId().equals(target.getUniqueId())) {
                plugin.getSoundEngine().playSoundProfile(sender, "sounds.deny");
            }
            return;
        }

        Player traveller   = req.isHereRequest() ? target  : sender;
        Player destination = req.isHereRequest() ? sender  : target;

        sender.sendMessage(Component.text(target.getName() + " accepted your request.", NamedTextColor.GREEN));
        target.sendMessage(Component.text("Request accepted. Teleporting...", NamedTextColor.GREEN));

        // FIX: Fire arrival audio profile inside the async chunk complete handler
        traveller.teleportAsync(destination.getLocation()).thenAccept(success -> {
            if (success) {
                plugin.getSoundEngine().playSoundProfile(traveller, "sounds.teleport");
                if (!traveller.getUniqueId().equals(destination.getUniqueId())) {
                    plugin.getSoundEngine().playSoundProfile(destination, "sounds.teleport");
                }
            }
        });
    }
}