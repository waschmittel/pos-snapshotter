package de.flubba;

import java.util.prefs.Preferences;

public class SettingsStore {
    private final Preferences prefs;
    private volatile DitherParams current;

    public SettingsStore() {
        this(Preferences.userNodeForPackage(DitherParams.class));
    }

    SettingsStore(Preferences prefs) {
        this.prefs = prefs;
        this.current = loadDitherParams();
    }

    public DitherParams currentDitherParams() {
        return current;
    }

    public void updateDitherParams(DitherParams params) {
        DitherParams clamped = params.clamped();
        current = clamped;
        saveDitherParams(clamped);
    }

    public DitherParams loadDitherParams() {
        var d = DitherParams.defaults();
        return new DitherParams(
                DiffusionMatrix.valueOf(prefs.get("diffusionMatrix", d.diffusionMatrix().name())),
                prefs.getDouble("preDitheringGamma", d.preDitheringGamma()),
                prefs.getDouble("sharpness", d.sharpness()),
                prefs.getDouble("contrast", d.contrast()),
                prefs.getInt("grayLevels", d.grayLevels()),
                prefs.getInt("claheTilesX", d.claheTilesX()),
                prefs.getDouble("claheClipLimit", d.claheClipLimit())
        );
    }

    public void saveDitherParams(DitherParams params) {
        prefs.put("diffusionMatrix", params.diffusionMatrix().name());
        prefs.putDouble("preDitheringGamma", params.preDitheringGamma());
        prefs.putDouble("sharpness", params.sharpness());
        prefs.putDouble("contrast", params.contrast());
        prefs.putInt("grayLevels", params.grayLevels());
        prefs.putInt("claheTilesX", params.claheTilesX());
        prefs.putDouble("claheClipLimit", params.claheClipLimit());
        current = params;
    }

    public void resetDitherParams() {
        prefs.remove("diffusionMatrix");
        prefs.remove("preDitheringGamma");
        prefs.remove("sharpness");
        prefs.remove("contrast");
        prefs.remove("grayLevels");
        prefs.remove("claheTilesX");
        prefs.remove("claheClipLimit");
        current = DitherParams.defaults();
    }

    public int loadCameraIndex() {
        return prefs.getInt("cameraIndex", 0);
    }

    public void saveCameraIndex(int index) {
        prefs.putInt("cameraIndex", index);
    }

    public String loadLastImageDirectory() {
        return prefs.get("lastImageDirectory", System.getProperty("user.home"));
    }

    public void saveLastImageDirectory(String path) {
        prefs.put("lastImageDirectory", path);
    }

    public int loadLastTab() {
        return prefs.getInt("lastTab", 0);
    }

    public void saveLastTab(int index) {
        prefs.putInt("lastTab", index);
    }

    public String loadPrinterName() {
        return prefs.get("printerName", null);
    }

    public void savePrinterName(String name) {
        if (name == null) {
            prefs.remove("printerName");
        } else {
            prefs.put("printerName", name);
        }
    }

    public boolean loadSidebarExpanded() {
        return prefs.getBoolean("sidebarExpanded", true);
    }

    public void saveSidebarExpanded(boolean expanded) {
        prefs.putBoolean("sidebarExpanded", expanded);
    }
}
