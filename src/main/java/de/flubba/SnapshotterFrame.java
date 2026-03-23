package de.flubba;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class SnapshotterFrame extends JFrame {
    private final CameraPanel cameraPanel;
    private final JButton captureButton;
    private final FrameGrabber grabber;
    private final Java2DFrameConverter converter = new Java2DFrameConverter();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger countdown = new AtomicInteger(-1);
    private final AtomicReference<BufferedImage> lastSnapshot = new AtomicReference<>();

    public SnapshotterFrame() throws FrameGrabber.Exception {
        super("POS Snapshotter");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        grabber = new OpenCVFrameGrabber(0);
        grabber.setImageWidth(768);
        grabber.setImageHeight(512);
        grabber.start();
        log.info("Webcam started: {}x{}", grabber.getImageWidth(), grabber.getImageHeight());

        cameraPanel = new CameraPanel();
        cameraPanel.setPreferredSize(new Dimension(grabber.getImageWidth(), grabber.getImageHeight()));

        captureButton = new JButton("Take Photo");
        captureButton.setFont(new Font("SansSerif", Font.BOLD, 32));
        captureButton.setPreferredSize(new Dimension(0, 80));
        captureButton.setBackground(new Color(0, 120, 215));
        captureButton.setForeground(Color.WHITE);
        captureButton.setFocusPainted(false);
        captureButton.addActionListener(_ -> startCountdown());

        setLayout(new BorderLayout());
        add(cameraPanel, BorderLayout.CENTER);
        add(captureButton, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(null);

        startCameraLoop();
    }

    private void startCameraLoop() {
        Thread.ofVirtual().name("camera-loop").start(() -> {
            while (running.get()) {
                try {
                    Frame frame = grabber.grab();
                    if (frame != null) {
                        BufferedImage image = converter.convert(frame);
                        if (image != null) {
                            // make a copy since the converter reuses the buffer
                            BufferedImage copy = new BufferedImage(
                                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
                            copy.getGraphics().drawImage(image, 0, 0, null);
                            cameraPanel.updateImage(copy);
                        }
                    }
                    Thread.sleep(33); // ~30 fps
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error grabbing frame", e);
                }
            }
        });
    }

    private void startCountdown() {
        captureButton.setEnabled(false);
        countdown.set(3);
        cameraPanel.repaint();

        Timer timer = new Timer(1000, null);
        timer.addActionListener(_ -> {
            int current = countdown.decrementAndGet();
            cameraPanel.repaint();
            if (current <= 0) {
                timer.stop();
                capturePhoto();
            }
        });
        timer.setInitialDelay(1000);
        timer.start();
    }

    private void capturePhoto() {
        countdown.set(-1);
        BufferedImage snapshot = cameraPanel.getCurrentImage();
        if (snapshot != null) {
            lastSnapshot.set(snapshot);
            log.info("Photo captured ({}x{})", snapshot.getWidth(), snapshot.getHeight());
            try {
                var chunks = Dithering.toDitheredChunks(snapshot);
                //Main.printIt(chunks);
            } catch (IOException e) {
                throw new RuntimeException(e); // TODO ...
            }
            // brief flash effect
            cameraPanel.flash();
        }
        captureButton.setEnabled(true);
    }

    public BufferedImage getLastSnapshot() {
        return lastSnapshot.get();
    }

    private void shutdown() {
        running.set(false);
        try {
            grabber.stop();
            grabber.release();
        } catch (FrameGrabber.Exception e) {
            log.error("Error stopping webcam", e);
        }
        dispose();
    }

    /**
     * Panel that displays the live camera feed and countdown overlay.
     */
    private class CameraPanel extends JPanel {
        private volatile BufferedImage currentImage;
        private volatile boolean flashing;

        void updateImage(BufferedImage image) {
            this.currentImage = image;
            repaint();
        }

        BufferedImage getCurrentImage() {
            return currentImage;
        }

        void flash() {
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

            if (currentImage != null) {
                // scale to fill panel while maintaining aspect ratio
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
                g2.setColor(Color.DARK_GRAY);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 24));
                String msg = "Starting camera...";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
            }

            // flash overlay
            if (flashing) {
                g2.setColor(new Color(255, 255, 255, 200));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }

            // countdown overlay
            int cd = countdown.get();
            if (cd > 0) {
                // semi-transparent background
                g2.setColor(new Color(0, 0, 0, 120));
                g2.fillRect(0, 0, getWidth(), getHeight());

                // large countdown number
                String text = String.valueOf(cd);
                g2.setFont(new Font("SansSerif", Font.BOLD, 200));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(text)) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;

                // text shadow
                g2.setColor(new Color(0, 0, 0, 180));
                g2.drawString(text, tx + 4, ty + 4);

                // bright countdown number
                g2.setColor(new Color(255, 80, 80));
                g2.drawString(text, tx, ty);
            }
        }
    }
}
