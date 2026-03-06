package dk.arko.api.common.scheduling;

import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Platform-agnostic task scheduler with support for delayed, repeating,
 * and async tasks. Paper implementation handles Folia compatibility.
 */
public interface TaskScheduler {

    /**
     * Run a task on the main thread.
     */
    ScheduledTask runSync(Runnable task);

    /**
     * Run a task asynchronously.
     */
    ScheduledTask runAsync(Runnable task);

    /**
     * Run a task on the main thread after a delay.
     */
    ScheduledTask runSyncLater(Runnable task, long delayTicks);

    /**
     * Run a task asynchronously after a delay.
     */
    ScheduledTask runAsyncLater(Runnable task, long delayTicks);

    /**
     * Run a repeating task on the main thread.
     */
    ScheduledTask runSyncRepeating(Runnable task, long delayTicks, long periodTicks);

    /**
     * Run a repeating async task.
     */
    ScheduledTask runAsyncRepeating(Runnable task, long delayTicks, long periodTicks);

    /**
     * Run a task chain with sequential async steps.
     */
    default TaskChain chain() {
        return new TaskChain(this);
    }

    /**
     * Cancel all tasks from this scheduler.
     */
    void cancelAll();

    /**
     * Represents a scheduled task.
     */
    interface ScheduledTask {
        void cancel();
        boolean isCancelled();
    }

    /**
     * Task chain for fluent async/sync task sequences.
     */
    class TaskChain {
        private final TaskScheduler scheduler;
        private final java.util.Queue<ChainStep> steps = new ConcurrentLinkedQueue<>();

        TaskChain(TaskScheduler scheduler) {
            this.scheduler = scheduler;
        }

        /**
         * Add a sync step.
         */
        public TaskChain sync(Runnable task) {
            steps.add(new ChainStep(task, false, 0));
            return this;
        }

        /**
         * Add an async step.
         */
        public TaskChain async(Runnable task) {
            steps.add(new ChainStep(task, true, 0));
            return this;
        }

        /**
         * Add a delayed step.
         */
        public TaskChain delay(long ticks) {
            steps.add(new ChainStep(() -> {}, false, ticks));
            return this;
        }

        /**
         * Execute the chain.
         */
        public void execute() {
            executeNext();
        }

        private void executeNext() {
            ChainStep step = steps.poll();
            if (step == null) return;

            Runnable wrapped = () -> {
                step.task.run();
                executeNext();
            };

            if (step.delay > 0) {
                if (step.async) scheduler.runAsyncLater(wrapped, step.delay);
                else scheduler.runSyncLater(wrapped, step.delay);
            } else {
                if (step.async) scheduler.runAsync(wrapped);
                else scheduler.runSync(wrapped);
            }
        }

        private record ChainStep(Runnable task, boolean async, long delay) {}
    }
}
