package de.flubba;

public record DitherParams(
        DiffusionMatrix diffusionMatrix,
        double preDitheringGamma,
        double sharpness,
        int claheTilesX,
        double claheClipLimit) {

    public static DitherParams defaults() {
        return new DitherParams(DiffusionMatrix.SIERRA_LITE, 1.0, 2.0, 8, 1.5);
    }
}
