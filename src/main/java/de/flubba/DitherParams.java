package de.flubba;

public record DitherParams(
        DiffusionMatrix diffusionMatrix,
        double ditheringGamma,
        double preDitheringGamma,
        int claheTilesX,
        double claheClipLimit) {

    public static DitherParams defaults() {
        return new DitherParams(DiffusionMatrix.SIERRA_LITE, 2.8, 0.3, 8, 1.5);
    }
}
