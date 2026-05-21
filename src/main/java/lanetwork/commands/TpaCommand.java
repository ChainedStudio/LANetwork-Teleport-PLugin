package lanetwork.commands;

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

    // Tracks individual requests. We use a custom object to know if it's a regular TPA or a TPAHERE.
    private final HashMap<UUID, TpaRequest> activeRequests = new HashMap<>();

    // Global toggles: Players who have disabled receiving TPA requests entirely
    private final HashSet<UUID> disabledTpa = new HashSet<>();

    // Per-player ignores: Maps a Player UUID to a set of Player UUIDs they are ignoring
    private final HashMap<UUID, HashSet<UUID>> ignoreLists = new HashMap<>();

    // Simple helper class to store request data
    private record TpaRequest(UUID senderId, boolean isHereRequest) {}

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Only players can use teleportation commands!", NamedTextColor.RED));
            return true;
        }

        String cmdName = command.getName().toLowerCase();

        // --- GLOBAL SETTINGS COMMANDS ---
        if (cmdName.equals("tpatoggle")) {
            if (disabledTpa.contains(p.getUniqueId())) {
                disabledTpa.remove(p.getUniqueId());
                p.sendMessage(Component.text("TPA requests enabled.", NamedTextColor.GREEN));
            } else {
                disabledTpa.add(p.getUniqueId());
                p.sendMessage(Component.text("TPA requests disabled.", NamedTextColor.RED));
            }
            return true;
        }

        if (cmdName.equals("tpaignore")) {
            if (args.length == 0) {
                p.sendMessage(Component.text("Usage: /tpaignore <player>", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                p.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
            ignoreLists.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(target.getUniqueId());
            p.sendMessage(Component.text("You are now ignoring requests from " + target.getName() + ".", NamedTextColor.GREEN));
            return true;
        }

        if (cmdName.equals("tpaunignore")) {
            if (args.length == 0) {
                p.sendMessage(Component.text("Usage: /tpaunignore <player>", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                p.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
            if (ignoreLists.containsKey(p.getUniqueId())) {
                ignoreLists.get(p.getUniqueId()).remove(target.getUniqueId());
            }
            p.sendMessage(Component.text("You unignored " + target.getName() + ".", NamedTextColor.GREEN));
            return true;
        }

        // --- ACTION EXECUTION COMMANDS (ACCEPT/DENY) ---
        if (cmdName.equals("tpaccept")) {
            handleResolve(p, true);
            return true;
        }

        if (cmdName.equals("tpadeny")) {
            handleResolve(p, false);
            return true;
        }

        // --- REQUEST GENERATION COMMANDS (TPA / TPAHERE / TPAHEREALL) ---
        if (cmdName.equals("tpahereall")) {
            int sentCount = 0;
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.getUniqueId().equals(p.getUniqueId())) continue;
                if (sendRequest(p, onlinePlayer, true)) {
                    sentCount++;
                }
            }
            p.sendMessage(Component.text("Sent a /tpahere request to all " + sentCount + " online players.", NamedTextColor.GREEN));
            return true;
        }

        // Standard /tpa and /tpahere targets validation
        if (args.length == 0) {
            p.sendMessage(Component.text("Usage: /" + cmdName + " <player>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            p.sendMessage(Component.text("Player not found or offline.", NamedTextColor.RED));
            return true;
        }

        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(Component.text("You cannot send teleport requests to yourself!", NamedTextColor.RED));
            return true;
        }

        boolean isHere = cmdName.equals("tpahere");
        sendRequest(p, target, isHere);
        return true;
    }

    private boolean sendRequest(Player sender, Player target, boolean isHereRequest) {
        // Validation Checks
        if (disabledTpa.contains(target.getUniqueId())) {
            sender.sendMessage(Component.text(target.getName() + " has TPA requests disabled.", NamedTextColor.RED));
            return false;
        }
        if (ignoreLists.containsKey(target.getUniqueId()) && ignoreLists.get(target.getUniqueId()).contains(sender.getUniqueId())) {
            // Silently fail or notify sender depending on your network design choice
            sender.sendMessage(Component.text("You cannot send requests to this player.", NamedTextColor.RED));
            return false;
        }

        activeRequests.put(target.getUniqueId(), new TpaRequest(sender.getUniqueId(), isHereRequest));
        sender.sendMessage(Component.text("Request sent to " + target.getName() + ".", NamedTextColor.GREEN));

        // Format message depending on strategy
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

        // Auto expiration logic (60 seconds)
        Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("LanetworkTeleport"), () -> {
            TpaRequest current = activeRequests.get(target.getUniqueId());
            if (current != null && current.senderId().equals(sender.getUniqueId())) {
                activeRequests.remove(target.getUniqueId());
                sender.sendMessage(Component.text("Your request to " + target.getName() + " has expired.", NamedTextColor.RED));
                target.sendMessage(Component.text("Teleport request from " + sender.getName() + " has expired.", NamedTextColor.RED));
            }
        }, 1200L);

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

        // Handle who goes where depending on the internal type
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
}