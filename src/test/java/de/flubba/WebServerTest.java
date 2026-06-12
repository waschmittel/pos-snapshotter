package de.flubba;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;

class WebServerTest {
    private WebServer webServer;
    private HttpClient client;
    private final int testPort = 8081;
    private Preferences tempPrefs;
    private SettingsStore settingsStore;
    private RecordingPrinter printer;

    @BeforeEach
    void setUp() throws IOException {
        tempPrefs = Preferences.userRoot().node("PosSnapshotterTest");
        settingsStore = new SettingsStore(tempPrefs);
        printer = new RecordingPrinter();
        webServer = new WebServer(testPort, settingsStore, printer);
        webServer.start();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        webServer.stop();
        tempPrefs.removeNode();
    }

    @Test
    void testFontsEndpoint() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + testPort + "/fonts"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(response.body()).startsWith("[").endsWith("]");
    }

    @Test
    void testPreviewEndpoint() throws IOException, InterruptedException {
        String html = "<h1>Test Preview</h1><p>Hello world</p>";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + testPort + "/preview"))
                .POST(HttpRequest.BodyPublishers.ofString(html))
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains("image/png");
        assertThat(response.body().length).isGreaterThan(0);
    }

    @Test
    void testStaticHandler() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + testPort + "/index.html"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("<title>POS Snapshotter - Web</title>");
    }

    @Test
    void testPrintEndpoint_sendsJobToPrinter() throws IOException, InterruptedException {
        settingsStore.updatePrinterName("EPSON TM-T88VII");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + testPort + "/print"))
                .POST(HttpRequest.BodyPublishers.ofString("<h1>Receipt</h1>"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(printer.lastPrinterName).isEqualTo("EPSON TM-T88VII");
        assertThat(printer.jobs).hasSize(1);
        assertThat(printer.jobs.getFirst()).isNotEmpty();
    }

    @Test
    void testPrintEndpoint_noPrinterSelected_returns500() throws IOException, InterruptedException {
        settingsStore.updatePrinterName(null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + testPort + "/print"))
                .POST(HttpRequest.BodyPublishers.ofString("<h1>Receipt</h1>"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(printer.jobs).isEmpty();
    }

    @Test
    void testPrintEndpointInvalidHtml() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + testPort + "/print"))
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
    }
}
