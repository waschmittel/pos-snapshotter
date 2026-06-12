package de.flubba;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

class RecordingPrinter implements Printer {

    final List<List<BufferedImage>> jobs = new ArrayList<>();
    String lastPrinterName;
    String[] available = {"EPSON TM-T88VII", "Some Other Printer"};

    @Override
    public String[] availablePrinters() {
        return available;
    }

    @Override
    public void print(String printerName, List<BufferedImage> chunks) {
        lastPrinterName = printerName;
        jobs.add(chunks);
    }
}
