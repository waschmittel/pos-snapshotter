package de.flubba;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

class Dithering {

    // Empirical grayscale levels from printer calibration (epson-multi-tone LUT).
    // Each entry = perceived brightness [0=black, 1=white] that the corresponding printer level produces.
    // Index 0 = darkest (byte 0, printer level 15), index 11 = lightest (byte 176, printer level 4).
    static final double[] EMPIRICAL_LEVELS = {
            0.000,  // printer level 15 (black)
            0.018,  // printer level 14 (interpolated)
            0.035,  // printer level 13
            0.176,  // printer level 12
            0.212,  // printer level 11
            0.384,  // printer level 10
            0.420,  // printer level 9
            0.616,  // printer level 8
            0.824,  // printer level 7
            0.949,  // printer level 6
            0.984,  // printer level 5
            1.000,  // printer level 4 (white)
    };

    // Byte values sent to printer for each level index (maps to 4-bit thermal head control)
    static final int[] LEVEL_TO_BYTE = {0, 16, 32, 48, 64, 80, 96, 112, 128, 144, 160, 176};

    // number of histogram bins, histogram resolution (256 matches 8-bit depth)
    static final int CLAHE_NUM_BINS = 256;

    record DitherResult(double[][] ditheredPixels, int[][] mappedBytes) {}

    // --- Pipeline entry points (callers go through DitherPipeline) ---

    static List<BufferedImage> toDitheredChunks(BufferedImage image, DitherParams params) {
        return doDitherChunks(image, params, true);
    }

    static List<BufferedImage> toDitheredChunksPortrait(BufferedImage image, DitherParams params) {
        return doDitherChunks(image, params, false);
    }

    static BufferedImage toDitheredImage(BufferedImage image, DitherParams params) {
        var pixels = preprocess(image, params);
        var activeLevels = getActiveLevels(params.grayLevels());
        var result = applyErrorDiffusion(pixels, params, activeLevels, null);
        return toImage(result.ditheredPixels());
    }

    // --- Shared pipeline ---

    private static List<BufferedImage> doDitherChunks(BufferedImage image, DitherParams params, boolean landscape) {
        var pixels = preprocess(image, params);
        var activeLevels = getActiveLevels(params.grayLevels());
        var activeBytes = getActiveBytes(params.grayLevels());
        var result = applyErrorDiffusion(pixels, params, activeLevels, activeBytes);
        var output = landscape ? rotate90CW(result.mappedBytes()) : result.mappedBytes();
        return chunkAndConvertToBufferedImages(output);
    }

    static double[][] preprocess(BufferedImage image, DitherParams params) {
        var pixels = convertToGrayscale(image);
        applyCLAHE(pixels, params);
        applyContrast(pixels, params.contrast());
        applySharpen(pixels, params.sharpness());
        applyGammaCorrection(pixels, params.preDitheringGamma());
        return pixels;
    }

    // --- Error diffusion (unified) ---

    static DitherResult applyErrorDiffusion(double[][] pixels, DitherParams params,
                                            double[] activeLevels, int[] activeBytes) {
        var matrix = params.diffusionMatrix().matrix;
        int width = pixels[0].length;
        int height = pixels.length;
        int matrixHeight = matrix.length;
        int matrixWidth = matrix[0].length;
        int offsetX = matrixWidth / 2;

        int[][] mappedBytes = activeBytes != null ? new int[height][width] : null;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double oldValue = pixels[y][x];
                int nearestIndex = findNearestLevelIndex(oldValue, activeLevels);
                double nearestValue = activeLevels[nearestIndex];
                double error = oldValue - nearestValue;

                pixels[y][x] = nearestValue;
                if (mappedBytes != null) {
                    mappedBytes[y][x] = activeBytes[nearestIndex];
                }

                for (int matrixY = 0; matrixY < matrixHeight; matrixY++) {
                    for (int matrixX = 0; matrixX < matrixWidth; matrixX++) {
                        int diffusionY = y + matrixY;
                        int diffusionX = x + matrixX - offsetX;
                        if (diffusionY >= 0 && diffusionY < height && diffusionX >= 0 && diffusionX < width) {
                            pixels[diffusionY][diffusionX] += error * matrix[matrixY][matrixX];
                        }
                    }
                }
            }
        }
        return new DitherResult(pixels, mappedBytes);
    }

    // --- Pixel operations ---

    static double[][] convertToGrayscale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double[][] pixels = new double[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int a = (rgb >> 24) & 0xFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                double alpha = a / 255.0;
                // Blend with white background: color * alpha + white * (1 - alpha)
                double red = r * alpha + 255.0 * (1.0 - alpha);
                double green = g * alpha + 255.0 * (1.0 - alpha);
                double blue = b * alpha + 255.0 * (1.0 - alpha);

                pixels[y][x] = (0.6 * red + 0.2 * green + 0.2 * blue) / 255.0;
            }
        }
        return pixels;
    }

    static void applyContrast(double[][] pixels, double contrast) {
        if (contrast == 1.0) return;
        for (int y = 0; y < pixels.length; y++) {
            for (int x = 0; x < pixels[y].length; x++) {
                pixels[y][x] = Math.max(0.0, Math.min(1.0, 0.5 + (pixels[y][x] - 0.5) * contrast));
            }
        }
    }

    static void applySharpen(double[][] pixels, double strength) {
        if (strength == 0.0) return;
        int height = pixels.length;
        int width = pixels[0].length;

        double[][] original = new double[height][width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(pixels[y], 0, original[y], 0, width);
        }

        // Unsharp mask: sharpened = original + strength * (original - blurred)
        // Using 3x3 box blur as the blur kernel
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                double center = original[y][x];
                double neighbors = original[y - 1][x] + original[y + 1][x]
                        + original[y][x - 1] + original[y][x + 1];
                double detail = center - neighbors / 4.0;
                pixels[y][x] = Math.max(0.0, Math.min(1.0, center + strength * detail));
            }
        }
    }

    static void applyGammaCorrection(double[][] pixels, double gamma) {
        if (gamma == 1.0) return;
        for (int y = 0; y < pixels.length; y++) {
            for (int x = 0; x < pixels[y].length; x++) {
                pixels[y][x] = Math.pow(pixels[y][x], gamma);
            }
        }
    }

    static int findNearestLevelIndex(double value, double[] levels) {
        int nearestIndex = 0;
        double minDiff = Math.abs(value - levels[0]);
        for (int i = 1; i < levels.length; i++) {
            double diff = Math.abs(value - levels[i]);
            if (diff < minDiff) {
                minDiff = diff;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    // --- Level subsampling ---

    static double[] getActiveLevels(int grayLevels) {
        if (grayLevels >= EMPIRICAL_LEVELS.length) return EMPIRICAL_LEVELS;
        var levels = new double[grayLevels];
        for (int i = 0; i < grayLevels; i++) {
            int srcIndex = Math.round((float) i * (EMPIRICAL_LEVELS.length - 1) / (grayLevels - 1));
            levels[i] = EMPIRICAL_LEVELS[srcIndex];
        }
        return levels;
    }

    static int[] getActiveBytes(int grayLevels) {
        if (grayLevels >= LEVEL_TO_BYTE.length) return LEVEL_TO_BYTE;
        var bytes = new int[grayLevels];
        for (int i = 0; i < grayLevels; i++) {
            int srcIndex = Math.round((float) i * (LEVEL_TO_BYTE.length - 1) / (grayLevels - 1));
            bytes[i] = LEVEL_TO_BYTE[srcIndex];
        }
        return bytes;
    }

    // --- Image conversion ---

    static int[][] rotate90CW(int[][] matrix) {
        int rows = matrix.length;
        int cols = matrix[0].length;
        int[][] rotated = new int[cols][rows];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                rotated[j][rows - 1 - i] = matrix[i][j];
            }
        }
        return rotated;
    }

    static ArrayList<BufferedImage> chunkAndConvertToBufferedImages(int[][] pixels) {
        var chunks = new ArrayList<BufferedImage>();
        int maxHeight = 200;
        int numChunks = (int) Math.ceil((double) pixels.length / maxHeight);

        for (int chunk = 0; chunk < numChunks; chunk++) {
            int startY = chunk * maxHeight;
            int endY = Math.min((chunk + 1) * maxHeight, pixels.length);
            int chunkHeight = endY - startY;

            int[][] chunkPixels = new int[chunkHeight][pixels[0].length];
            for (int y = 0; y < chunkHeight; y++) {
                System.arraycopy(pixels[startY + y], 0, chunkPixels[y], 0, pixels[0].length);
            }
            chunks.add(toImage(chunkPixels));
        }
        return chunks;
    }

    static BufferedImage toImage(double[][] pixels) {
        int width = pixels[0].length;
        int height = pixels.length;
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int grayscaleVal = clamp((int) (pixels[y][x] * 255));
                result.setRGB(x, y, new Color(grayscaleVal, grayscaleVal, grayscaleVal).getRGB());
            }
        }
        return result;
    }

    static BufferedImage toImage(int[][] pixels) {
        int width = pixels[0].length;
        int height = pixels.length;
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int grayscaleVal = clamp(pixels[y][x]);
                result.setRGB(x, y, new Color(grayscaleVal, grayscaleVal, grayscaleVal).getRGB());
            }
        }
        return result;
    }

    static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    // --- CLAHE ---

    static void applyCLAHE(double[][] pixels, DitherParams params) {
        int height = pixels.length;
        int width = pixels[0].length;

        int tilesX = params.claheTilesX();
        int tilesY = Math.max(1, (int) Math.round(tilesX * (double) height / width));

        // Build a CDF lookup for each tile
        double[][][] cdfs = new double[tilesY][tilesX][CLAHE_NUM_BINS];

        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                int y0 = ty * height / tilesY;
                int y1 = (ty + 1) * height / tilesY;
                int x0 = tx * width / tilesX;
                int x1 = (tx + 1) * width / tilesX;
                int tilePixels = (y1 - y0) * (x1 - x0);

                // Build histogram
                double[] hist = new double[CLAHE_NUM_BINS];
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        int bin = Math.min((int) (pixels[y][x] * CLAHE_NUM_BINS), CLAHE_NUM_BINS - 1);
                        hist[bin]++;
                    }
                }

                // Clip histogram and redistribute
                double limit = params.claheClipLimit() * tilePixels / CLAHE_NUM_BINS;
                double excess = 0;
                for (int i = 0; i < CLAHE_NUM_BINS; i++) {
                    if (hist[i] > limit) {
                        excess += hist[i] - limit;
                        hist[i] = limit;
                    }
                }
                double increment = excess / CLAHE_NUM_BINS;
                for (int i = 0; i < CLAHE_NUM_BINS; i++) {
                    hist[i] += increment;
                }

                // Build CDF
                cdfs[ty][tx][0] = hist[0];
                for (int i = 1; i < CLAHE_NUM_BINS; i++) {
                    cdfs[ty][tx][i] = cdfs[ty][tx][i - 1] + hist[i];
                }
                // Normalize CDF to [0,1]
                for (int i = 0; i < CLAHE_NUM_BINS; i++) {
                    cdfs[ty][tx][i] /= tilePixels;
                }
            }
        }

        // Map each pixel by bilinear interpolation of surrounding tile CDFs
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int bin = Math.min((int) (pixels[y][x] * CLAHE_NUM_BINS), CLAHE_NUM_BINS - 1);

                double tyCentre = ((double) y / height) * tilesY - 0.5;
                double txCentre = ((double) x / width) * tilesX - 0.5;

                int ty1 = (int) Math.floor(tyCentre);
                int ty2 = ty1 + 1;
                int tx1 = (int) Math.floor(txCentre);
                int tx2 = tx1 + 1;

                double fy = tyCentre - ty1;
                double fx = txCentre - tx1;

                // Clamp tile indices
                ty1 = Math.max(0, Math.min(ty1, tilesY - 1));
                ty2 = Math.max(0, Math.min(ty2, tilesY - 1));
                tx1 = Math.max(0, Math.min(tx1, tilesX - 1));
                tx2 = Math.max(0, Math.min(tx2, tilesX - 1));

                // Bilinear interpolation
                double val = (1 - fy) * ((1 - fx) * cdfs[ty1][tx1][bin] + fx * cdfs[ty1][tx2][bin])
                        + fy * ((1 - fx) * cdfs[ty2][tx1][bin] + fx * cdfs[ty2][tx2][bin]);

                pixels[y][x] = Math.max(0.0, Math.min(1.0, val));
            }
        }
    }
}
