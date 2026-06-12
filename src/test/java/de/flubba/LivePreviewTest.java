package de.flubba;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;

class LivePreviewTest {

    private Preferences testPrefs;
    private SettingsStore settings;
    private final AtomicBoolean running = new AtomicBoolean(true);

    @BeforeEach
    void setUp() throws BackingStoreException {
        testPrefs = Preferences.userRoot().node("/test/livePreview");
        testPrefs.clear();
        settings = new SettingsStore(testPrefs);
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        running.set(false);
        testPrefs.removeNode();
    }

    private static BufferedImage testImage() {
        return new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
    }

    private static void waitFor(java.util.function.BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    @Test
    void continuous_deliversDitheredImagesToSink() throws Exception {
        var delivered = new AtomicReference<BufferedImage>();
        LivePreview.continuous("test-continuous", running, () -> true,
                LivePreviewTest::testImage, settings, delivered::set);

        waitFor(() -> delivered.get() != null, 5000);
        assertThat(delivered.get()).isNotNull();
        assertThat(delivered.get().getWidth()).isEqualTo(8);
    }

    @Test
    void continuous_nullSource_neverCallsSink() throws Exception {
        var calls = new AtomicInteger();
        LivePreview.continuous("test-null-source", running, () -> true,
                () -> null, settings, _ -> calls.incrementAndGet());

        Thread.sleep(500);
        assertThat(calls.get()).isZero();
    }

    @Test
    void continuous_inactive_doesNotUpdate() throws Exception {
        var calls = new AtomicInteger();
        LivePreview.continuous("test-inactive", running, () -> false,
                LivePreviewTest::testImage, settings, _ -> calls.incrementAndGet());

        Thread.sleep(500);
        assertThat(calls.get()).isZero();
    }

    @Test
    void debounced_noPoke_doesNotUpdate() throws Exception {
        var calls = new AtomicInteger();
        LivePreview.debounced("test-no-poke", running, 50,
                LivePreviewTest::testImage, settings, _ -> calls.incrementAndGet());

        Thread.sleep(500);
        assertThat(calls.get()).isZero();
    }

    @Test
    void debounced_pokeNow_updatesOnce() throws Exception {
        var calls = new AtomicInteger();
        var preview = LivePreview.debounced("test-poke-now", running, 60_000,
                LivePreviewTest::testImage, settings, _ -> calls.incrementAndGet());

        preview.pokeNow();
        waitFor(() -> calls.get() > 0, 5000);
        assertThat(calls.get()).isEqualTo(1);

        // long debounce + no further poke: no second update
        Thread.sleep(500);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void debounced_poke_updatesAfterDelay() throws Exception {
        var calls = new AtomicInteger();
        var preview = LivePreview.debounced("test-poke", running, 100,
                LivePreviewTest::testImage, settings, _ -> calls.incrementAndGet());

        preview.poke();
        waitFor(() -> calls.get() > 0, 5000);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void stoppingRunningFlag_terminatesLoop() throws Exception {
        var calls = new AtomicInteger();
        var localRunning = new AtomicBoolean(true);
        LivePreview.continuous("test-stop", localRunning, () -> true,
                LivePreviewTest::testImage, settings, _ -> calls.incrementAndGet());

        waitFor(() -> calls.get() > 0, 5000);
        localRunning.set(false);
        Thread.sleep(300);
        int after = calls.get();
        Thread.sleep(500);
        assertThat(calls.get()).isEqualTo(after);
    }
}
