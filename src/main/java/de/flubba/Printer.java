package de.flubba;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Seam in front of the physical printer: lists available devices and prints
 * pre-dithered image chunks. Production uses {@link EscPosPrinter}; tests use a
 * recording fake.
 */
public interface Printer {

    String[] availablePrinters();

    void print(String printerName, List<BufferedImage> chunks) throws IOException;

    default String findDefaultPrinter() {
        return Arrays.stream(availablePrinters())
                .filter(name -> name.toLowerCase().contains("epson"))
                .findFirst()
                .orElse(null);
    }
}
