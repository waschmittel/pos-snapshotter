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

    // --- scaleToWidth ---

    @Test
    void scaleToWidth_outputHasExactWidth() {
        BufferedImage source = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = ImageScaler.scaleToWidth(source, 512);
        assertThat(result.getWidth()).isEqualTo(512);
    }

    @Test
    void scaleToWidth_maintainsAspectRatio() {
        BufferedImage source = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = ImageScaler.scaleToWidth(source, 512);
        // 200:100 = 2:1, so 512 wide → 256 tall
        assertThat(result.getHeight()).isEqualTo(256);
    }

    @Test
    void scaleToWidth_1x1Source_noError() {
        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = ImageScaler.scaleToWidth(source, 512);
        assertThat(result.getWidth()).isEqualTo(512);
        assertThat(result.getHeight()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void scaleToWidth_portraitImage_tallResult() {
        BufferedImage source = new BufferedImage(100, 400, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = ImageScaler.scaleToWidth(source, 512);
        assertThat(result.getWidth()).isEqualTo(512);
        // 100:400 = 1:4, so 512 wide → 2048 tall
        assertThat(result.getHeight()).isEqualTo(2048);
    }

    // --- scaleToHeight ---

    @Test
    void scaleToHeight_outputHasExactHeight() {
        BufferedImage source = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = ImageScaler.scaleToHeight(source, 512);
        assertThat(result.getHeight()).isEqualTo(512);
    }

    @Test
    void scaleToHeight_maintainsAspectRatio() {
        BufferedImage source = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = ImageScaler.scaleToHeight(source, 512);
        // 200:100 = 2:1, so 512 tall → 1024 wide
        assertThat(result.getWidth()).isEqualTo(1024);
    }

    @Test
    void scaleToHeight_1x1Source_noError() {
        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = ImageScaler.scaleToHeight(source, 512);
        assertThat(result.getHeight()).isEqualTo(512);
        assertThat(result.getWidth()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void scaleToHeight_landscapeForPrinting() {
        // Typical use: landscape 1920x1080, scale height to 512 for printing
        BufferedImage source = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = ImageScaler.scaleToHeight(source, 512);
        assertThat(result.getHeight()).isEqualTo(512);
        // 1920/1080 * 512 ≈ 910-911 (rounding)
        assertThat(result.getWidth()).isBetween(910, 911);
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
