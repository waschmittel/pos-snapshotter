package de.flubba;

import com.formdev.flatlaf.extras.FlatSVGIcon;
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
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import com.formdev.flatlaf.util.SystemFileChooser;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

@Slf4j
public class SnapshotterFrame extends JFrame {
    private final CameraPanel cameraPanel;
    private final ImagePanel previewPanel;
    private final JButton captureButton;
    private final JPanel paramsPanel;
    private final JScrollPane paramsScrollPane;
    private final JPanel panelsContainer;
    private boolean settingsExpanded = false;
    private FrameGrabber grabber;
    private final Java2DFrameConverter converter = new Java2DFrameConverter();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean cameraPaused = new AtomicBoolean(false);
    private final AtomicInteger countdown = new AtomicInteger(-1);
    private final AtomicReference<BufferedImage> lastSnapshot = new AtomicReference<>();
    private final SettingsStore settingsStore = new SettingsStore();
    private final AtomicReference<DitherParams> currentParams = new AtomicReference<>(settingsStore.loadDitherParams());
    private TextPrintPanel textPrintPanel;
    private final ImagePanel sourceImagePanel;
    private final ImagePanel imageDitheredPreview;
    private JPanel imagePanelsContainer;
    private JPanel imageFilePanel;
    private boolean imageSettingsExpanded = false;
    private final AtomicBoolean imageTabActive = new AtomicBoolean(false);
    private final AtomicReference<BufferedImage> loadedOriginalImage = new AtomicReference<>();
    private JTabbedPane tabbedPane;

    // parameter controls
    private JComboBox<DiffusionMatrix> matrixCombo;
    private JSpinner preDitheringGammaSpinner;
    private JSlider preDitheringGammaSlider;
    private JSpinner sharpnessSpinner;
    private JSlider sharpnessSlider;
    private JSpinner contrastSpinner;
    private JSlider contrastSlider;
    private JSpinner grayLevelsSpinner;
    private JSpinner claheTilesXSpinner;
    private JSpinner claheClipLimitSpinner;
    private JSlider claheClipLimitSlider;

    public SnapshotterFrame() throws FrameGrabber.Exception {
        super("POS Snapshotter");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        int savedCamera = settingsStore.loadCameraIndex();
        grabber = startGrabber(savedCamera);

        cameraPanel = new CameraPanel();
        cameraPanel.setPreferredSize(new Dimension(grabber.getImageWidth(), grabber.getImageHeight()));

        previewPanel = new ImagePanel("Dithering preview...");
        previewPanel.setPreferredSize(new Dimension(grabber.getImageWidth(), grabber.getImageHeight()));

        captureButton = createActionButton("Take Photo", "icons/camera.svg");
        captureButton.addActionListener(_ -> startCountdown());

        JButton settingsButton = new JButton(new FlatSVGIcon("icons/settings.svg", 16, 16));
        settingsButton.setToolTipText("Settings");
        settingsButton.setFocusPainted(false);
        settingsButton.addActionListener(_ -> {
            settingsExpanded = !settingsExpanded;
            updateLayout();
        });

        String[] cameraLabels = detectCameraNames();
        JComboBox<String> cameraCombo = new JComboBox<>(cameraLabels);
        cameraCombo.setSelectedIndex(Math.min(savedCamera, cameraLabels.length - 1));
        cameraCombo.addActionListener(_ -> switchCamera(cameraCombo.getSelectedIndex()));

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightPanel.add(cameraCombo);
        rightPanel.add(settingsButton);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(captureButton, BorderLayout.CENTER);
        bottomPanel.add(rightPanel, BorderLayout.EAST);

        panelsContainer = new JPanel(new GridLayout(1, 1));
        panelsContainer.add(cameraPanel);

        paramsPanel = buildParamsPanel();
        paramsScrollPane = new JScrollPane(paramsPanel);
        paramsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        paramsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        paramsScrollPane.setVisible(false);
        previewPanel.setVisible(false);

        // Wrap photo UI in its own panel
        JPanel photoPanel = new JPanel(new BorderLayout());
        photoPanel.add(paramsScrollPane, BorderLayout.WEST);
        photoPanel.add(panelsContainer, BorderLayout.CENTER);
        photoPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Image file panel
        sourceImagePanel = new ImagePanel("No image loaded");
        sourceImagePanel.setPreferredSize(new Dimension(640, 427));
        imageDitheredPreview = new ImagePanel("Dithering preview...");
        imageDitheredPreview.setPreferredSize(new Dimension(640, 427));
        imageFilePanel = buildImageFilePanel();

        // Text print panel
        textPrintPanel = new TextPrintPanel(currentParams);

        // Tabbed pane for mode switching
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Webcam", photoPanel);
        tabbedPane.addTab("Image", imageFilePanel);
        tabbedPane.addTab("Text", textPrintPanel);
        tabbedPane.addChangeListener(_ -> {
            int selected = tabbedPane.getSelectedIndex();
            settingsStore.saveLastTab(selected);
            cameraPaused.set(selected != 0);
            imageTabActive.set(selected == 1);
            // Reparent shared params panel to active tab and sync visibility
            if (selected == 0) {
                photoPanel.add(paramsScrollPane, BorderLayout.WEST);
                updateLayout();
            } else if (selected == 1) {
                imageFilePanel.add(paramsScrollPane, BorderLayout.WEST);
                updateImageLayout();
            }
        });

        tabbedPane.setSelectedIndex(Math.min(settingsStore.loadLastTab(), tabbedPane.getTabCount() - 1));

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);
        smartPack();
        setLocationRelativeTo(null);

        startCameraLoop();
        startDitheringLoop();
        startImageDitheringLoop();
    }

    private void smartPack() {
        pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension current = getSize();
        boolean resized = false;
        int newW = current.width;
        int newH = current.height;
        if (current.width > screen.width * 0.95) {
            newW = (int) (screen.width * 0.95);
            resized = true;
        }
        if (current.height > screen.height * 0.9) {
            newH = (int) (screen.height * 0.9);
            resized = true;
        }
        if (resized) {
            setSize(newW, newH);
        }
    }

    private JPanel buildParamsPanel() {
        DitherParams saved = currentParams.get();
        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 8, 0);

        // --- General ---
        JPanel generalPanel = createGroupPanel("General");
        matrixCombo = new JComboBox<>(DiffusionMatrix.values());
        matrixCombo.setSelectedItem(saved.diffusionMatrix());
        matrixCombo.addActionListener(_ -> syncParams());
        addSettingRow(generalPanel, "Diffusion:", matrixCombo, 0);

        grayLevelsSpinner = new JSpinner(new SpinnerNumberModel(saved.grayLevels(), 2, 12, 1));
        grayLevelsSpinner.addChangeListener(_ -> syncParams());
        addSettingRow(generalPanel, "Gray levels:", grayLevelsSpinner, 1);

        root.add(generalPanel, gbc);
        gbc.gridy++;

        // --- Image Adjustments ---
        JPanel adjustPanel = createGroupPanel("Image Adjustments");
        preDitheringGammaSlider = new JSlider(1, 30, (int) (saved.preDitheringGamma() * 10));
        preDitheringGammaSpinner = new JSpinner(new SpinnerNumberModel(saved.preDitheringGamma(), 0.1, 3.0, 0.1));
        linkSliderSpinner(preDitheringGammaSlider, preDitheringGammaSpinner, 10.0);
        addSettingRow(adjustPanel, "Brightness \u03B3:", preDitheringGammaSlider, preDitheringGammaSpinner, 0);

        contrastSlider = new JSlider(5, 30, (int) (saved.contrast() * 10));
        contrastSpinner = new JSpinner(new SpinnerNumberModel(saved.contrast(), 0.5, 3.0, 0.1));
        linkSliderSpinner(contrastSlider, contrastSpinner, 10.0);
        addSettingRow(adjustPanel, "Contrast:", contrastSlider, contrastSpinner, 1);

        sharpnessSlider = new JSlider(0, 50, (int) (saved.sharpness() * 10));
        sharpnessSpinner = new JSpinner(new SpinnerNumberModel(saved.sharpness(), 0.0, 5.0, 0.1));
        linkSliderSpinner(sharpnessSlider, sharpnessSpinner, 10.0);
        addSettingRow(adjustPanel, "Sharpness:", sharpnessSlider, sharpnessSpinner, 2);

        root.add(adjustPanel, gbc);
        gbc.gridy++;

        // --- Advanced (CLAHE) ---
        JPanel clahePanel = createGroupPanel("Advanced (CLAHE)");
        claheTilesXSpinner = new JSpinner(new SpinnerNumberModel(saved.claheTilesX(), 1, 32, 1));
        claheTilesXSpinner.addChangeListener(_ -> syncParams());
        addSettingRow(clahePanel, "Tiles X:", claheTilesXSpinner, 0);

        claheClipLimitSlider = new JSlider(10, 80, (int) (saved.claheClipLimit() * 10));
        claheClipLimitSpinner = new JSpinner(new SpinnerNumberModel(saved.claheClipLimit(), 1.0, 8.0, 0.1));
        linkSliderSpinner(claheClipLimitSlider, claheClipLimitSpinner, 10.0);
        addSettingRow(clahePanel, "Clip Limit:", claheClipLimitSlider, claheClipLimitSpinner, 1);

        root.add(clahePanel, gbc);
        gbc.gridy++;

        JButton resetButton = new JButton("Reset to Defaults", new FlatSVGIcon("icons/reset.svg", 16, 16));
        resetButton.addActionListener(_ -> resetToDefaults());
        gbc.insets = new Insets(8, 4, 0, 4);
        root.add(resetButton, gbc);

        // Push everything up
        gbc.gridy++;
        gbc.weighty = 1.0;
        root.add(new JPanel(), gbc);

        root.setPreferredSize(new Dimension(300, 600));
        return root;
    }

    private JPanel createGroupPanel(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD));
        panel.setBorder(BorderFactory.createCompoundBorder(
                border,
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        return panel;
    }

    private void addSettingRow(JPanel panel, String labelText, Component control, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 4, 2, 8);
        panel.add(new JLabel(labelText), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(2, 0, 2, 4);
        panel.add(control, gbc);
    }

    private void addSettingRow(JPanel panel, String labelText, JSlider slider, JSpinner spinner, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 4, 2, 8);
        panel.add(new JLabel(labelText), gbc);

        JPanel combo = new JPanel(new BorderLayout(4, 0));
        combo.add(slider, BorderLayout.CENTER);
        spinner.setPreferredSize(new Dimension(60, spinner.getPreferredSize().height));
        combo.add(spinner, BorderLayout.EAST);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(2, 0, 2, 4);
        panel.add(combo, gbc);
    }

    private void linkSliderSpinner(JSlider slider, JSpinner spinner, double factor) {
        slider.addChangeListener(_ -> {
            if (slider.getValueIsAdjusting()) {
                spinner.setValue(slider.getValue() / factor);
            }
        });
        spinner.addChangeListener(_ -> {
            slider.setValue((int) (((Number) spinner.getValue()).doubleValue() * factor));
            syncParams();
        });
        ((JSpinner.NumberEditor) spinner.getEditor()).getFormat().setMinimumFractionDigits(1);
    }

    private JPanel buildImageFilePanel() {
        imagePanelsContainer = new JPanel(new GridLayout(1, 1));
        imagePanelsContainer.add(sourceImagePanel);

        JButton loadButton = new JButton("Load Image...", new FlatSVGIcon("icons/open.svg", 16, 16));
        loadButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        loadButton.addActionListener(_ -> loadImageFromFile());

        JButton printButton = createActionButton("Print Image", "icons/print.svg");
        printButton.addActionListener(_ -> printFileImage());

        JButton imageSettingsButton = new JButton(new FlatSVGIcon("icons/settings.svg", 16, 16));
        imageSettingsButton.setToolTipText("Settings");
        imageSettingsButton.setFocusPainted(false);
        imageSettingsButton.addActionListener(_ -> {
            imageSettingsExpanded = !imageSettingsExpanded;
            updateImageLayout();
        });

        JPanel imageRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        imageRightPanel.add(loadButton);
        imageRightPanel.add(imageSettingsButton);

        JPanel imageBottomPanel = new JPanel(new BorderLayout());
        imageBottomPanel.add(printButton, BorderLayout.CENTER);
        imageBottomPanel.add(imageRightPanel, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout());
        // paramsPanel will be reparented here when tab is active
        panel.add(imagePanelsContainer, BorderLayout.CENTER);
        panel.add(imageBottomPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void loadImageFromFile() {
        var fc = new SystemFileChooser();
        fc.setDialogTitle("Load Image");
        fc.setCurrentDirectory(new File(settingsStore.loadLastImageDirectory()));
        fc.setFileFilter(new SystemFileChooser.FileNameExtensionFilter("Images (png, jpg, bmp, gif)", "png", "jpg", "jpeg", "bmp", "gif"));

        if (fc.showOpenDialog(this) != SystemFileChooser.APPROVE_OPTION) return;

        var file = fc.getSelectedFile();
        settingsStore.saveLastImageDirectory(file.getParent());
        try {
            BufferedImage raw = ImageIO.read(file);
            if (raw != null) {
                loadedOriginalImage.set(raw);
                sourceImagePanel.updateImage(raw);
                log.info("Loaded image from file: {} ({}x{})", file.getName(), raw.getWidth(), raw.getHeight());
            }
        } catch (IOException e) {
            log.error("Failed to load image", e);
        }
    }

    private static final int PRINTER_WIDTH = 512;

    private void printFileImage() {
        BufferedImage original = loadedOriginalImage.get();
        if (original == null) return;

        boolean landscape = original.getWidth() > original.getHeight();
        // Scale to maximize printer area (512px wide, unlimited height)
        // Landscape: transpose will swap dimensions, so scale height to printer width
        // Portrait/square: scale width to printer width directly
        BufferedImage scaled = landscape
                ? ImageScaler.scaleToHeight(original, PRINTER_WIDTH)
                : ImageScaler.scaleToWidth(original, PRINTER_WIDTH);

        try {
            var chunks = landscape
                    ? Dithering.toDitheredChunks(scaled, currentParams.get())
                    : Dithering.toDitheredChunksPortrait(scaled, currentParams.get());
            Main.printIt(chunks);
            log.info("Printed image from file: {}x{} → {}x{} ({})",
                    original.getWidth(), original.getHeight(),
                    scaled.getWidth(), scaled.getHeight(),
                    landscape ? "landscape, rotated" : "portrait");
        } catch (IOException e) {
            log.error("Failed to print image", e);
        }
    }

    private void updateImageLayout() {
        toggleLayout(imagePanelsContainer, sourceImagePanel, imageDitheredPreview, imageSettingsExpanded);
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
        currentParams.set(params);
        settingsStore.saveDitherParams(params);
    }

    private void resetToDefaults() {
        settingsStore.resetDitherParams();
        var defaults = DitherParams.defaults();
        matrixCombo.setSelectedItem(defaults.diffusionMatrix());
        preDitheringGammaSpinner.setValue(defaults.preDitheringGamma());
        preDitheringGammaSlider.setValue((int) (defaults.preDitheringGamma() * 10));
        sharpnessSpinner.setValue(defaults.sharpness());
        sharpnessSlider.setValue((int) (defaults.sharpness() * 10));
        contrastSpinner.setValue(defaults.contrast());
        contrastSlider.setValue((int) (defaults.contrast() * 10));
        grayLevelsSpinner.setValue(defaults.grayLevels());
        claheTilesXSpinner.setValue(defaults.claheTilesX());
        claheClipLimitSpinner.setValue(defaults.claheClipLimit());
        claheClipLimitSlider.setValue((int) (defaults.claheClipLimit() * 10));
    }

    private static String[] detectCameraNames() {
        try {
            var process = new ProcessBuilder("system_profiler", "SPCameraDataType", "-json")
                    .redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes());
            process.waitFor();

            var names = new ArrayList<String>();
            // Extract "_name" values from JSON using simple pattern matching
            int searchFrom = 0;
            while (true) {
                int nameKeyPos = output.indexOf("\"_name\"", searchFrom);
                if (nameKeyPos < 0) break;
                int colonPos = output.indexOf(":", nameKeyPos);
                int quoteStart = output.indexOf("\"", colonPos + 1);
                int quoteEnd = output.indexOf("\"", quoteStart + 1);
                if (quoteStart >= 0 && quoteEnd > quoteStart) {
                    names.add(output.substring(quoteStart + 1, quoteEnd));
                }
                searchFrom = quoteEnd + 1;
            }

            if (!names.isEmpty()) {
                return names.toArray(String[]::new);
            }
        } catch (Exception e) {
            log.warn("Could not detect camera names", e);
        }
        // Fallback: generic labels
        return new String[]{"Camera 0", "Camera 1", "Camera 2", "Camera 3"};
    }

    private FrameGrabber startGrabber(int deviceIndex) {
        try {
            var g = new OpenCVFrameGrabber(deviceIndex);
            g.setImageWidth(768);
            g.setImageHeight(512);
            g.start();
            log.info("Camera {} started: {}x{}", deviceIndex, g.getImageWidth(), g.getImageHeight());
            return g;
        } catch (FrameGrabber.Exception e) {
            log.error("Failed to start camera {}", deviceIndex, e);
            return null;
        }
    }

    private void switchCamera(int deviceIndex) {
        Thread.ofPlatform().name("camera-switch").start(() -> {
            try {
                if (grabber != null) {
                    grabber.stop();
                    grabber.release();
                }
            } catch (FrameGrabber.Exception e) {
                log.error("Error stopping old camera", e);
            }
            grabber = startGrabber(deviceIndex);
            settingsStore.saveCameraIndex(deviceIndex);
        });
    }

    private void updateLayout() {
        toggleLayout(panelsContainer, cameraPanel, previewPanel, settingsExpanded);
    }

    private void toggleLayout(JPanel container, JPanel mainPanel, JPanel preview, boolean expanded) {
        paramsScrollPane.setVisible(expanded);
        preview.setVisible(expanded);
        container.removeAll();
        ((GridLayout) container.getLayout()).setColumns(expanded ? 2 : 1);
        container.add(mainPanel);
        if (expanded) {
            container.add(preview);
        }
        container.revalidate();
        smartPack();
    }

    private void startCameraLoop() {
        startPollingLoop("camera-loop", () -> !cameraPaused.get(), () -> {
            var currentGrabber = grabber;
            if (currentGrabber == null) return;
            Frame frame = currentGrabber.grab();
            if (frame != null) {
                BufferedImage image = converter.convert(frame);
                if (image != null) {
                    cameraPanel.updateImage(image);
                }
            }
        });
    }

    private void startDitheringLoop() {
        startPollingLoop("dithering-loop", () -> !cameraPaused.get(), () -> {
            BufferedImage image = cameraPanel.getCurrentImage();
            if (image != null) {
                previewPanel.updateImage(Dithering.toDitheredImage(image, currentParams.get()));
            }
        });
    }

    private void startImageDitheringLoop() {
        startPollingLoop("image-dithering-loop", imageTabActive::get, () -> {
            BufferedImage image = sourceImagePanel.getCurrentImage();
            if (image != null) {
                imageDitheredPreview.updateImage(Dithering.toDitheredImage(image, currentParams.get()));
            }
        });
    }

    private void startPollingLoop(String name, BooleanSupplier shouldRun, ThrowingRunnable work) {
        Thread.ofVirtual().name(name).start(() -> {
            while (running.get()) {
                try {
                    if (!shouldRun.getAsBoolean()) { Thread.sleep(200); continue; }
                    work.run();
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in {}", name, e);
                }
            }
        });
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    static JButton createActionButton(String text, String iconPath) {
        JButton button = new JButton(text, new FlatSVGIcon(iconPath, 32, 32));
        button.setFont(new Font("SansSerif", Font.BOLD, 32));
        button.setPreferredSize(new Dimension(0, 80));
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setBackground(new Color(0, 120, 215));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        return button;
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
                // Scale to maximize printer area (512px wide, unlimited height)
                // Camera images are usually landscape, so we scale height to printer width
                // and then transpose (landscape=true).
                BufferedImage scaled = ImageScaler.scaleToHeight(snapshot, PRINTER_WIDTH);
                Main.printIt(Dithering.toDitheredChunks(scaled, currentParams.get()));
            } catch (IOException e) {
                log.error("Failed to print photo", e);
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
        textPrintPanel.saveBeforeShutdown();
        try {
            if (grabber != null) {
                grabber.stop();
                grabber.release();
            }
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
