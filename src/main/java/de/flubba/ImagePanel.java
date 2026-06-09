package de.flubba;

import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class ImagePanel extends JPanel {
    private volatile BufferedImage currentImage;
    private final String placeholderMessage;

    public ImagePanel(String placeholderMessage) {
        this.placeholderMessage = placeholderMessage;
    }

    public void updateImage(BufferedImage image) {
        this.currentImage = image;
        repaint();
    }

    public BufferedImage getCurrentImage() {
        return currentImage;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        if (currentImage != null) {
            double scale = Math.min(
                    (double) getWidth() / currentImage.getWidth(),
                    (double) getHeight() / currentImage.getHeight());
            int w = (int) (currentImage.getWidth() * scale);
            int h = (int) (currentImage.getHeight() * scale);
            int x = (getWidth() - w) / 2;
            int y = (getHeight() - h) / 2;

            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(currentImage, x, y, w, h, null);
        } else {
            Color bg = getBackground();
            if (bg == null) bg = new Color(0x2B2B2B);
            g2.setColor(bg);
            g2.fillRect(0, 0, getWidth(), getHeight());

            boolean darkBg = (bg.getRed() + bg.getGreen() + bg.getBlue()) / 3 < 128;
            g2.setColor(darkBg ? new Color(0xE0E0E0) : new Color(0x707070));
            Font baseFont = UIManager.getFont("Label.font");
            if (baseFont == null) baseFont = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
            g2.setFont(baseFont.deriveFont(Font.PLAIN, 18f));
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(placeholderMessage,
                    (getWidth() - fm.stringWidth(placeholderMessage)) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
        }
    }
}
