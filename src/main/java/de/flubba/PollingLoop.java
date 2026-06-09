package de.flubba;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

@Slf4j
public final class PollingLoop {

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private PollingLoop() {
    }

    public static void start(String name, AtomicBoolean running, BooleanSupplier shouldRun, ThrowingRunnable work) {
        Thread.ofVirtual().name(name).start(() -> {
            while (running.get()) {
                try {
                    if (!shouldRun.getAsBoolean()) {
                        Thread.sleep(200);
                        continue;
                    }
                    work.run();
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in {}", name, e);
                }
            }
        });
    }
}
