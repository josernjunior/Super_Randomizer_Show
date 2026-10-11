package srs.core;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Runs code on the server's tick thread. Commands typed in a dedicated server's console run on a separate thread,
 * and touching levels/mobs from there while the server ticks can deadlock it.
 */
public final class ServerTasks {

    private static final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

    private ServerTasks() {
    }

    public static void submit(Runnable task) {
        queue.add(task);
    }

    /** Called at the end of every Server.tick(), which keeps running even while an empty server is paused. */
    public static void runQueued() {
        Runnable task;
        while ((task = queue.poll()) != null) {
            try {
                task.run();
            } catch (Throwable e) {
                SRS.warn("Server task failed: " + e);
                e.printStackTrace();
            }
        }
    }

}
