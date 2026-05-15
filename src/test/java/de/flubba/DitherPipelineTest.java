package de.flubba;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DitherPipelineTest {

    // --- convertToGrayscale ---

    @Test
    void convertToGrayscale_pureRed_returns06() {
        var image = singlePixelImage(255, 0, 0);
        double[][] result = Dithering.convertToGrayscale(image);
        assertThat(result[0][0]).isCloseTo(0.6, within(0.001));
    }

    @Test
    void convertToGrayscale_pureGreen_returns02() {
        var image = singlePixelImage(0, 255, 0);
        double[][] result = Dithering.convertToGrayscale(image);
        assertThat(result[0][0]).isCloseTo(0.2, within(0.001));
    }

    @Test
    void convertToGrayscale_pureBlue_returns02() {
        var image = singlePixelImage(0, 0, 255);
        double[][] result = Dithering.convertToGrayscale(image);
        assertThat(result[0][0]).isCloseTo(0.2, within(0.001));
    }

    @Test
    void convertToGrayscale_white_returns1() {
        var image = singlePixelImage(255, 255, 255);
        double[][] result = Dithering.convertToGrayscale(image);
        assertThat(result[0][0]).isCloseTo(1.0, within(0.001));
    }

    @Test
    void convertToGrayscale_black_returns0() {
        var image = singlePixelImage(0, 0, 0);
        double[][] result = Dithering.convertToGrayscale(image);
        assertThat(result[0][0]).isCloseTo(0.0, within(0.001));
    }

    @Test
    void convertToGrayscale_1x1Image_returnsCorrectShape() {
        var image = singlePixelImage(128, 128, 128);
        double[][] result = Dithering.convertToGrayscale(image);
        assertThat(result).hasNumberOfRows(1);
        assertThat(result[0]).hasSize(1);
    }

    // --- applyGammaCorrection ---

    @Test
    void applyGamma_1_noChange() {
        double[][] pixels = {{0.5, 0.25}};
        double[][] copy = {{0.5, 0.25}};
        Dithering.applyGammaCorrection(pixels, 1.0);
        assertThat(pixels).isDeepEqualTo(copy);
    }

    @Test
    void applyGamma_2_squaresValues() {
        double[][] pixels = {{0.5}};
        Dithering.applyGammaCorrection(pixels, 2.0);
        assertThat(pixels[0][0]).isCloseTo(0.25, within(0.001));
    }

    @Test
    void applyGamma_05_squareRoots() {
        double[][] pixels = {{0.25}};
        Dithering.applyGammaCorrection(pixels, 0.5);
        assertThat(pixels[0][0]).isCloseTo(0.5, within(0.001));
    }

    @Test
    void applyGamma_zeroPixel_staysZero() {
        double[][] pixels = {{0.0}};
        Dithering.applyGammaCorrection(pixels, 2.0);
        assertThat(pixels[0][0]).isEqualTo(0.0);
    }

    @Test
    void applyGamma_onePixel_staysOne() {
        double[][] pixels = {{1.0}};
        Dithering.applyGammaCorrection(pixels, 2.0);
        assertThat(pixels[0][0]).isCloseTo(1.0, within(0.001));
    }

    // --- applyContrast ---

    @Test
    void applyContrast_1_noChange() {
        double[][] pixels = {{0.3, 0.7}};
        double[][] copy = {{0.3, 0.7}};
        Dithering.applyContrast(pixels, 1.0);
        assertThat(pixels).isDeepEqualTo(copy);
    }

    @Test
    void applyContrast_2_expandsFromMiddle() {
        double[][] pixels = {{0.75}};
        Dithering.applyContrast(pixels, 2.0);
        // 0.5 + (0.75 - 0.5) * 2.0 = 1.0
        assertThat(pixels[0][0]).isCloseTo(1.0, within(0.001));
    }

    @Test
    void applyContrast_clampsAt0() {
        double[][] pixels = {{0.1}};
        Dithering.applyContrast(pixels, 3.0);
        // 0.5 + (0.1 - 0.5) * 3.0 = 0.5 - 1.2 = -0.7 → clamped to 0
        assertThat(pixels[0][0]).isEqualTo(0.0);
    }

    @Test
    void applyContrast_clampsAt1() {
        double[][] pixels = {{0.9}};
        Dithering.applyContrast(pixels, 3.0);
        // 0.5 + (0.9 - 0.5) * 3.0 = 0.5 + 1.2 = 1.7 → clamped to 1
        assertThat(pixels[0][0]).isEqualTo(1.0);
    }

    @Test
    void applyContrast_middlePixel_staysMiddle() {
        double[][] pixels = {{0.5}};
        Dithering.applyContrast(pixels, 2.0);
        assertThat(pixels[0][0]).isCloseTo(0.5, within(0.001));
    }

    // --- applySharpen ---

    @Test
    void applySharpen_0_noChange() {
        double[][] pixels = {{0.1, 0.2, 0.3}, {0.4, 0.5, 0.6}, {0.7, 0.8, 0.9}};
        double[][] copy = new double[3][3];
        for (int y = 0; y < 3; y++) System.arraycopy(pixels[y], 0, copy[y], 0, 3);
        Dithering.applySharpen(pixels, 0.0);
        assertThat(pixels).isDeepEqualTo(copy);
    }

    @Test
    void applySharpen_uniformImage_noChange() {
        double[][] pixels = {{0.5, 0.5, 0.5}, {0.5, 0.5, 0.5}, {0.5, 0.5, 0.5}};
        Dithering.applySharpen(pixels, 3.0);
        // Interior pixel: center=0.5, neighbors all 0.5, detail=0.5-0.5=0 → no change
        assertThat(pixels[1][1]).isCloseTo(0.5, within(0.001));
    }

    @Test
    void applySharpen_borderPixels_unchanged() {
        double[][] pixels = {{0.1, 0.2, 0.3}, {0.4, 0.5, 0.6}, {0.7, 0.8, 0.9}};
        double topLeft = pixels[0][0];
        double bottomRight = pixels[2][2];
        Dithering.applySharpen(pixels, 3.0);
        // Border pixels should not be modified
        assertThat(pixels[0][0]).isEqualTo(topLeft);
        assertThat(pixels[2][2]).isEqualTo(bottomRight);
    }

    @Test
    void applySharpen_known3x3_centerSharpened() {
        // Center=0.8, all 4 neighbors=0.2
        // detail = 0.8 - 0.8/4.0 = 0.8 - 0.2 = 0.6
        // sharpened = 0.8 + 1.0 * 0.6 = 1.4 → clamped to 1.0
        double[][] pixels = {{0.5, 0.2, 0.5}, {0.2, 0.8, 0.2}, {0.5, 0.2, 0.5}};
        Dithering.applySharpen(pixels, 1.0);
        assertThat(pixels[1][1]).isCloseTo(1.0, within(0.001));
    }

    // --- applyCLAHE ---

    @Test
    void applyCLAHE_uniformImage_staysApproxUniform() {
        double[][] pixels = new double[10][10];
        for (var row : pixels) java.util.Arrays.fill(row, 0.5);
        var params = new DitherParams(DiffusionMatrix.SIERRA_LITE, 0.8, 3.0, 1.0, 12, 5, 1.0);
        Dithering.applyCLAHE(pixels, params);
        // After CLAHE on uniform image, all pixels should be approximately the same
        for (var row : pixels) {
            for (double v : row) {
                assertThat(v).isBetween(0.0, 1.0);
            }
        }
    }

    @Test
    void applyCLAHE_singleTile_noError() {
        double[][] pixels = {{0.0, 0.5}, {0.5, 1.0}};
        var params = new DitherParams(DiffusionMatrix.SIERRA_LITE, 0.8, 3.0, 1.0, 12, 1, 1.0);
        Dithering.applyCLAHE(pixels, params);
        for (var row : pixels) {
            for (double v : row) {
                assertThat(v).isBetween(0.0, 1.0);
            }
        }
    }

    // --- findNearestLevelIndex ---

    @Test
    void findNearest_exactMatch() {
        double[] levels = {0.0, 0.5, 1.0};
        assertThat(Dithering.findNearestLevelIndex(0.5, levels)).isEqualTo(1);
    }

    @Test
    void findNearest_betweenLevels_closerToSecond() {
        double[] levels = {0.0, 0.5, 1.0};
        assertThat(Dithering.findNearestLevelIndex(0.6, levels)).isEqualTo(1);
    }

    @Test
    void findNearest_value0() {
        double[] levels = {0.0, 1.0};
        assertThat(Dithering.findNearestLevelIndex(0.0, levels)).isEqualTo(0);
    }

    @Test
    void findNearest_value1() {
        double[] levels = {0.0, 1.0};
        assertThat(Dithering.findNearestLevelIndex(1.0, levels)).isEqualTo(1);
    }

    @Test
    void findNearest_negativeValue_returnsIndex0() {
        double[] levels = {0.0, 0.5, 1.0};
        assertThat(Dithering.findNearestLevelIndex(-0.5, levels)).isEqualTo(0);
    }

    @Test
    void findNearest_valueAbove1_returnsLastIndex() {
        double[] levels = {0.0, 0.5, 1.0};
        assertThat(Dithering.findNearestLevelIndex(1.5, levels)).isEqualTo(2);
    }

    // --- getActiveLevels ---

    @Test
    void getActiveLevels_12_returnsFullArray() {
        double[] result = Dithering.getActiveLevels(12);
        assertThat(result).isSameAs(Dithering.EMPIRICAL_LEVELS);
    }

    @Test
    void getActiveLevels_greaterThan12_returnsFullArray() {
        double[] result = Dithering.getActiveLevels(20);
        assertThat(result).isSameAs(Dithering.EMPIRICAL_LEVELS);
    }

    @Test
    void getActiveLevels_2_returnsFirstAndLast() {
        double[] result = Dithering.getActiveLevels(2);
        assertThat(result).hasSize(2);
        assertThat(result[0]).isEqualTo(Dithering.EMPIRICAL_LEVELS[0]);
        assertThat(result[1]).isEqualTo(Dithering.EMPIRICAL_LEVELS[Dithering.EMPIRICAL_LEVELS.length - 1]);
    }

    @Test
    void getActiveLevels_3_includesFirstMiddleLast() {
        double[] result = Dithering.getActiveLevels(3);
        assertThat(result).hasSize(3);
        assertThat(result[0]).isEqualTo(Dithering.EMPIRICAL_LEVELS[0]);
        assertThat(result[2]).isEqualTo(Dithering.EMPIRICAL_LEVELS[11]);
    }

    // --- getActiveBytes ---

    @Test
    void getActiveBytes_12_returnsFullArray() {
        int[] result = Dithering.getActiveBytes(12);
        assertThat(result).isSameAs(Dithering.LEVEL_TO_BYTE);
    }

    @Test
    void getActiveBytes_2_returnsFirstAndLast() {
        int[] result = Dithering.getActiveBytes(2);
        assertThat(result).hasSize(2);
        assertThat(result[0]).isEqualTo(0);
        assertThat(result[1]).isEqualTo(176);
    }

    @Test
    void getActiveLevels_and_getActiveBytes_sameIndices() {
        for (int n = 2; n <= 12; n++) {
            double[] levels = Dithering.getActiveLevels(n);
            int[] bytes = Dithering.getActiveBytes(n);
            assertThat(levels).hasSameSizeAs(bytes);
        }
    }

    // --- transpose ---

    @Test
    void transpose_3x5_flips() {
        int[][] matrix = {
                {1, 2, 3, 4, 5},
                {6, 7, 8, 9, 10},
                {11, 12, 13, 14, 15}
        };
        int[][] result = Dithering.transpose(matrix);
        assertThat(result).hasNumberOfRows(5);
        assertThat(result[0]).hasSize(3);
        assertThat(result[0][0]).isEqualTo(1);
        assertThat(result[4][2]).isEqualTo(15);
        assertThat(result[2][1]).isEqualTo(8);
    }

    @Test
    void transpose_singleRow_becomesSingleColumn() {
        int[][] matrix = {{1, 2, 3}};
        int[][] result = Dithering.transpose(matrix);
        assertThat(result).hasNumberOfRows(3);
        assertThat(result[0]).containsExactly(1);
        assertThat(result[1]).containsExactly(2);
        assertThat(result[2]).containsExactly(3);
    }

    @Test
    void transpose_singleElement() {
        int[][] matrix = {{42}};
        int[][] result = Dithering.transpose(matrix);
        assertThat(result).isDeepEqualTo(new int[][]{{42}});
    }

    @Test
    void transpose_square_symmetric() {
        int[][] matrix = {{1, 2}, {3, 4}};
        int[][] result = Dithering.transpose(matrix);
        assertThat(result).isDeepEqualTo(new int[][]{{1, 3}, {2, 4}});
    }

    @Test
    void transpose_doubleTranspose_identity() {
        int[][] matrix = {{1, 2, 3}, {4, 5, 6}};
        int[][] result = Dithering.transpose(Dithering.transpose(matrix));
        assertThat(result).isDeepEqualTo(matrix);
    }

    // --- chunkAndConvertToBufferedImages ---

    @Test
    void chunk_heightUnder200_singleChunk() {
        int[][] pixels = new int[100][50];
        List<BufferedImage> chunks = Dithering.chunkAndConvertToBufferedImages(pixels);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().getHeight()).isEqualTo(100);
        assertThat(chunks.getFirst().getWidth()).isEqualTo(50);
    }

    @Test
    void chunk_height400_twoChunks() {
        int[][] pixels = new int[400][50];
        List<BufferedImage> chunks = Dithering.chunkAndConvertToBufferedImages(pixels);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getHeight()).isEqualTo(200);
        assertThat(chunks.get(1).getHeight()).isEqualTo(200);
    }

    @Test
    void chunk_height201_twoChunksWithRemainder() {
        int[][] pixels = new int[201][50];
        List<BufferedImage> chunks = Dithering.chunkAndConvertToBufferedImages(pixels);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getHeight()).isEqualTo(200);
        assertThat(chunks.get(1).getHeight()).isEqualTo(1);
    }

    @Test
    void chunk_exactlyMaxHeight_singleChunk() {
        int[][] pixels = new int[200][50];
        List<BufferedImage> chunks = Dithering.chunkAndConvertToBufferedImages(pixels);
        assertThat(chunks).hasSize(1);
    }

    // --- clamp ---

    @Test
    void clamp_withinRange() {
        assertThat(Dithering.clamp(128)).isEqualTo(128);
    }

    @Test
    void clamp_belowZero() {
        assertThat(Dithering.clamp(-10)).isEqualTo(0);
    }

    @Test
    void clamp_above255() {
        assertThat(Dithering.clamp(300)).isEqualTo(255);
    }

    @Test
    void clamp_boundaries() {
        assertThat(Dithering.clamp(0)).isEqualTo(0);
        assertThat(Dithering.clamp(255)).isEqualTo(255);
    }

    // --- toImage (double[][]) ---

    @Test
    void toImage_double_black() {
        double[][] pixels = {{0.0}};
        BufferedImage img = Dithering.toImage(pixels);
        int gray = (img.getRGB(0, 0) >> 16) & 0xFF;
        assertThat(gray).isEqualTo(0);
    }

    @Test
    void toImage_double_white() {
        double[][] pixels = {{1.0}};
        BufferedImage img = Dithering.toImage(pixels);
        int gray = (img.getRGB(0, 0) >> 16) & 0xFF;
        assertThat(gray).isEqualTo(255);
    }

    // --- toImage (int[][]) ---

    @Test
    void toImage_int_midGray() {
        int[][] pixels = {{128}};
        BufferedImage img = Dithering.toImage(pixels);
        int gray = (img.getRGB(0, 0) >> 16) & 0xFF;
        assertThat(gray).isEqualTo(128);
    }

    // --- preprocess ---

    @Test
    void preprocess_returnsArrayWithCorrectDimensions() {
        BufferedImage image = new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB);
        var params = DitherParams.defaults();
        double[][] result = Dithering.preprocess(image, params);
        assertThat(result).hasNumberOfRows(8);
        assertThat(result[0]).hasSize(16);
    }

    @Test
    void preprocess_allPixelsInRange() {
        BufferedImage image = gradientImage(32, 32);
        var params = DitherParams.defaults();
        double[][] result = Dithering.preprocess(image, params);
        for (var row : result) {
            for (double v : row) {
                assertThat(v).isBetween(0.0, 1.0);
            }
        }
    }

    // --- End-to-end characterization ---

    @Test
    void toDitheredImage_producesCorrectSizeOutput() {
        BufferedImage image = gradientImage(16, 16);
        var params = DitherParams.defaults();
        BufferedImage result = Dithering.toDitheredImage(image, params);
        assertThat(result.getWidth()).isEqualTo(16);
        assertThat(result.getHeight()).isEqualTo(16);
    }

    @Test
    void toDitheredChunks_landscape_transposesOutput() {
        BufferedImage image = gradientImage(100, 50); // wider than tall
        var params = DitherParams.defaults();
        List<BufferedImage> chunks = Dithering.toDitheredChunks(image, params);
        // After transpose, height and width swap: 100x50 → 50x100, single chunk (50<200)
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().getWidth()).isEqualTo(50);
        assertThat(chunks.getFirst().getHeight()).isEqualTo(100);
    }

    @Test
    void toDitheredChunksPortrait_noTranspose() {
        BufferedImage image = gradientImage(100, 50);
        var params = DitherParams.defaults();
        List<BufferedImage> chunks = Dithering.toDitheredChunksPortrait(image, params);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().getWidth()).isEqualTo(100);
        assertThat(chunks.getFirst().getHeight()).isEqualTo(50);
    }

    @Test
    void applyErrorDiffusion_withoutBytes_returnsNullMappedBytes() {
        double[][] pixels = {{0.0, 0.5, 1.0}};
        double[] levels = {0.0, 1.0};
        var result = Dithering.applyErrorDiffusion(pixels, DitherParams.defaults(), levels, null);
        assertThat(result.mappedBytes()).isNull();
        assertThat(result.ditheredPixels()).isNotNull();
    }

    @Test
    void applyErrorDiffusion_withBytes_returnsMappedBytes() {
        double[][] pixels = {{0.0, 0.5, 1.0}};
        double[] levels = {0.0, 1.0};
        int[] bytes = {0, 176};
        var result = Dithering.applyErrorDiffusion(pixels, DitherParams.defaults(), levels, bytes);
        assertThat(result.mappedBytes()).isNotNull();
        assertThat(result.mappedBytes()[0]).hasSize(3);
        // Each byte value should be either 0 or 176
        for (int b : result.mappedBytes()[0]) {
            assertThat(b).isIn(0, 176);
        }
    }

    // --- Helpers ---

    private static BufferedImage singlePixelImage(int r, int g, int b) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, new Color(r, g, b).getRGB());
        return image;
    }

    private static BufferedImage gradientImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = (x * 255) / Math.max(1, width - 1);
                image.setRGB(x, y, new Color(gray, gray, gray).getRGB());
            }
        }
        return image;
    }
}
