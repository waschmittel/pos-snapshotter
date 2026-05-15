package de.flubba;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DiffusionMatrixTest {

    @ParameterizedTest
    @EnumSource(DiffusionMatrix.class)
    void allMatrices_weightsSum_approximately1(DiffusionMatrix matrix) {
        double sum = 0;
        for (double[] row : matrix.matrix) {
            for (double v : row) {
                sum += v;
            }
        }
        assertThat(sum).isCloseTo(1.0, within(0.01));
    }

    @Test
    void floydSteinberg_shape_2x3() {
        var m = DiffusionMatrix.FLOYD_STEINBERG;
        assertThat(m.matrix).hasNumberOfRows(2);
        assertThat(m.matrix[0]).hasSize(3);
        assertThat(m.matrix[1]).hasSize(3);
    }

    @Test
    void jarvisJudiceNinke_shape_3x5() {
        var m = DiffusionMatrix.JARVIS_JUDICE_NINKE;
        assertThat(m.matrix).hasNumberOfRows(3);
        assertThat(m.matrix[0]).hasSize(5);
    }

    @Test
    void sierraLite_shape_2x3() {
        var m = DiffusionMatrix.SIERRA_LITE;
        assertThat(m.matrix).hasNumberOfRows(2);
        assertThat(m.matrix[0]).hasSize(3);
    }

    @Test
    void flubba_shape_3x4() {
        var m = DiffusionMatrix.FLUBBA;
        assertThat(m.matrix).hasNumberOfRows(3);
        assertThat(m.matrix[0]).hasSize(4);
    }

    @Test
    void floydSteinberg_firstRow_startsWithZeros() {
        var m = DiffusionMatrix.FLOYD_STEINBERG;
        // First two entries of first row should be 0 (current pixel and left neighbor)
        assertThat(m.matrix[0][0]).isEqualTo(0.0);
        assertThat(m.matrix[0][1]).isEqualTo(0.0);
    }

    @ParameterizedTest
    @EnumSource(DiffusionMatrix.class)
    void allMatrices_noNegativeWeights(DiffusionMatrix matrix) {
        for (double[] row : matrix.matrix) {
            for (double v : row) {
                assertThat(v).isGreaterThanOrEqualTo(0.0);
            }
        }
    }
}
