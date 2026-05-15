package de.flubba;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsStoreTest {
    private Preferences testPrefs;
    private SettingsStore store;

    @BeforeEach
    void setUp() throws BackingStoreException {
        testPrefs = Preferences.userRoot().node("/test/posSnapshotter");
        testPrefs.clear();
        store = new SettingsStore(testPrefs);
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        testPrefs.removeNode();
    }

    @Test
    void loadFromEmptyPrefs_returnsDefaults() {
        DitherParams result = store.loadDitherParams();
        DitherParams defaults = DitherParams.defaults();
        assertThat(result).isEqualTo(defaults);
    }

    @Test
    void saveThenLoad_roundTrip() {
        var custom = new DitherParams(DiffusionMatrix.FLOYD_STEINBERG, 1.5, 2.0, 1.5, 8, 10, 3.0);
        store.saveDitherParams(custom);
        DitherParams loaded = store.loadDitherParams();
        assertThat(loaded).isEqualTo(custom);
    }

    @Test
    void resetThenLoad_returnsDefaults() {
        var custom = new DitherParams(DiffusionMatrix.JARVIS_JUDICE_NINKE, 2.0, 1.0, 2.0, 6, 3, 5.0);
        store.saveDitherParams(custom);
        store.resetDitherParams();
        DitherParams loaded = store.loadDitherParams();
        assertThat(loaded).isEqualTo(DitherParams.defaults());
    }

    @Test
    void cameraIndex_saveAndLoad() {
        store.saveCameraIndex(2);
        assertThat(store.loadCameraIndex()).isEqualTo(2);
    }

    @Test
    void cameraIndex_defaultIsZero() {
        assertThat(store.loadCameraIndex()).isEqualTo(0);
    }

    @Test
    void cameraIndex_independentOfDitherParams() {
        store.saveCameraIndex(3);
        store.resetDitherParams();
        // Camera index should survive a dither params reset
        assertThat(store.loadCameraIndex()).isEqualTo(3);
    }

    @Test
    void lastImageDirectory_default_isUserHome() {
        assertThat(store.loadLastImageDirectory()).isEqualTo(System.getProperty("user.home"));
    }

    @Test
    void lastImageDirectory_saveAndLoad() {
        store.saveLastImageDirectory("/tmp/test/images");
        assertThat(store.loadLastImageDirectory()).isEqualTo("/tmp/test/images");
    }

    @Test
    void saveThenLoad_allDiffusionMatrices() {
        for (DiffusionMatrix matrix : DiffusionMatrix.values()) {
            var params = new DitherParams(matrix, 0.8, 3.0, 1.0, 12, 5, 1.0);
            store.saveDitherParams(params);
            DitherParams loaded = store.loadDitherParams();
            assertThat(loaded.diffusionMatrix()).isEqualTo(matrix);
        }
    }
}
