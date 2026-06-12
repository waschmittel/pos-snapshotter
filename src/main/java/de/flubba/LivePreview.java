package de.flubba;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Turns a source image into a live dithered preview. Hides the polling thread,
 * null guards, dither-parameter reads and EDT handoff from callers: the sink is
 * always invoked on the EDT.
 */
public final class LivePreview {

    private static final long IDLE = Long.MIN_VALUE;

    private final Supplier<BufferedImage> source;
    private final SettingsStore settings;
    private final Consumer<BufferedImage> sink;
    private final long debounceMs;
    private final AtomicLong dueAt = new AtomicLong(IDLE);

    private LivePreview(Supplier<BufferedImage> source, SettingsStore settings,
                        Consumer<BufferedImage> sink, long debounceMs) {
        this.source = source;
        this.settings = settings;
        this.sink = sink;
        this.debounceMs = debounceMs;
    }

    /**
     * Re-dithers the source image on every poll tick while {@code active} is true.
     */
    public static LivePreview continuous(String name, AtomicBoolean running, BooleanSupplier active,
                                         Supplier<BufferedImage> source, SettingsStore settings,
                                         Consumer<BufferedImage> sink) {
        var preview = new LivePreview(source, settings, sink, 0);
        PollingLoop.start(name, running, active, preview::update);
        return preview;
    }

    /**
     * Updates only after {@link #poke()} and the debounce delay has elapsed
     * without further pokes.
     */
    public static LivePreview debounced(String name, AtomicBoolean running, long debounceMs,
                                        Supplier<BufferedImage> source, SettingsStore settings,
                                        Consumer<BufferedImage> sink) {
        var preview = new LivePreview(source, settings, sink, debounceMs);
        PollingLoop.start(name, running, preview::isDue, preview::update);
        return preview;
    }

    /** Schedule an update after the debounce delay; resets the delay if already pending. */
    public void poke() {
        dueAt.set(System.currentTimeMillis() + debounceMs);
    }

    /** Schedule an update on the next poll tick, skipping the debounce delay. */
    public void pokeNow() {
        dueAt.set(System.currentTimeMillis());
    }

    private boolean isDue() {
        long due = dueAt.get();
        return due != IDLE && System.currentTimeMillis() >= due;
    }

    private void update() {
        long due = dueAt.get();
        if (due != IDLE) {
            dueAt.compareAndSet(due, IDLE);
        }
        BufferedImage image = source.get();
        if (image == null) {
            return;
        }
        BufferedImage dithered = DitherPipeline.preview(image, settings.currentDitherParams());
        SwingUtilities.invokeLater(() -> sink.accept(dithered));
    }
}
