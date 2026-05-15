package de.flubba;

import java.util.prefs.Preferences;

public class SettingsStore {
    private final Preferences prefs;

    public SettingsStore() {
        this(Preferences.userNodeForPackage(DitherParams.class));
    }

    SettingsStore(Preferences prefs) {
        this.prefs = prefs;
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
    }

    public void resetDitherParams() {
        prefs.remove("diffusionMatrix");
        prefs.remove("preDitheringGamma");
        prefs.remove("sharpness");
        prefs.remove("contrast");
        prefs.remove("grayLevels");
        prefs.remove("claheTilesX");
        prefs.remove("claheClipLimit");
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
}
