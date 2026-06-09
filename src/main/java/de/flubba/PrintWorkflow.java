package de.flubba;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.function.Supplier;

public final class PrintWorkflow {

    public static final int PRINTER_WIDTH = 512;

    private final SettingsStore settings;
    private final Supplier<String> printerSupplier;

    public PrintWorkflow(SettingsStore settings, Supplier<String> printerSupplier) {
        this.settings = settings;
        this.printerSupplier = printerSupplier;
    }

    public void print(BufferedImage image, Orientation orientation) throws IOException {
        var chunks = DitherPipeline.render(image, orientation, settings.currentDitherParams());
        PrinterService.print(printerSupplier.get(), chunks);
    }

    public void printFitted(BufferedImage image) throws IOException {
        boolean landscape = image.getWidth() > image.getHeight();
        BufferedImage scaled = landscape
                ? ImageScaler.scaleToHeight(image, PRINTER_WIDTH)
                : ImageScaler.scaleToWidth(image, PRINTER_WIDTH);
        print(scaled, landscape ? Orientation.LANDSCAPE : Orientation.PORTRAIT);
    }
}
