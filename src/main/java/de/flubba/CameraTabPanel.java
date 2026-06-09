package de.flubba;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class CameraTabPanel extends JPanel {

    private final SettingsStore settings;
    private final PrintWorkflow printWorkflow;
    private final Runnable repack;
    private final StatusBar statusBar;

    private final CameraPanel cameraPanel;
    private final ImagePanel previewPanel;
    private final JButton captureButton;
    private final JScrollPane paramsScrollPane;
    private final JPanel panelsContainer;
    private final JToggleButton settingsButton;

    private final AtomicBoolean running;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final Java2DFrameConverter converter = new Java2DFrameConverter();
    private FrameGrabber grabber;
    private boolean settingsExpanded;

    public CameraTabPanel(SettingsStore settings,
                          PrintWorkflow printWorkflow,
                          JScrollPane paramsScrollPane,
                          AtomicBoolean running,
                          Runnable repack,
                          StatusBar statusBar) throws FrameGrabber.Exception {
        super(new BorderLayout());
        this.settings = settings;
        this.printWorkflow = printWorkflow;
        this.paramsScrollPane = paramsScrollPane;
        this.running = running;
        this.repack = repack;
        this.statusBar = statusBar;
        this.settingsExpanded = settings.loadSidebarExpanded();

        int savedCamera = settings.loadCameraIndex();
        grabber = startGrabber(savedCamera);

        cameraPanel = new CameraPanel();
        cameraPanel.setPreferredSize(new Dimension(grabber.getImageWidth(), grabber.getImageHeight()));

        previewPanel = new ImagePanel("Dithering preview...");
        previewPanel.setBackground(Color.WHITE);
        previewPanel.setPreferredSize(new Dimension(grabber.getImageWidth(), grabber.getImageHeight()));
        previewPanel.setVisible(settingsExpanded);

        captureButton = SnapshotterFrame.createActionButton("Take Photo", "icons/camera.svg");
        captureButton.setToolTipText("Capture and print photo (Ctrl+Enter)");
        captureButton.addActionListener(_ -> startCapture());

        settingsButton = new JToggleButton(new FlatSVGIcon("icons/settings.svg", 16, 16));
        settingsButton.setToolTipText("Show dithering settings + preview");
        settingsButton.setFocusPainted(false);
        settingsButton.setSelected(settingsExpanded);
        settingsButton.addActionListener(_ -> {
            settingsExpanded = settingsButton.isSelected();
            settings.saveSidebarExpanded(settingsExpanded);
            updateLayout();
        });

        String[] cameraLabels = detectCameraNames();
        JComboBox<String> cameraCombo = new JComboBox<>(cameraLabels);
        cameraCombo.setSelectedIndex(Math.min(savedCamera, cameraLabels.length - 1));
        cameraCombo.setToolTipText("Select camera device");
        cameraCombo.addActionListener(_ -> switchCamera(cameraCombo.getSelectedIndex()));

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightPanel.add(cameraCombo);
        rightPanel.add(settingsButton);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(captureButton, BorderLayout.CENTER);
        bottomPanel.add(rightPanel, BorderLayout.EAST);

        panelsContainer = new JPanel(new GridLayout(1, settingsExpanded ? 2 : 1));
        panelsContainer.add(cameraPanel);
        if (settingsExpanded) panelsContainer.add(previewPanel);

        add(panelsContainer, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        installShortcuts();
        startCameraLoop();
        startDitheringLoop();
    }

    public void attachSidebar() {
        paramsScrollPane.setVisible(settingsExpanded);
        add(paramsScrollPane, BorderLayout.WEST);
        updateLayout();
    }

    public void detachSidebar() {
        remove(paramsScrollPane);
    }

    public void setActive(boolean isActive) {
        active.set(isActive);
    }

    public void shutdown() {
        try {
            if (grabber != null) {
                grabber.stop();
                grabber.release();
            }
        } catch (FrameGrabber.Exception e) {
            log.error("Error stopping webcam", e);
        }
    }

    public Dimension grabberSize() {
        return new Dimension(grabber.getImageWidth(), grabber.getImageHeight());
    }

    private void installShortcuts() {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, mask), "capture");
        getActionMap().put("capture", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (captureButton.isEnabled()) startCapture();
            }
        });
    }

    private void updateLayout() {
        paramsScrollPane.setVisible(settingsExpanded);
        previewPanel.setVisible(settingsExpanded);
        panelsContainer.removeAll();
        ((GridLayout) panelsContainer.getLayout()).setColumns(settingsExpanded ? 2 : 1);
        panelsContainer.add(cameraPanel);
        if (settingsExpanded) {
            panelsContainer.add(previewPanel);
        }
        panelsContainer.revalidate();
        repack.run();
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
            if (statusBar != null) statusBar.error("Failed to start camera " + deviceIndex + ": " + e.getMessage());
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
            settings.saveCameraIndex(deviceIndex);
        });
    }

    private void startCameraLoop() {
        PollingLoop.start("camera-loop", running, active::get, () -> {
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
        PollingLoop.start("dithering-loop", running, active::get, () -> {
            BufferedImage image = cameraPanel.getCurrentImage();
            if (image != null) {
                previewPanel.updateImage(DitherPipeline.preview(image, settings.currentDitherParams()));
            }
        });
    }

    private void startCapture() {
        captureButton.setEnabled(false);
        statusBar.info("Capturing in 3...");
        cameraPanel.startCountdown(3, this::capturePhoto);
    }

    private void capturePhoto() {
        BufferedImage snapshot = cameraPanel.getCurrentImage();
        if (snapshot != null) {
            log.info("Photo captured ({}x{})", snapshot.getWidth(), snapshot.getHeight());
            try {
                printWorkflow.printFitted(snapshot);
                statusBar.success("Photo captured and sent to printer");
            } catch (IOException e) {
                log.error("Failed to print photo", e);
                statusBar.error("Print failed: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                        "Failed to print photo:\n" + e.getMessage(),
                        "Print Error", JOptionPane.ERROR_MESSAGE);
            }
            cameraPanel.flash();
        }
        captureButton.setEnabled(true);
    }

    private static String[] detectCameraNames() {
        try {
            var process = new ProcessBuilder("system_profiler", "SPCameraDataType", "-json")
                    .redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes());
            process.waitFor();

            var names = new ArrayList<String>();
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
        return new String[]{"Camera 0", "Camera 1", "Camera 2", "Camera 3"};
    }
}
