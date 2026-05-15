package de.flubba;

import lombok.extern.slf4j.Slf4j;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class Dithering {
    private static final boolean WRITE_DEBUG_IMAGES = false; // TODO: make this configurable
    private static final AtomicInteger DEBUG_IMAGE_NO = new AtomicInteger(0);

    // Empirical grayscale levels from printer calibration (epson-multi-tone LUT).
    // Each entry = perceived brightness [0=black, 1=white] that the corresponding printer level produces.
    // Index 0 = darkest (byte 0, printer level 15), index 11 = lightest (byte 176, printer level 4).
    private static final double[] EMPIRICAL_LEVELS = {
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
    private static final int[] LEVEL_TO_BYTE = {0, 16, 32, 48, 64, 80, 96, 112, 128, 144, 160, 176};

    // number of histogram bins, histogram resolution (256 matches 8-bit depth)
    public static final int CLAHE_NUM_BINS = 256;

    public static List<BufferedImage> toDitheredChunks(BufferedImage image, DitherParams params) throws IOException {
        var pixels = convertToGrayscale(image);
        writeDebugImage(pixels, "converted_to_grayscale");
        applyCLAHE(pixels, params);
        writeDebugImage(pixels, "clahe_applied");
        applySharpen(pixels, params.sharpness());
        applyGammaCorrection(pixels, params.preDitheringGamma());
        writeDebugImage(pixels, "pre_dithering_gamma_corrected");
        var pixels255 = applyErrorDiffusionDitheringAndMapToBytes(pixels, params);
        return chunkAndConvertToBufferedImages(transpose(pixels255));
    }

    public static int[][] transpose(int[][] matrix) {
        int rows = matrix.length;
        int cols = matrix[0].length;

        int[][] transposed = new int[cols][rows];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                transposed[j][i] = matrix[i][j];
            }
        }
        return transposed;
    }

    public static BufferedImage toDitheredImage(BufferedImage image, DitherParams params) {
        var pixels = convertToGrayscale(image);
        applyCLAHE(pixels, params);
        applySharpen(pixels, params.sharpness());
        applyGammaCorrection(pixels, params.preDitheringGamma());
        applyErrorDiffusionDithering(pixels, params);
        // pixels now contain perceptual brightness values from EMPIRICAL_LEVELS
        return toImage(pixels);
    }

    private static ArrayList<BufferedImage> chunkAndConvertToBufferedImages(int[][] pixels) {
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

    private static void writeDebugImage(double[][] pixels, String name) {
    }

    private static BufferedImage toImage(double[][] pixels) {
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

    private static BufferedImage toImage(int[][] pixels) {
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

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static double[][] convertToGrayscale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double[][] pixels = new double[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                pixels[y][x] = (0.6 * r + 0.2 * g + 0.2 * b) / 255.0; // TODO: maybe make the grayscale weights adjustable
            }
        }
        return pixels;
    }

    private static void applySharpen(double[][] pixels, double strength) {
        if (strength == 0.0) return;
        int height = pixels.length;
        int width = pixels[0].length;

        // Copy original for reading while writing sharpened values
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

    private static void applyGammaCorrection(double[][] pixels, double gamma) {
        if (gamma == 1.0) return;
        for (int y = 0; y < pixels.length; y++) {
            for (int x = 0; x < pixels[y].length; x++) {
                pixels[y][x] = Math.pow(pixels[y][x], gamma);
            }
        }
    }

    private static void applyErrorDiffusionDithering(double[][] pixels, DitherParams params) {
        var matrix = params.diffusionMatrix().matrix;

        int width = pixels[0].length;
        int height = pixels.length;

        int matrixHeight = matrix.length;
        int matrixWidth = matrix[0].length;
        int offsetX = matrixWidth / 2;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double oldValue = pixels[y][x];
                int nearestIndex = findNearestLevelIndex(oldValue);
                double nearestValue = EMPIRICAL_LEVELS[nearestIndex];
                double error = oldValue - nearestValue;
                // Store perceptual brightness for preview display
                pixels[y][x] = nearestValue;

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
    }

    private static int[][] applyErrorDiffusionDitheringAndMapToBytes(double[][] pixels, DitherParams params) {
        var result = new int[pixels.length][pixels[0].length];
        var matrix = params.diffusionMatrix().matrix;

        int width = pixels[0].length;
        int height = pixels.length;

        int matrixHeight = matrix.length;
        int matrixWidth = matrix[0].length;
        int offsetX = matrixWidth / 2;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double oldValue = pixels[y][x];
                int nearestIndex = findNearestLevelIndex(oldValue);
                double nearestValue = EMPIRICAL_LEVELS[nearestIndex];
                double error = oldValue - nearestValue;
                result[y][x] = LEVEL_TO_BYTE[nearestIndex];

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
        return result;
    }

    private static int findNearestLevelIndex(double value) {
        int nearestIndex = 0;
        double minDiff = Math.abs(value - EMPIRICAL_LEVELS[0]);
        for (int i = 1; i < EMPIRICAL_LEVELS.length; i++) {
            double diff = Math.abs(value - EMPIRICAL_LEVELS[i]);
            if (diff < minDiff) {
                minDiff = diff;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    /**
     * Contrast Limited Adaptive Histogram Equalization (CLAHE).
     * Operates on the double[][] pixels array (values 0.0–1.0).
     * CLAHE_TILES_Y is derived from CLAHE_TILES_X to match the image aspect ratio.
     *
     * @param pixels grayscale image, values in [0,1]
     */
    private static void applyCLAHE(double[][] pixels, DitherParams params) {
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

                // Find the tile-centre coordinates this pixel falls between
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
