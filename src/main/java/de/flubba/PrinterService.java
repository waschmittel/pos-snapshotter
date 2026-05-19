package de.flubba;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.image.CoffeeImageImpl;
import com.github.anastaciocintra.escpos.image.EscPosImage;
import com.github.anastaciocintra.output.PrinterOutputStream;
import lombok.extern.slf4j.Slf4j;

import javax.print.PrintService;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class PrinterService {

    public static String[] getAvailablePrinters() {
        return PrinterOutputStream.getListPrintServicesNames();
    }

    public static void print(String printerName, List<BufferedImage> chunks) throws IOException {
        if (printerName == null || printerName.isEmpty()) {
            throw new IOException("No printer selected");
        }

        PrintService printService = PrinterOutputStream.getPrintServiceByName(printerName);
        if (printService == null) {
            throw new IOException("Printer not found: " + printerName);
        }

        PrinterOutputStream printerOutputStream = new PrinterOutputStream(printService);

        try (EscPos escpos = new EscPos(printerOutputStream)) {
            for (var chunk : chunks) {
                printLogoGraphicsImage(escpos, chunk);
            }
            escpos.feed(5);
            escpos.feed(3);
            escpos.cut(EscPos.CutMode.FULL);
        }
    }

    private static void printLogoGraphicsImage(EscPos escpos, BufferedImage chunk) throws IOException {
        EscPosImage escposImage = new DitherableEscPosImage(new CoffeeImageImpl(chunk));
        escpos.write(new DitheredEpsonGrayscaleImageWrapper(), escposImage);
    }

    public static String findDefaultPrinter() {
        return Arrays.stream(getAvailablePrinters())
                .filter(name -> name.toLowerCase().contains("epson"))
                .findFirst()
                .orElse(null);
    }
}
