package de.flubba;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executors;

@Slf4j
public class WebServer {
    private final HttpServer server;
    private final SettingsStore settingsStore;
    private final PrintWorkflow printWorkflow;

    public WebServer(int port, SettingsStore settingsStore) throws IOException {
        this.settingsStore = settingsStore;
        this.printWorkflow = new PrintWorkflow(settingsStore, settingsStore::loadPrinterName);
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/", new StaticHandler());
        this.server.createContext("/print", new PrintHandler());
        this.server.createContext("/preview", new PreviewHandler());
        this.server.createContext("/fonts", new FontsHandler());
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    public void start() {
        server.start();
        log.info("Web server started on port {}", server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        log.info("Web server stopped");
    }

    private class FontsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String[] fonts = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < fonts.length; i++) {
                sb.append("\"").append(fonts[i].replace("\"", "\\\"")).append("\"");
                if (i < fonts.length - 1) sb.append(",");
            }
            sb.append("]");
            byte[] response = sb.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }

    private class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            
            String resourcePath = "/static" + path;
            try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                
                String contentType = "text/plain";
                if (path.endsWith(".html")) contentType = "text/html";
                else if (path.endsWith(".js")) contentType = "application/javascript";
                else if (path.endsWith(".css")) contentType = "text/css";

                byte[] bytes = is.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        }
    }

    private class PrintHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String html = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                BufferedImage img = HtmlToImageRenderer.render(html, HtmlToImageRenderer.PRINTER_WIDTH);
                if (img != null) {
                    printWorkflow.print(img, Orientation.PORTRAIT);

                    String response = "OK";
                    exchange.sendResponseHeaders(200, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                } else {
                    exchange.sendResponseHeaders(400, -1);
                }
            } catch (Exception e) {
                log.error("Print failed", e);
                String error = "Print failed: " + e.getMessage();
                exchange.sendResponseHeaders(500, error.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(error.getBytes());
                }
            }
        }
    }

    private class PreviewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String html = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                BufferedImage img = HtmlToImageRenderer.render(html, HtmlToImageRenderer.PRINTER_WIDTH);
                if (img != null) {
                    var params = settingsStore.loadDitherParams();
                    BufferedImage dithered = DitherPipeline.preview(img, params);
                    
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(dithered, "png", baos);
                    byte[] bytes = baos.toByteArray();
                    
                    exchange.getResponseHeaders().set("Content-Type", "image/png");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                } else {
                    exchange.sendResponseHeaders(400, -1);
                }
            } catch (Exception e) {
                log.error("Preview failed", e);
                exchange.sendResponseHeaders(500, -1);
            }
        }
    }
}
