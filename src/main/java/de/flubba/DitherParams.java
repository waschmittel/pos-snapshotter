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
}
