package de.flubba;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.image.CoffeeImageImpl;
import com.github.anastaciocintra.escpos.image.EscPosImage;
import com.github.anastaciocintra.output.PrinterOutputStream;

import javax.print.PrintService;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Main {
    static void main() {
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                var frame = new SnapshotterFrame();
                frame.setVisible(true);
            } catch (Exception e) {
                throw new RuntimeException("Failed to start webcam", e);
            }
        });
    }

    public static void printIt(List<BufferedImage> chunks) throws IOException {
        var starPrinterName = Arrays.stream(PrinterOutputStream.getListPrintServicesNames())
                .filter(name -> name.toLowerCase().contains("epson"))
                .findFirst()
                .get();
        //this call is slow, try to use it only once and reuse the PrintService variable.
        PrintService printService = PrinterOutputStream.getPrintServiceByName(starPrinterName);
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

}
