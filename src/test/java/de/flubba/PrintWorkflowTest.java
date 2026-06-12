package de.flubba;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrintWorkflowTest {

    private Preferences testPrefs;
    private SettingsStore settings;
    private RecordingPrinter printer;
    private PrintWorkflow workflow;

    @BeforeEach
    void setUp() throws BackingStoreException {
        testPrefs = Preferences.userRoot().node("/test/printWorkflow");
        testPrefs.clear();
        settings = new SettingsStore(testPrefs);
        printer = new RecordingPrinter();
        workflow = new PrintWorkflow(settings, printer);
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        testPrefs.removeNode();
    }

    @Test
    void print_withoutSelectedPrinter_throws() {
        var image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        assertThatThrownBy(() -> workflow.print(image, Orientation.PORTRAIT))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No printer selected");
        assertThat(printer.jobs).isEmpty();
    }

    @Test
    void print_sendsDitheredChunksToSelectedPrinter() throws IOException {
        settings.updatePrinterName("EPSON TM-T88VII");
        var image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);

        workflow.print(image, Orientation.PORTRAIT);

        assertThat(printer.lastPrinterName).isEqualTo("EPSON TM-T88VII");
        assertThat(printer.jobs).hasSize(1);
        assertThat(printer.jobs.getFirst()).isNotEmpty();
    }

    @Test
    void printFitted_landscapeImage_isScaledToPrinterWidthHeight() throws IOException {
        settings.updatePrinterName("EPSON TM-T88VII");
        var wide = new BufferedImage(1000, 400, BufferedImage.TYPE_INT_RGB);

        workflow.printFitted(wide);

        assertThat(printer.jobs).hasSize(1);
        // landscape chunks are rotated to printer width
        for (BufferedImage chunk : printer.jobs.getFirst()) {
            assertThat(chunk.getWidth()).isEqualTo(PrintWorkflow.PRINTER_WIDTH);
        }
    }

    @Test
    void printFitted_portraitImage_isScaledToPrinterWidth() throws IOException {
        settings.updatePrinterName("EPSON TM-T88VII");
        var tall = new BufferedImage(400, 1000, BufferedImage.TYPE_INT_RGB);

        workflow.printFitted(tall);

        assertThat(printer.jobs).hasSize(1);
        for (BufferedImage chunk : printer.jobs.getFirst()) {
            assertThat(chunk.getWidth()).isEqualTo(PrintWorkflow.PRINTER_WIDTH);
        }
    }
}
