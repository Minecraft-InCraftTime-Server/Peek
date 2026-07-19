package ict.minesunshineone.peek.util;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Folia/Paper entity scheduler helpers.
 */
public final class FoliaSchedulerUtil {

    private FoliaSchedulerUtil() {
    }

    public static boolean isOwnedByCurrentRegion(Plugin plugin, Entity entity) {
        if (plugin == null || entity == null) {
            return false;
        }

        try {
            return plugin.getServer().isOwnedByCurrentRegion(entity);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean runOnEntity(Plugin plugin, Entity entity, Runnable action, Runnable retired) {
        if (plugin == null || entity == null || action == null) {
            return false;
        }

        if (!plugin.isEnabled() || isOwnedByCurrentRegion(plugin, entity)) {
            action.run();
            return true;
        }

        ScheduledTask task = entity.getScheduler().run(plugin, scheduledTask -> action.run(),
                () -> runRetired(retired));
        if (task != null) {
            return true;
        }

        runRetired(retired);
        return false;
    }

    public static boolean executeOnEntity(Plugin plugin, Entity entity, Runnable action, Runnable retired, long delay) {
        if (plugin == null || entity == null || action == null) {
            return false;
        }

        if (!plugin.isEnabled() || (delay <= 0L && isOwnedByCurrentRegion(plugin, entity))) {
            action.run();
            return true;
        }

        boolean scheduled = entity.getScheduler().execute(plugin, action, () -> runRetired(retired), delay);
        if (scheduled) {
            return true;
        }

        runRetired(retired);
        return false;
    }

    private static void runRetired(Runnable retired) {
        if (retired != null) {
            retired.run();
        }
    }
}
