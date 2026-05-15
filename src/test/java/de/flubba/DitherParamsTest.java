package de.flubba;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DitherParamsTest {

    @Test
    void defaults_hasValidValues() {
        var d = DitherParams.defaults();
        assertThat(d.diffusionMatrix()).isNotNull();
        assertThat(d.preDitheringGamma()).isBetween(0.1, 3.0);
        assertThat(d.sharpness()).isBetween(0.0, 5.0);
        assertThat(d.contrast()).isBetween(0.5, 3.0);
        assertThat(d.grayLevels()).isBetween(2, 12);
        assertThat(d.claheTilesX()).isBetween(1, 32);
        assertThat(d.claheClipLimit()).isBetween(1.0, 8.0);
    }

    @Test
    void recordEquality() {
        var a = new DitherParams(DiffusionMatrix.SIERRA_LITE, 0.8, 3.0, 1.0, 12, 5, 1.0);
        var b = new DitherParams(DiffusionMatrix.SIERRA_LITE, 0.8, 3.0, 1.0, 12, 5, 1.0);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void recordInequality_differentMatrix() {
        var a = DitherParams.defaults();
        var b = new DitherParams(DiffusionMatrix.FLOYD_STEINBERG, a.preDitheringGamma(), a.sharpness(),
                a.contrast(), a.grayLevels(), a.claheTilesX(), a.claheClipLimit());
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void defaults_returnsNewInstanceEachTime() {
        var a = DitherParams.defaults();
        var b = DitherParams.defaults();
        assertThat(a).isEqualTo(b);
    }
}
