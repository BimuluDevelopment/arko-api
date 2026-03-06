package dk.arko.api.paper.task;

import dk.arko.api.common.scheduling.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Paper task scheduler with Folia detection and compatibility.
 * Automatically delegates to the correct scheduler based on the server type.
 */
public class PaperTaskScheduler implements TaskScheduler {

    private final JavaPlugin plugin;
    private final boolean isFolia;
    private final List<ScheduledTask> tasks = new ArrayList<>();

    public PaperTaskScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.isFolia = detectFolia();
    }

    private boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public boolean isFolia() { return isFolia; }

    @Override
    public ScheduledTask runSync(Runnable task) {
        if (isFolia) {
            return wrapFolia(Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run()));
        }
        return wrapBukkit(Bukkit.getScheduler().runTask(plugin, task));
    }

    @Override
    public ScheduledTask runAsync(Runnable task) {
        if (isFolia) {
            return wrapFolia(Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run()));
        }
        return wrapBukkit(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }

    @Override
    public ScheduledTask runSyncLater(Runnable task, long delayTicks) {
        if (isFolia) {
            return wrapFolia(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), delayTicks));
        }
        return wrapBukkit(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    @Override
    public ScheduledTask runAsyncLater(Runnable task, long delayTicks) {
        if (isFolia) {
            return wrapFolia(Bukkit.getAsyncScheduler().runDelayed(plugin, t -> task.run(),
                    delayTicks * 50, java.util.concurrent.TimeUnit.MILLISECONDS));
        }
        return wrapBukkit(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks));
    }

    @Override
    public ScheduledTask runSyncRepeating(Runnable task, long delayTicks, long periodTicks) {
        if (isFolia) {
            return wrapFolia(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), delayTicks, periodTicks));
        }
        return wrapBukkit(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
    }

    @Override
    public ScheduledTask runAsyncRepeating(Runnable task, long delayTicks, long periodTicks) {
        if (isFolia) {
            return wrapFolia(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(),
                    delayTicks * 50, periodTicks * 50, java.util.concurrent.TimeUnit.MILLISECONDS));
        }
        return wrapBukkit(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks));
    }

    /**
     * Run a task for a specific entity (Folia-aware: uses entity's region scheduler).
     */
    public ScheduledTask runForEntity(org.bukkit.entity.Entity entity, Runnable task) {
        if (isFolia) {
            return wrapFolia(entity.getScheduler().run(plugin, t -> task.run(), null));
        }
        return runSync(task);
    }

    /**
     * Run a task at a specific location (Folia-aware: uses region scheduler).
     */
    public ScheduledTask runAtLocation(org.bukkit.Location location, Runnable task) {
        if (isFolia) {
            return wrapFolia(Bukkit.getRegionScheduler().run(plugin, location, t -> task.run()));
        }
        return runSync(task);
    }

    @Override
    public void cancelAll() {
        tasks.forEach(ScheduledTask::cancel);
        tasks.clear();
    }

    // ─── Wrappers ──────────────────────────────────────────────

    private ScheduledTask wrapBukkit(org.bukkit.scheduler.BukkitTask bukkitTask) {
        ScheduledTask task = new ScheduledTask() {
            @Override
            public void cancel() { bukkitTask.cancel(); }
            @Override
            public boolean isCancelled() { return bukkitTask.isCancelled(); }
        };
        tasks.add(task);
        return task;
    }

    private ScheduledTask wrapFolia(io.papermc.paper.threadedregions.scheduler.ScheduledTask foliaTask) {
        ScheduledTask task = new ScheduledTask() {
            @Override
            public void cancel() { foliaTask.cancel(); }
            @Override
            public boolean isCancelled() { return foliaTask.isCancelled(); }
        };
        tasks.add(task);
        return task;
    }
}
