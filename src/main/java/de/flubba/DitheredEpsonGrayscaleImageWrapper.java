/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
package de.flubba;

import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.image.EscPosImage;
import com.github.anastaciocintra.escpos.image.ImageWrapperInterface;

import java.io.ByteArrayOutputStream;

/**
 * Supply ESC/POS Graphics print Image commands.<p>
 * using <code>GS(L</code>
 */
public class DitheredEpsonGrayscaleImageWrapper implements EscPosConst, ImageWrapperInterface<DitheredEpsonGrayscaleImageWrapper> {
    protected Justification justification;


    public DitheredEpsonGrayscaleImageWrapper() {
        justification = Justification.Left_Default;
    }

    /**
     * Set horizontal justification
     *
     * @param justification left, center or right
     * @return this object
     */
    public DitheredEpsonGrayscaleImageWrapper setJustification(Justification justification) {
        this.justification = justification;
        return this;
    }

    /**
     * Bit Image commands Assembly into ESC/POS bytes. <p>
     * <p>
     * Select justification <p>
     * ASCII ESC a n <p>
     * <p>
     * function 112 Store the graphics data in the print buffer  <p>
     * GS(L pL pH m fn a bx by c xL xH yL yH d1...dk  <p>
     * <p>
     * function 050 Prints the buffered graphics data <p>
     * GS ( L pL pH m fn  <p>
     *
     * @param image to be printed
     * @return bytes of ESC/POS
     * @see EscPosImage#getRasterBytes()
     * @see EscPosImage#getRasterSizeInBytes()
     */
    @Override
    public byte[] getBytes(EscPosImage image) {
        if (image instanceof DitherableEscPosImage ditherableEscPosImage) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            //
            bytes.write(ESC);
            bytes.write('a');
            bytes.write(justification.value);
            //
            int paramSize = image.getRasterSizeInBytes() + 10;

            var colors = new int[]{49, 50, 51, 52};

            for (int bitMapLayer = 0; bitMapLayer < colors.length; bitMapLayer++) {

                // Lots of almost-duplication from GraphicsImageWrapper here for writing out an image
                int pL = paramSize & 0xFF;
                int pH = (paramSize & 0xFF00) >> 8;

                bytes.write(GS);
                bytes.write('(');
                bytes.write('L');
                bytes.write(pL); // pl
                bytes.write(pH); // ph
                bytes.write(48); // m
                bytes.write(112); //fn
                bytes.write(52); // a
                bytes.write(1); // bx -- normal 1x width
                bytes.write(1); // by -- normal 1x height
                bytes.write(colors[bitMapLayer]); // c

                //  bits in horizontal direction for the bit image
                int horizontalBits = image.getWidthOfImageInBits();
                int xL = horizontalBits & 0xFF;
                int xH = (horizontalBits & 0xFF00) >> 8;
                //
                //  bits in vertical direction for the bit image
                int verticalBits = image.getHeightOfImageInBits();
                // getting first and second bytes separated
                int yL = verticalBits & 0xFF;
                int yH = (verticalBits & 0xFF00) >> 8;

                bytes.write(xL);
                bytes.write(xH);
                bytes.write(yL);
                bytes.write(yH);
                // write raster bytes for this color layer
                byte[] rasterBytes = ditherableEscPosImage.getRasterBytesByColorIndex(bitMapLayer).toByteArray();
                bytes.write(rasterBytes, 0, rasterBytes.length);
            }

            // function 050
            bytes.write(GS);
            bytes.write('(');
            bytes.write('L');
            bytes.write(2); // pl
            bytes.write(0); // ph
            bytes.write(48); //m
            bytes.write(50); //fn


            //
            return bytes.toByteArray();
        } else {
            throw new IllegalArgumentException("Image must be of type DitherableEscPosImage");
        }
    }

}
