package de.flubba;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class ImageScalerTest {

    // --- scaleToFill ---

    @Test
    void scaleToFill_outputHasExactTargetDimensions() {
        BufferedImage source = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = ImageScaler.scaleToFill(source, 910, 512);
        assertThat(result.getWidth()).isEqualTo(910);
        assertThat(result.getHeight()).isEqualTo(512);
    }

    @Test
    void scaleToFill_1x1Source_noError() {
        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, Color.RED.getRGB());
        BufferedImage result = ImageScaler.scaleToFill(source, 100, 100);
        assertThat(result.getWidth()).isEqualTo(100);
        assertThat(result.getHeight()).isEqualTo(100);
    }

    @Test
    void scaleToFill_largeToSmall() {
        BufferedImage source = new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = ImageScaler.scaleToFill(source, 200, 100);
        assertThat(result.getWidth()).isEqualTo(200);
        assertThat(result.getHeight()).isEqualTo(100);
    }

    // --- scaleToFit ---

    @Test
    void scaleToFit_landscapeSource_widthFills_heightLetterboxed() {
        BufferedImage source = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        fillWhite(source);
        BufferedImage result = ImageScaler.scaleToFit(source, 200, 200);
        assertThat(result.getWidth()).isEqualTo(200);
        assertThat(result.getHeight()).isEqualTo(200);
        // Top-left corner should be black (letterbox)
        assertThat(getGray(result, 0, 0)).isEqualTo(0);
        // Center should not be black (contains the image)
        assertThat(getGray(result, 100, 100)).isEqualTo(255);
    }

    @Test
    void scaleToFit_portraitSource_heightFills_widthLetterboxed() {
        BufferedImage source = new BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB);
        fillWhite(source);
        BufferedImage result = ImageScaler.scaleToFit(source, 200, 200);
        assertThat(result.getWidth()).isEqualTo(200);
        assertThat(result.getHeight()).isEqualTo(200);
        // Left edge should be black (letterbox)
        assertThat(getGray(result, 0, 100)).isEqualTo(0);
        // Center should be white
        assertThat(getGray(result, 100, 100)).isEqualTo(255);
    }

    @Test
    void scaleToFit_exactRatio_noLetterbox() {
        BufferedImage source = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        fillWhite(source);
        BufferedImage result = ImageScaler.scaleToFit(source, 200, 200);
        // All corners should be white (image fills entire area)
        assertThat(getGray(result, 0, 0)).isEqualTo(255);
        assertThat(getGray(result, 199, 199)).isEqualTo(255);
    }

    @Test
    void scaleToFit_letterboxCornerPixels_areBlack() {
        BufferedImage source = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        fillWhite(source);
        BufferedImage result = ImageScaler.scaleToFit(source, 200, 200);
        // Top-left and bottom-left corners should be black letterbox
        assertThat(getGray(result, 0, 0)).isEqualTo(0);
        assertThat(getGray(result, 0, 199)).isEqualTo(0);
    }

    @Test
    void scaleToFit_1x1Source_noError() {
        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, Color.WHITE.getRGB());
        BufferedImage result = ImageScaler.scaleToFit(source, 100, 100);
        assertThat(result.getWidth()).isEqualTo(100);
        assertThat(result.getHeight()).isEqualTo(100);
    }

    @Test
    void scaleToFit_outputHasExactTargetDimensions() {
        BufferedImage source = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = ImageScaler.scaleToFit(source, 910, 512);
        assertThat(result.getWidth()).isEqualTo(910);
        assertThat(result.getHeight()).isEqualTo(512);
    }

    // --- Helpers ---

    private static void fillWhite(BufferedImage img) {
        var g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.dispose();
    }

    private static int getGray(BufferedImage img, int x, int y) {
        return (img.getRGB(x, y) >> 16) & 0xFF;
    }
}
