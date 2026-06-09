package de.flubba;

public record DitherParams(
        DiffusionMatrix diffusionMatrix,
        double preDitheringGamma,
        double sharpness,
        double contrast,
        int grayLevels,
        int claheTilesX,
        double claheClipLimit) {

    public static DitherParams defaults() {
        return new DitherParams(DiffusionMatrix.SIERRA_LITE, 0.8, 3.0, 1.0, 12, 5, 1.0);
    }

    public DitherParams clamped() {
        return new DitherParams(
                diffusionMatrix != null ? diffusionMatrix : DiffusionMatrix.SIERRA_LITE,
                clamp(preDitheringGamma, 0.1, 3.0),
                clamp(sharpness, 0.0, 5.0),
                clamp(contrast, 0.5, 3.0),
                (int) clamp(grayLevels, 2, 12),
                (int) clamp(claheTilesX, 1, 32),
                clamp(claheClipLimit, 1.0, 8.0)
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
