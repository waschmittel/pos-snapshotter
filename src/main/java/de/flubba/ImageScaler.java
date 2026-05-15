package de.flubba;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class ImageScaler {

    /**
     * Scale image to fill target dimensions exactly (stretching allowed).
     * Used for camera frames where the aspect ratio mismatch is acceptable.
     */
    public static BufferedImage scaleToFill(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2 = result.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        g2.dispose();
        return result;
    }

    /**
     * Scale image to fit within target dimensions, preserving aspect ratio.
     * Letterboxes with black if aspect ratios differ.
     * Used for file images where distortion is unacceptable.
     */
    public static BufferedImage scaleToFit(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2 = result.createGraphics();
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, targetWidth, targetHeight);

        double scale = Math.min((double) targetWidth / source.getWidth(), (double) targetHeight / source.getHeight());
        int w = (int) (source.getWidth() * scale);
        int h = (int) (source.getHeight() * scale);
        int x = (targetWidth - w) / 2;
        int y = (targetHeight - h) / 2;

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(source, x, y, w, h, null);
        g2.dispose();
        return result;
    }

    /**
     * Scale image so width equals targetWidth, maintaining aspect ratio.
     * Height is determined by the source aspect ratio.
     * Used for printer output where width is fixed and height is unlimited.
     */
    public static BufferedImage scaleToWidth(BufferedImage source, int targetWidth) {
        double scale = (double) targetWidth / source.getWidth();
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        return scaleToFill(source, targetWidth, targetHeight);
    }

    /**
     * Scale image so height equals targetHeight, maintaining aspect ratio.
     * Width is determined by the source aspect ratio.
     * Used when a landscape image will be transposed for printing.
     */
    public static BufferedImage scaleToHeight(BufferedImage source, int targetHeight) {
        double scale = (double) targetHeight / source.getHeight();
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        return scaleToFill(source, targetWidth, targetHeight);
    }
}
