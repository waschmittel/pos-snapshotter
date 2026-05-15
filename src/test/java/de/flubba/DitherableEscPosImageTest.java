package de.flubba;

import com.github.anastaciocintra.escpos.image.CoffeeImageImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class DitherableEscPosImageTest {

    @Test
    void getRasterSizeInBytes_8x1_equals1() {
        BufferedImage img = new BufferedImage(8, 1, BufferedImage.TYPE_BYTE_GRAY);
        var escImg = new DitherableEscPosImage(new CoffeeImageImpl(img));
        assertThat(escImg.getRasterSizeInBytes()).isEqualTo(1);
    }

    @Test
    void getRasterSizeInBytes_9x1_equals2() {
        // 9 pixels / 8 bits = 1.125 → ceil = 2
        BufferedImage img = new BufferedImage(9, 1, BufferedImage.TYPE_BYTE_GRAY);
        var escImg = new DitherableEscPosImage(new CoffeeImageImpl(img));
        // Actually: ceil(9 * 1 / 8) = 2
        assertThat(escImg.getRasterSizeInBytes()).isEqualTo(2);
    }

    @Test
    void getRasterSizeInBytes_16x2_equals4() {
        BufferedImage img = new BufferedImage(16, 2, BufferedImage.TYPE_BYTE_GRAY);
        var escImg = new DitherableEscPosImage(new CoffeeImageImpl(img));
        assertThat(escImg.getRasterSizeInBytes()).isEqualTo(4);
    }

    @Test
    void getRasterBytesByColorIndex_blackPixel_allLayersSet() {
        BufferedImage img = singleGrayPixelImage(0); // black
        var escImg = new DitherableEscPosImage(new CoffeeImageImpl(img));
        for (int colorIndex = 0; colorIndex < 4; colorIndex++) {
            byte[] bytes = escImg.getRasterBytesByColorIndex(colorIndex).toByteArray();
            // Single pixel in MSB of first byte → bit 7 set → value 128
            assertThat(bytes[0] & 0xFF).isEqualTo(128);
        }
    }

    @Test
    void getRasterBytesByColorIndex_whitePixel_layer0NotSet() {
        BufferedImage img = singleGrayPixelImage(255); // white
        var escImg = new DitherableEscPosImage(new CoffeeImageImpl(img));
        byte[] bytes = escImg.getRasterBytesByColorIndex(0).toByteArray();
        // 255 < 128 is false → bit not set → 0
        assertThat(bytes[0] & 0xFF).isEqualTo(0);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 128",   // black, layer 0: 0 < 128 → 1
            "0, 1, 128",   // black, layer 1: 0 % 128 = 0 < 64 → 1
            "0, 2, 128",   // black, layer 2: 0 % 64 = 0 < 32 → 1
            "0, 3, 128",   // black, layer 3: 0 % 32 = 0 < 16 → 1
            "255, 0, 0",   // white, layer 0: 255 < 128 → 0
            "127, 0, 128", // 127, layer 0: 127 < 128 → 1
            "128, 0, 0",   // 128, layer 0: 128 < 128 → 0
    })
    void getRasterBytesByColorIndex_knownValues(int grayLevel, int colorIndex, int expectedByte) {
        BufferedImage img = singleGrayPixelImage(grayLevel);
        var escImg = new DitherableEscPosImage(new CoffeeImageImpl(img));
        byte[] bytes = escImg.getRasterBytesByColorIndex(colorIndex).toByteArray();
        assertThat(bytes[0] & 0xFF).isEqualTo(expectedByte);
    }

    @Test
    void getRasterBytesByColorIndex_multiplePixels_packedCorrectly() {
        // 8 pixels: alternating black/white
        BufferedImage img = new BufferedImage(8, 1, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 8; x++) {
            img.setRGB(x, 0, x % 2 == 0 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
        }
        var escImg = new DitherableEscPosImage(new CoffeeImageImpl(img));
        byte[] bytes = escImg.getRasterBytesByColorIndex(0).toByteArray();
        // Black pixels at even positions → bits 7,5,3,1 set → 0b10101010 = 170
        assertThat(bytes[0] & 0xFF).isEqualTo(0b10101010);
    }

    private static BufferedImage singleGrayPixelImage(int gray) {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, new Color(gray, gray, gray).getRGB());
        return img;
    }
}
