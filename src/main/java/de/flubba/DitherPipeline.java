package de.flubba;

import java.awt.image.BufferedImage;
import java.util.List;

public final class DitherPipeline {

    public static final int PRINTER_LAYERS = 4;

    private DitherPipeline() {
    }

    public static List<BufferedImage> render(BufferedImage image, Orientation orientation, DitherParams params) {
        return orientation == Orientation.LANDSCAPE
                ? Dithering.toDitheredChunks(image, params)
                : Dithering.toDitheredChunksPortrait(image, params);
    }

    public static BufferedImage preview(BufferedImage image, DitherParams params) {
        return Dithering.toDitheredImage(image, params);
    }
}
