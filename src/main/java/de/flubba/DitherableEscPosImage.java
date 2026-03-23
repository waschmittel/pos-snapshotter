package de.flubba;

import com.github.anastaciocintra.escpos.image.CoffeeImage;
import com.github.anastaciocintra.escpos.image.EscPosImage;

import java.io.ByteArrayOutputStream;

public class DitherableEscPosImage extends EscPosImage {
    /**
     * creates an EscPosImage
     *
     * @param image normal RGB image
     * @see #getBitonalVal(int, int)
     */
    public DitherableEscPosImage(CoffeeImage image) {
        super(image, null);
    }

    public ByteArrayOutputStream getRasterBytesByColorIndex(int colorIndex) {
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        int Byte;
        int bit;
        for (int y = 0; y < image.getHeight(); y++) {
            Byte = 0;
            bit = 0;
            for (int x = 0; x < image.getWidth(); x++) {
                int val = getBitonalValWithColorIndex(x, y, colorIndex);
                Byte = Byte | (val << (7 - bit));
                bit++;
                if (bit == 8) {
                    byteArray.write(Byte);
                    Byte = 0;
                    bit = 0;
                }
            }
            if (bit > 0) {
                byteArray.write(Byte);
            }

        }
        return byteArray;
    }

    private int getBitonalValWithColorIndex(int x, int y, int colorIndex) {
        int RGBA = image.getRGB(x, y);
        // it's grayscale anyway, so we just pick the red channel
        int red = (RGBA >> 16) & 0xFF;

        if (colorIndex == 0) {
            return red < 128 ? 1 : 0;
        }
        if (colorIndex == 1) {
            return red % 128 < 64 ? 1 : 0;
        }
        if (colorIndex == 2) {
            return red % 64 < 32 ? 1 : 0;
        }
        return red % 32 < 16 ? 1 : 0;
    }

    public int getRasterSizeInBytes() { // re-implemented because the original implementation uses the non-existent
        return (int) Math.ceil(image.getHeight() * image.getWidth() / 8d);
    }


}
