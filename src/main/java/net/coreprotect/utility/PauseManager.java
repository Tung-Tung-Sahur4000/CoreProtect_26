package net.coreprotect.utility;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Temporarily suppresses CoreProtect logging inside selected GriefPrevention admin
 * claims or whole dimensions. Used for hooking up farms, redstone, and TNT testing
 * without flooding the database.
 *
 * Pauses are in-memory only and evaporate on plugin/server restart.
 */
public final class PauseManager {

    public static final long MAX_DURATION_MILLIS = 6L * 60L * 60L * 1000L; // 6 hours hard cap

    private static final Map<Long, Long> PAUSED_CLAIMS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PAUSED_DIMENSIONS = new ConcurrentHashMap<>();

    private static volatile boolean griefPreventionResolved = false;
    private static Object dataStoreInstance;
    private static Method getClaimAtMethod;
    private static Method isAdminClaimMethod;
    private static Method getIdMethod;

    private PauseManager() {
        throw new IllegalStateException("Utility class");
    }

    public static long maxDurationMillis() {
        return MAX_DURATION_MILLIS;
    }

    public static boolean pauseDimension(World world, long durationMillis) {
        if (world == null || durationMillis <= 0) {
            return false;
        }
        long capped = Math.min(durationMillis, MAX_DURATION_MILLIS);
        PAUSED_DIMENSIONS.put(world.getUID(), System.currentTimeMillis() + capped);
        return true;
    }

    public static boolean pauseClaimAt(Location location, long durationMillis) {
        if (location == null || durationMillis <= 0) {
            return false;
        }
        Long claimId = adminClaimIdAt(location);
        if (claimId == null) {
            return false;
        }
        long capped = Math.min(durationMillis, MAX_DURATION_MILLIS);
        PAUSED_CLAIMS.put(claimId, System.currentTimeMillis() + capped);
        return true;
    }

    /** Cancels any active pause that covers the given location. Returns true if anything was cleared. */
    public static boolean cancelAt(Location location) {
        boolean cleared = false;
        if (location != null && location.getWorld() != null) {
            if (PAUSED_DIMENSIONS.remove(location.getWorld().getUID()) != null) {
                cleared = true;
            }
            Long claimId = adminClaimIdAt(location);
            if (claimId != null && PAUSED_CLAIMS.remove(claimId) != null) {
                cleared = true;
            }
        }
        return cleared;
    }

    public static boolean isPaused(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        long now = System.currentTimeMillis();

        Long dimExpiry = PAUSED_DIMENSIONS.get(location.getWorld().getUID());
        if (dimExpiry != null) {
            if (now < dimExpiry) {
                return true;
            }
            PAUSED_DIMENSIONS.remove(location.getWorld().getUID(), dimExpiry);
        }

        Long claimId = adminClaimIdAt(location);
        if (claimId != null) {
            Long claimExpiry = PAUSED_CLAIMS.get(claimId);
            if (claimExpiry != null) {
                if (now < claimExpiry) {
                    return true;
                }
                PAUSED_CLAIMS.remove(claimId, claimExpiry);
            }
        }
        return false;
    }

    public static Map<UUID, Long> pausedDimensions() {
        sweepExpired();
        return PAUSED_DIMENSIONS;
    }

    public static Map<Long, Long> pausedClaims() {
        sweepExpired();
        return PAUSED_CLAIMS;
    }

    public static void clearAll() {
        PAUSED_CLAIMS.clear();
        PAUSED_DIMENSIONS.clear();
    }

    public static void sweepExpired() {
        long now = System.currentTimeMillis();
        PAUSED_DIMENSIONS.entrySet().removeIf(entry -> entry.getValue() <= now);
        PAUSED_CLAIMS.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    /**
     * Returns the numeric claim ID at the given location if it sits inside a
     * GriefPrevention admin claim, otherwise null. Uses reflection so we keep
     * GriefPrevention as an optional soft-depend.
     */
    public static Long adminClaimIdAt(Location location) {
        if (!resolveGriefPrevention()) {
            return null;
        }
        try {
            Object claim = getClaimAtMethod.invoke(dataStoreInstance, location, true, null);
            if (claim == null) {
                return null;
            }
            Boolean isAdmin = (Boolean) isAdminClaimMethod.invoke(claim);
            if (Boolean.TRUE.equals(isAdmin)) {
                return (Long) getIdMethod.invoke(claim);
            }
        }
        catch (Exception e) {
            ErrorReporter.report(e);
        }
        return null;
    }

    public static boolean griefPreventionAvailable() {
        return resolveGriefPrevention();
    }

    private static boolean resolveGriefPrevention() {
        if (griefPreventionResolved) {
            return dataStoreInstance != null;
        }
        synchronized (PauseManager.class) {
            if (griefPreventionResolved) {
                return dataStoreInstance != null;
            }
            try {
                Plugin gp = Bukkit.getPluginManager().getPlugin("GriefPrevention");
                if (gp != null && gp.isEnabled()) {
                    Class<?> gpClass = gp.getClass();
                    Object dataStore = gpClass.getField("dataStore").get(gp);
                    Class<?> dataStoreClass = dataStore.getClass();
                    Class<?> claimClass = Class.forName("me.ryanhamshire.GriefPrevention.Claim", false, gpClass.getClassLoader());

                    getClaimAtMethod = dataStoreClass.getMethod("getClaimAt", Location.class, boolean.class, claimClass);
                    isAdminClaimMethod = claimClass.getMethod("isAdminClaim");
                    getIdMethod = claimClass.getMethod("getID");
                    dataStoreInstance = dataStore;
                }
            }
            catch (Throwable t) {
                dataStoreInstance = null;
            }
            griefPreventionResolved = true;
            return dataStoreInstance != null;
        }
    }
}
