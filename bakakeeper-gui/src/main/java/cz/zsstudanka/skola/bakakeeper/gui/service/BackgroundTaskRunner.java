package cz.zsstudanka.skola.bakakeeper.gui.service;

import javafx.concurrent.Task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Centrální spouštěč pozadových úloh pro GUI.
 * Používá vláknový pool (daemon vlákna) pro všechny servisní operace.
 */
public final class BackgroundTaskRunner {

    private static final ExecutorService executor =
            Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "bk-worker");
                t.setDaemon(true);
                return t;
            });

    private BackgroundTaskRunner() {
    }

    /**
     * Spustí Task na pozadovém vlákně.
     */
    public static <T> void run(Task<T> task) {
        executor.submit(task);
    }

    /**
     * Ukončí pool – volat při Application.stop().
     */
    public static void shutdown() {
        executor.shutdownNow();
    }
}
