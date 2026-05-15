package de.flubba;

import java.util.prefs.Preferences;

public record DitherParams(
        DiffusionMatrix diffusionMatrix,
        double preDitheringGamma,
        double sharpness,
        double contrast,
        int grayLevels,
        int claheTilesX,
        double claheClipLimit) {

    private static final Preferences PREFS = Preferences.userNodeForPackage(DitherParams.class);

    public static DitherParams defaults() {
        return new DitherParams(DiffusionMatrix.SIERRA_LITE, 0.8, 3.0, 1.0, 12, 5, 1.0);
    }

    public static DitherParams load() {
        var d = defaults();
        return new DitherParams(
                DiffusionMatrix.valueOf(PREFS.get("diffusionMatrix", d.diffusionMatrix.name())),
                PREFS.getDouble("preDitheringGamma", d.preDitheringGamma),
                PREFS.getDouble("sharpness", d.sharpness),
                PREFS.getDouble("contrast", d.contrast),
                PREFS.getInt("grayLevels", d.grayLevels),
                PREFS.getInt("claheTilesX", d.claheTilesX),
                PREFS.getDouble("claheClipLimit", d.claheClipLimit)
        );
    }

    public void save() {
        PREFS.put("diffusionMatrix", diffusionMatrix.name());
        PREFS.putDouble("preDitheringGamma", preDitheringGamma);
        PREFS.putDouble("sharpness", sharpness);
        PREFS.putDouble("contrast", contrast);
        PREFS.putInt("grayLevels", grayLevels);
        PREFS.putInt("claheTilesX", claheTilesX);
        PREFS.putDouble("claheClipLimit", claheClipLimit);
    }

    public static int loadCameraIndex() {
        return PREFS.getInt("cameraIndex", 0);
    }

    public static void saveCameraIndex(int index) {
        PREFS.putInt("cameraIndex", index);
    }

    public static void resetPrefs() {
        PREFS.remove("diffusionMatrix");
        PREFS.remove("preDitheringGamma");
        PREFS.remove("sharpness");
        PREFS.remove("contrast");
        PREFS.remove("grayLevels");
        PREFS.remove("claheTilesX");
        PREFS.remove("claheClipLimit");
    }
}
