package net.coreprotect.command;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.coreprotect.utility.Chat;
import net.coreprotect.utility.Color;
import net.coreprotect.utility.PauseManager;

public final class PauseCommand {

    private PauseCommand() {
        throw new IllegalStateException("Utility class");
    }

    public static void runCommand(CommandSender sender, boolean permission, String[] args) {
        if (!permission) {
            Chat.sendMessage(sender, Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- You do not have permission to do that.");
            return;
        }

        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "claim":
                pauseClaim(sender, args);
                return;
            case "dimension":
            case "dim":
            case "world":
                pauseDimension(sender, args);
                return;
            case "list":
                listPauses(sender);
                return;
            case "cancel":
                cancel(sender);
                return;
            default:
                usage(sender);
        }
    }

    private static void pauseClaim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            Chat.sendMessage(sender, prefix() + "/co pause claim must be used by a player.");
            return;
        }
        if (!PauseManager.griefPreventionAvailable()) {
            Chat.sendMessage(sender, prefix() + "GriefPrevention is not loaded. Use /co pause dimension instead.");
            return;
        }
        Long duration = parseDuration(sender, args);
        if (duration == null) {
            return;
        }
        Player player = (Player) sender;
        if (PauseManager.adminClaimIdAt(player.getLocation()) == null) {
            Chat.sendMessage(sender, prefix() + "You must stand inside a GriefPrevention admin claim.");
            return;
        }
        boolean ok = PauseManager.pauseClaimAt(player.getLocation(), duration);
        if (ok) {
            Chat.sendMessage(sender, prefix() + Color.WHITE + "Logging paused in this admin claim for " + formatDuration(duration) + ".");
        }
        else {
            Chat.sendMessage(sender, prefix() + Color.WHITE + "Unable to pause logging here.");
        }
    }

    private static void pauseDimension(CommandSender sender, String[] args) {
        Long duration = parseDuration(sender, args);
        if (duration == null) {
            return;
        }
        World world = null;
        if (args.length >= 4) {
            world = Bukkit.getWorld(args[3]);
            if (world == null) {
                Chat.sendMessage(sender, prefix() + "Unknown world: " + args[3]);
                return;
            }
        }
        else if (sender instanceof Player) {
            world = ((Player) sender).getWorld();
        }
        else {
            Chat.sendMessage(sender, prefix() + "Console must specify a world: /co pause dimension <duration> <world>");
            return;
        }
        PauseManager.pauseDimension(world, duration);
        Chat.sendMessage(sender, prefix() + Color.WHITE + "Logging paused in dimension '" + world.getName() + "' for " + formatDuration(duration) + ".");
    }

    private static void listPauses(CommandSender sender) {
        PauseManager.sweepExpired();
        Map<UUID, Long> dims = PauseManager.pausedDimensions();
        Map<Long, Long> claims = PauseManager.pausedClaims();
        if (dims.isEmpty() && claims.isEmpty()) {
            Chat.sendMessage(sender, prefix() + Color.WHITE + "No active pauses.");
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : dims.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            String name = world != null ? world.getName() : entry.getKey().toString();
            Chat.sendMessage(sender, prefix() + Color.WHITE + "Dimension '" + name + "' - " + formatDuration(entry.getValue() - now) + " remaining.");
        }
        for (Map.Entry<Long, Long> entry : claims.entrySet()) {
            Chat.sendMessage(sender, prefix() + Color.WHITE + "Admin claim #" + entry.getKey() + " - " + formatDuration(entry.getValue() - now) + " remaining.");
        }
    }

    private static void cancel(CommandSender sender) {
        if (!(sender instanceof Player)) {
            PauseManager.clearAll();
            Chat.sendMessage(sender, prefix() + Color.WHITE + "All pauses cleared.");
            return;
        }
        Player player = (Player) sender;
        boolean cleared = PauseManager.cancelAt(player.getLocation());
        if (cleared) {
            Chat.sendMessage(sender, prefix() + Color.WHITE + "Pause cleared at your location.");
        }
        else {
            Chat.sendMessage(sender, prefix() + Color.WHITE + "No active pause at your location.");
        }
    }

    private static Long parseDuration(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Chat.sendMessage(sender, prefix() + "Duration is required, e.g. 5m, 30s, 1h. Max " + (PauseManager.MAX_DURATION_MILLIS / 3600000L) + "h.");
            return null;
        }
        Long ms = parseDurationString(args[2]);
        if (ms == null) {
            Chat.sendMessage(sender, prefix() + "Invalid duration: '" + args[2] + "'. Use formats like 30s, 5m, 2h.");
            return null;
        }
        return ms;
    }

    private static Long parseDurationString(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        char unit = raw.charAt(raw.length() - 1);
        String numPart = raw.substring(0, raw.length() - 1);
        long multiplier;
        switch (unit) {
            case 's': multiplier = 1000L; break;
            case 'm': multiplier = 60_000L; break;
            case 'h': multiplier = 3_600_000L; break;
            default: return null;
        }
        try {
            long value = Long.parseLong(numPart);
            if (value <= 0) {
                return null;
            }
            return value * multiplier;
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatDuration(long ms) {
        long secs = Math.max(0, ms / 1000L);
        long hours = secs / 3600;
        long minutes = (secs % 3600) / 60;
        long seconds = secs % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private static void usage(CommandSender sender) {
        Chat.sendMessage(sender, prefix() + Color.WHITE + "Usage:");
        Chat.sendMessage(sender, "  /co pause claim <duration>");
        Chat.sendMessage(sender, "  /co pause dimension <duration> [world]");
        Chat.sendMessage(sender, "  /co pause list");
        Chat.sendMessage(sender, "  /co pause cancel");
    }

    private static String prefix() {
        return Color.DARK_AQUA + "CoreProtect " + Color.WHITE + "- ";
    }
}
