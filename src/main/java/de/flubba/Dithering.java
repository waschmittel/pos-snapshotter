package de.flubba;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
public class Dithering {
    private static final boolean WRITE_DEBUG_IMAGES = false; // TODO: make this configurable
    private static final AtomicInteger DEBUG_IMAGE_NO = new AtomicInteger(0);

    // more steps means finer grays, but also dependent on printer
    private static final int GRAY_LEVELS = 16;

    // number of histogram bins, histogram resolution (256 matches 8-bit depth)
    public static final int CLAHE_NUM_BINS = 256;

    public static List<BufferedImage> toDitheredChunks(BufferedImage image, DitherParams params) throws IOException {
        var pixels = convertToGrayscale(image);
        writeDebugImage(pixels, "converted_to_grayscale");
        applyCLAHE(pixels, params);
        writeDebugImage(pixels, "clahe_applied");
        applyGammaCorrection(pixels, params.preDitheringGamma());
        writeDebugImage(pixels, "pre_dithering_gamma_corrected");
        applyErrorDiffusionDithering(pixels, params);
        applyGammaCorrection(pixels, 1 / params.preDitheringGamma());
        writeDebugImage(pixels, "dithered");
        //applyGammaCorrection(pixels, params.ditheringGamma()); //TODO: the DitherableEscPosImage should know about the gamma values
        writeDebugImage(pixels, "gamma_corrected");
        return chunkAndConvertToBufferedImages(transpose(pixels));
    }

    public static double[][] transpose(double[][] matrix) {
        int rows = matrix.length;
        int cols = matrix[0].length;

        double[][] transposed = new double[cols][rows];

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
        applyGammaCorrection(pixels, params.preDitheringGamma());
        applyErrorDiffusionDithering(pixels, params);
        applyGammaCorrection(pixels, params.ditheringGamma());
        return toImage(pixels);
    }

    private static ArrayList<BufferedImage> chunkAndConvertToBufferedImages(double[][] pixels) {
        var chunks = new ArrayList<BufferedImage>();
        int maxHeight = 200;

        int numChunks = (int) Math.ceil((double) pixels.length / maxHeight);

        for (int chunk = 0; chunk < numChunks; chunk++) {
            int startY = chunk * maxHeight;
            int endY = Math.min((chunk + 1) * maxHeight, pixels.length);
            int chunkHeight = endY - startY;

            double[][] chunkPixels = new double[chunkHeight][pixels[0].length];
            for (int y = 0; y < chunkHeight; y++) {
                System.arraycopy(pixels[startY + y], 0, chunkPixels[y], 0, pixels[0].length);
            }

            chunks.add(toImage(chunkPixels));
        }
        return chunks;
    }

    private static void writeDebugImage(double[][] pixels, String name) {
        if (WRITE_DEBUG_IMAGES) {
            try {
                ImageIO.write(toImage(pixels), "png", new File("%s_%s.png".formatted(DEBUG_IMAGE_NO.getAndIncrement(), name)));
            } catch (IOException e) {
                log.error("Failed to write debug image: " + e.getMessage());
            }
        }
    }

    private static BufferedImage toImage(double[][] pixels) {
        int width = pixels[0].length;
        int height = pixels.length;
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                var grayscaleVal = clamp((int) (pixels[y][x] * 255));
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
                int g = (rgb >>  8) & 0xFF;
                int b =  rgb        & 0xFF;
                pixels[y][x] = (0.6 * r + 0.2 * g + 0.2 * b) / 255.0; // TODO: maybe make the grayscale weights adjustable
            }
        }
        return pixels;
    }

    private static void applyGammaCorrection(double[][] pixels, double gamma) {
        for (int y = 0; y < pixels.length; y++) {
            for (int x = 0; x < pixels[y].length; x++) {
                double linear = Math.pow(Math.pow(pixels[y][x], gamma), gamma);
                pixels[y][x] = linear;
            }
        }
    }

    private static void applyErrorDiffusionDithering(double[][] pixels, DitherParams params) {
        var matrix = params.diffusionMatrix().matrix;
        double[] grayscaleLevels = computeGrayscaleLevels();

        int width = pixels[0].length;
        int height = pixels.length;

        int matrixHeight = matrix.length;
        int matrixWidth = matrix[0].length;
        int offsetX = matrixWidth / 2;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double oldValue = pixels[y][x];
                double newValue = findNearestLevel(oldValue);
                double error = oldValue - newValue;
                pixels[y][x] = newValue;

                for (int matrixY = 0; matrixY < matrixHeight; matrixY++) {
                    for (int matrixX = 0; matrixX < matrixWidth; matrixX++) {
                        int diffusionY = y + matrixY;
                        int diffusionX = x + matrixX - offsetX; // TODO: why this offset?
                        if (diffusionY >= 0 && diffusionY < height && diffusionX >= 0 && diffusionX < width) {
                            pixels[diffusionY][diffusionX] += error * matrix[matrixY][matrixX];
                        }
                    }
                }
            }
        }
        writeDebugImage(pixels, "dithered");
    }

    private static double findNearestLevel(double value) {
        double nearest = GRAYSCALE_LEVELS[0];
        double minDiff = value <= nearest ? nearest - value : value - nearest;
        for (int i = 1; i < GRAYSCALE_LEVELS.length; i++) {
            double diff = value <= GRAYSCALE_LEVELS[i] ? GRAYSCALE_LEVELS[i] - value : value - GRAYSCALE_LEVELS[i];
            if (diff < minDiff) {
                minDiff = diff;
                nearest = GRAYSCALE_LEVELS[i];
            }
        }
        return nearest;
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
