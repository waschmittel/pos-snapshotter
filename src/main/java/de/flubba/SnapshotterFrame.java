package de.flubba;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
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
    private final ImagePanel previewPanel;
    private final JButton captureButton;
    private final JPanel paramsPanel;
    private final JPanel panelsContainer;
    private boolean settingsExpanded = false;
    private final FrameGrabber grabber;
    private final Java2DFrameConverter converter = new Java2DFrameConverter();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger countdown = new AtomicInteger(-1);
    private final AtomicReference<BufferedImage> lastSnapshot = new AtomicReference<>();
    public static final AtomicReference<DitherParams> CURRENT_PARAMS = new AtomicReference<>(DitherParams.load());

    // parameter controls
    private JComboBox<DiffusionMatrix> matrixCombo;
    private JSpinner preDitheringGammaSpinner;
    private JSpinner sharpnessSpinner;
    private JSpinner contrastSpinner;
    private JSpinner grayLevelsSpinner;
    private JSpinner claheTilesXSpinner;
    private JSpinner claheClipLimitSpinner;

    public SnapshotterFrame() throws FrameGrabber.Exception {
        super("POS Snapshotter");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        grabber = new OpenCVFrameGrabber(0); // TODO: allow capturing from another device
        grabber.setImageWidth(768);
        grabber.setImageHeight(512);
        grabber.start(); // TODO: release in finally?
        log.info("Webcam started: {}x{}", grabber.getImageWidth(), grabber.getImageHeight());

        cameraPanel = new CameraPanel();
        cameraPanel.setPreferredSize(new Dimension(grabber.getImageWidth(), grabber.getImageHeight()));

        previewPanel = new ImagePanel("Dithering preview...");
        previewPanel.setPreferredSize(new Dimension(grabber.getImageWidth(), grabber.getImageHeight()));

        captureButton = new JButton("Take Photo");
        captureButton.setFont(new Font("SansSerif", Font.BOLD, 32));
        captureButton.setPreferredSize(new Dimension(0, 80));
        captureButton.setBackground(new Color(0, 120, 215));
        captureButton.setForeground(Color.WHITE);
        captureButton.setFocusPainted(false);
        captureButton.addActionListener(_ -> startCountdown());

        JButton settingsButton = new JButton("Settings \u25B6");
        settingsButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        settingsButton.setFocusPainted(false);
        settingsButton.addActionListener(_ -> {
            settingsExpanded = !settingsExpanded;
            settingsButton.setText(settingsExpanded ? "\u25C0 Settings" : "Settings \u25B6");
            updateLayout();
        });

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(captureButton, BorderLayout.CENTER);
        bottomPanel.add(settingsButton, BorderLayout.EAST);

        panelsContainer = new JPanel(new GridLayout(1, 1));
        panelsContainer.add(cameraPanel);

        paramsPanel = buildParamsPanel();
        paramsPanel.setVisible(false);
        previewPanel.setVisible(false);

        setLayout(new BorderLayout());
        add(paramsPanel, BorderLayout.NORTH);
        add(panelsContainer, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(null);

        startCameraLoop();
        startDitheringLoop();
    }

    private JPanel buildParamsPanel() {
        DitherParams saved = CURRENT_PARAMS.get();

        matrixCombo = new JComboBox<>(DiffusionMatrix.values());
        matrixCombo.setSelectedItem(saved.diffusionMatrix());
        matrixCombo.addActionListener(_ -> syncParams());

        preDitheringGammaSpinner = new JSpinner(new SpinnerNumberModel(saved.preDitheringGamma(), 0.1, 3.0, 0.1));
        ((JSpinner.NumberEditor) preDitheringGammaSpinner.getEditor()).getFormat().setMinimumFractionDigits(1);
        preDitheringGammaSpinner.addChangeListener(_ -> syncParams());

        sharpnessSpinner = new JSpinner(new SpinnerNumberModel(saved.sharpness(), 0.0, 5.0, 0.1));
        ((JSpinner.NumberEditor) sharpnessSpinner.getEditor()).getFormat().setMinimumFractionDigits(1);
        sharpnessSpinner.addChangeListener(_ -> syncParams());

        contrastSpinner = new JSpinner(new SpinnerNumberModel(saved.contrast(), 0.5, 3.0, 0.1));
        ((JSpinner.NumberEditor) contrastSpinner.getEditor()).getFormat().setMinimumFractionDigits(1);
        contrastSpinner.addChangeListener(_ -> syncParams());

        grayLevelsSpinner = new JSpinner(new SpinnerNumberModel(saved.grayLevels(), 2, 12, 1));
        grayLevelsSpinner.addChangeListener(_ -> syncParams());

        claheTilesXSpinner = new JSpinner(new SpinnerNumberModel(saved.claheTilesX(), 1, 32, 1));
        claheTilesXSpinner.addChangeListener(_ -> syncParams());

        claheClipLimitSpinner = new JSpinner(new SpinnerNumberModel(saved.claheClipLimit(), 1.0, 8.0, 0.1));
        ((JSpinner.NumberEditor) claheClipLimitSpinner.getEditor()).getFormat().setMinimumFractionDigits(1);
        claheClipLimitSpinner.addChangeListener(_ -> syncParams());

        JButton resetButton = new JButton("Reset");
        resetButton.addActionListener(_ -> resetToDefaults());

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Dithering Parameters"));
        panel.add(new JLabel("Diffusion:"));
        panel.add(matrixCombo);
        panel.add(new JLabel("Brightness γ:"));
        panel.add(preDitheringGammaSpinner);
        panel.add(new JLabel("Contrast:"));
        panel.add(contrastSpinner);
        panel.add(new JLabel("Sharpness:"));
        panel.add(sharpnessSpinner);
        panel.add(new JLabel("Gray levels:"));
        panel.add(grayLevelsSpinner);
        panel.add(new JLabel("CLAHE tiles X:"));
        panel.add(claheTilesXSpinner);
        panel.add(new JLabel("CLAHE clip:"));
        panel.add(claheClipLimitSpinner);
        panel.add(resetButton);
        return panel;
    }

    private void syncParams() {
        var params = new DitherParams(
                (DiffusionMatrix) matrixCombo.getSelectedItem(),
                (double) preDitheringGammaSpinner.getValue(),
                (double) sharpnessSpinner.getValue(),
                (double) contrastSpinner.getValue(),
                (int) grayLevelsSpinner.getValue(),
                (int) claheTilesXSpinner.getValue(),
                (double) claheClipLimitSpinner.getValue()
        );
        CURRENT_PARAMS.set(params);
        params.save();
    }

    private void resetToDefaults() {
        DitherParams.resetPrefs();
        var defaults = DitherParams.defaults();
        matrixCombo.setSelectedItem(defaults.diffusionMatrix());
        preDitheringGammaSpinner.setValue(defaults.preDitheringGamma());
        sharpnessSpinner.setValue(defaults.sharpness());
        contrastSpinner.setValue(defaults.contrast());
        grayLevelsSpinner.setValue(defaults.grayLevels());
        claheTilesXSpinner.setValue(defaults.claheTilesX());
        claheClipLimitSpinner.setValue(defaults.claheClipLimit());
    }

    private void updateLayout() {
        paramsPanel.setVisible(settingsExpanded);
        previewPanel.setVisible(settingsExpanded);
        panelsContainer.removeAll();
        if (settingsExpanded) {
            ((GridLayout) panelsContainer.getLayout()).setColumns(2);
            panelsContainer.add(cameraPanel);
            panelsContainer.add(previewPanel);
        } else {
            ((GridLayout) panelsContainer.getLayout()).setColumns(1);
            panelsContainer.add(cameraPanel);
        }
        panelsContainer.revalidate();
        pack();
    }

    private void startCameraLoop() {
        Thread.ofPlatform().name("camera-loop").start(() -> {
            while (running.get()) {
                try {
                    Frame frame = grabber.grab();
                    if (frame != null) {
                        BufferedImage image = converter.convert(frame);
                        if (image != null) {
                            // scale to target resolution (camera may deliver a different size than requested)
                            BufferedImage copy = new BufferedImage(910, 512, BufferedImage.TYPE_3BYTE_BGR);
                            Graphics2D g2 = copy.createGraphics();
                            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            g2.drawImage(image, 0, 0, 910, 512, null);
                            g2.dispose();
                            cameraPanel.updateImage(copy); // TODO: update async
                        }
                    }
                    // TODO: irgendwas macht mords CPU-Load ... unde es scheint weder der dithering-loop zu sein noch dieser.
                    Thread.sleep(200); // ~5 fps
                } catch (InterruptedException e) {
                    log.warn("Camera loop interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error grabbing frame", e);
                }
            }
        });
    }

    private void startDitheringLoop() {
        Thread.ofVirtual().name("dithering-loop").start(() -> {
            while (running.get()) {
                try {
                    BufferedImage image = cameraPanel.getCurrentImage();
                    if (image != null) {
                        BufferedImage dithered = Dithering.toDitheredImage(image, CURRENT_PARAMS.get());
                        previewPanel.updateImage(dithered);
                    }
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    log.warn("Dithering loop interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error dithering frame", e);
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
                var chunks = Dithering.toDitheredChunks(snapshot, CURRENT_PARAMS.get());
                Main.printIt(chunks);
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
     * Panel that displays an image scaled to fit, with a placeholder message when no image is available.
     */
    private static class ImagePanel extends JPanel {
        private volatile BufferedImage currentImage;
        private final String placeholderMessage;

        ImagePanel(String placeholderMessage) {
            this.placeholderMessage = placeholderMessage;
        }

        void updateImage(BufferedImage image) {
            this.currentImage = image;
            repaint();
        }

        BufferedImage getCurrentImage() {
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
                g2.setColor(Color.DARK_GRAY);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 24));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(placeholderMessage, (getWidth() - fm.stringWidth(placeholderMessage)) / 2, getHeight() / 2);
            }
        }
    }

    /**
     * Panel that displays the live camera feed with countdown and flash overlays.
     */
    private class CameraPanel extends ImagePanel {
        private volatile boolean flashing;

        CameraPanel() {
            super("Starting camera...");
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
