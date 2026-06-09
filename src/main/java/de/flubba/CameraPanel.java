package de.flubba;

import javax.swing.Timer;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.concurrent.atomic.AtomicInteger;

public class CameraPanel extends ImagePanel {
    private volatile boolean flashing;
    private final AtomicInteger countdown = new AtomicInteger(-1);

    public CameraPanel() {
        super("Starting camera...");
    }

    public void startCountdown(int seconds, Runnable onComplete) {
        countdown.set(seconds);
        repaint();
        Timer timer = new Timer(1000, null);
        timer.addActionListener(_ -> {
            int current = countdown.decrementAndGet();
            repaint();
            if (current <= 0) {
                timer.stop();
                countdown.set(-1);
                onComplete.run();
            }
        });
        timer.setInitialDelay(1000);
        timer.start();
    }

    public void flash() {
        flashing = true;
        repaint();
        Timer flashTimer = new Timer(150, _ -> {
            flashing = false;
            repaint();
        });
        flashTimer.setRepeats(false);
        flashTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        if (flashing) {
            g2.setColor(new Color(255, 255, 255, 200));
            g2.fillRect(0, 0, getWidth(), getHeight());
        }

        int cd = countdown.get();
        if (cd > 0) {
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRect(0, 0, getWidth(), getHeight());

            String text = String.valueOf(cd);
            g2.setFont(new Font("SansSerif", Font.BOLD, 200));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (getWidth() - fm.stringWidth(text)) / 2;
            int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;

            g2.setColor(new Color(0, 0, 0, 180));
            g2.drawString(text, tx + 4, ty + 4);

            g2.setColor(new Color(255, 80, 80));
            g2.drawString(text, tx, ty);
        }
    }
}
