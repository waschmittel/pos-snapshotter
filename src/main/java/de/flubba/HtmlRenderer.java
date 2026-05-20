package de.flubba;

import javax.swing.JTextPane;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.View;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class HtmlRenderer {
    private static final int PRINTER_WIDTH = 512;

    public static BufferedImage render(String html) {
        if (html == null || html.isEmpty()) return null;

        // Create a local kit to be thread-safe and avoid default margin issues
        HTMLEditorKit kit = new HTMLEditorKit();
        // Root issue: default Swing HTML stylesheet has 8px margins on <body>
        kit.getStyleSheet().addRule("body { margin: 0; padding: 0; }");

        JTextPane offscreen = new JTextPane();
        offscreen.setEditorKit(kit);
        offscreen.setContentType("text/html");
        offscreen.setBorder(null);
        offscreen.setMargin(new java.awt.Insets(0, 0, 0, 0));
        
        // Root issue: Asynchronous loading can cause partial rendering/measuring
        offscreen.getDocument().putProperty("com.sun.java.swing.text.html.NoAsync", Boolean.TRUE);
        offscreen.setText(html);
        
        // Trigger layout with the target width
        offscreen.setSize(PRINTER_WIDTH, 1000000); 
        var ui = offscreen.getUI();
        var rootView = ui.getRootView(offscreen);
        rootView.setSize(PRINTER_WIDTH, 1000000);
        
        // Measure exact required height (preferredSpan returns float, need ceil for full coverage)
        int height = (int) Math.ceil(rootView.getPreferredSpan(View.Y_AXIS));
        
        if (height <= 0) height = 1;

        offscreen.setSize(PRINTER_WIDTH, height);

        BufferedImage img = new BufferedImage(PRINTER_WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, PRINTER_WIDTH, height);
        
        // printAll is more robust for offscreen rendering than paint()
        offscreen.printAll(g2);
        
        g2.dispose();
        return img;
    }
}
