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
        DitherParams result = store.currentDitherParams();
        DitherParams defaults = DitherParams.defaults();
        assertThat(result).isEqualTo(defaults);
    }

    @Test
    void updateThenRead_returnsUpdatedParams() {
        var custom = new DitherParams(DiffusionMatrix.FLOYD_STEINBERG, 1.5, 2.0, 1.5, 8, 10, 3.0);
        store.updateDitherParams(custom);
        assertThat(store.currentDitherParams()).isEqualTo(custom);
    }

    @Test
    void update_persistsAcrossInstances() {
        var custom = new DitherParams(DiffusionMatrix.FLOYD_STEINBERG, 1.5, 2.0, 1.5, 8, 10, 3.0);
        store.updateDitherParams(custom);
        var reopened = new SettingsStore(testPrefs);
        assertThat(reopened.currentDitherParams()).isEqualTo(custom);
    }

    @Test
    void update_clampsOutOfRangeValues() {
        var outOfRange = new DitherParams(DiffusionMatrix.FLOYD_STEINBERG, 99.0, -1.0, 0.0, 100, 0, 99.0);
        store.updateDitherParams(outOfRange);
        assertThat(store.currentDitherParams()).isEqualTo(outOfRange.clamped());
    }

    @Test
    void resetThenRead_returnsDefaults() {
        var custom = new DitherParams(DiffusionMatrix.JARVIS_JUDICE_NINKE, 2.0, 1.0, 2.0, 6, 3, 5.0);
        store.updateDitherParams(custom);
        store.resetDitherParams();
        assertThat(store.currentDitherParams()).isEqualTo(DitherParams.defaults());
        assertThat(new SettingsStore(testPrefs).currentDitherParams()).isEqualTo(DitherParams.defaults());
    }

    @Test
    void printerName_defaultIsNull() {
        assertThat(store.currentPrinterName()).isNull();
    }

    @Test
    void printerName_updateThenRead() {
        store.updatePrinterName("EPSON TM-T88VII");
        assertThat(store.currentPrinterName()).isEqualTo("EPSON TM-T88VII");
    }

    @Test
    void printerName_persistsAcrossInstances() {
        store.updatePrinterName("EPSON TM-T88VII");
        assertThat(new SettingsStore(testPrefs).currentPrinterName()).isEqualTo("EPSON TM-T88VII");
    }

    @Test
    void printerName_updateToNull_clears() {
        store.updatePrinterName("EPSON TM-T88VII");
        store.updatePrinterName(null);
        assertThat(store.currentPrinterName()).isNull();
        assertThat(new SettingsStore(testPrefs).currentPrinterName()).isNull();
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
    void lastTab_defaultIsZero() {
        assertThat(store.loadLastTab()).isEqualTo(0);
    }

    @Test
    void lastTab_saveAndLoad() {
        store.saveLastTab(2);
        assertThat(store.loadLastTab()).isEqualTo(2);
    }

    @Test
    void updateThenRead_allDiffusionMatrices() {
        for (DiffusionMatrix matrix : DiffusionMatrix.values()) {
            var params = new DitherParams(matrix, 0.8, 3.0, 1.0, 12, 5, 1.0);
            store.updateDitherParams(params);
            assertThat(new SettingsStore(testPrefs).currentDitherParams().diffusionMatrix()).isEqualTo(matrix);
        }
    }
}
