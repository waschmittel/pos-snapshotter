package de.flubba;

import java.awt.image.BufferedImage;
import java.io.IOException;

public final class PrintWorkflow {

    public static final int PRINTER_WIDTH = 512;

    private final SettingsStore settings;
    private final Printer printer;

    public PrintWorkflow(SettingsStore settings, Printer printer) {
        this.settings = settings;
        this.printer = printer;
    }

    public void print(BufferedImage image, Orientation orientation) throws IOException {
        String printerName = settings.currentPrinterName();
        if (printerName == null || printerName.isEmpty()) {
            throw new IOException("No printer selected");
        }
        var chunks = DitherPipeline.render(image, orientation, settings.currentDitherParams());
        printer.print(printerName, chunks);
    }

    public void printFitted(BufferedImage image) throws IOException {
        boolean landscape = image.getWidth() > image.getHeight();
        BufferedImage scaled = landscape
                ? ImageScaler.scaleToHeight(image, PRINTER_WIDTH)
                : ImageScaler.scaleToWidth(image, PRINTER_WIDTH);
        print(scaled, landscape ? Orientation.LANDSCAPE : Orientation.PORTRAIT);
    }
}
