package de.flubba;

import javax.swing.JTextPane;
import javax.swing.text.Document;
import javax.swing.text.View;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class HtmlToImageRenderer {

    public static final int PRINTER_WIDTH = 512;

    private HtmlToImageRenderer() {
    }

    public static BufferedImage render(String html, int width) {
        if (html == null || html.isEmpty()) return null;
        JTextPane pane = newOffscreenPane();
        pane.setText(html);
        return paintToImage(pane, width);
    }

    public static BufferedImage render(Document document, int width) {
        if (document == null || document.getLength() == 0) return null;
        JTextPane pane = newOffscreenPane();
        pane.setDocument(document);
        return paintToImage(pane, width);
    }

    private static JTextPane newOffscreenPane() {
        HTMLEditorKit kit = new HTMLEditorKit();
        // Default Swing HTML stylesheet adds 8px body margins; remove for printer alignment
        kit.getStyleSheet().addRule("body { margin: 0; padding: 0; }");
        JTextPane pane = new JTextPane();
        pane.setEditorKit(kit);
        pane.setContentType("text/html");
        pane.setBorder(null);
        pane.setMargin(new Insets(0, 0, 0, 0));
        return pane;
    }

    private static BufferedImage paintToImage(JTextPane pane, int width) {
        // Async loading can cause partial measuring
        pane.getDocument().putProperty("com.sun.java.swing.text.html.NoAsync", Boolean.TRUE);

        pane.setSize(width, 1_000_000);
        var rootView = pane.getUI().getRootView(pane);
        rootView.setSize(width, 1_000_000);

        int height = Math.max(1, (int) Math.ceil(rootView.getPreferredSpan(View.Y_AXIS)));
        pane.setSize(width, height);

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        // printAll is more robust than paint() for offscreen rendering
        pane.printAll(g2);
        g2.dispose();
        return img;
    }
}
