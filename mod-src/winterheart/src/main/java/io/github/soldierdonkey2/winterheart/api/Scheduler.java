package io.github.soldierdonkey2.winterheart.api;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Runs a task a number of server ticks from now (what server.scheduleInTicks did). Pending tasks are dropped when the server stops. */
public final class Scheduler {
    private static final class Task {
        long due;
        final Runnable run;
        Task(long due, Runnable run) { this.due = due; this.run = run; }
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static long now = 0;

    private Scheduler() {}

    public static synchronized void runLater(MinecraftServer server, int ticks, Runnable task) {
        TASKS.add(new Task(now + Math.max(0, ticks), task));
    }

    /** A random delay between min and max ticks, inclusive. */
    public static void runLaterRandom(MinecraftServer server, int min, int max, Runnable task) {
        runLater(server, ThreadLocalRandom.current().nextInt(min, max + 1), task);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        List<Runnable> due = new ArrayList<>();
        synchronized (Scheduler.class) {
            now++;
            for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
                Task t = it.next();
                if (t.due <= now) { due.add(t.run); it.remove(); }
            }
        }
        due.forEach(Runnable::run);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        synchronized (Scheduler.class) { TASKS.clear(); }
    }
}
