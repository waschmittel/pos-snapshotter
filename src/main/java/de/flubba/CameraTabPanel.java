package de.flubba;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.extern.slf4j.Slf4j;

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

    private final AtomicBoolean active = new AtomicBoolean(true);
    private final Camera camera;
    private boolean settingsExpanded;

    public CameraTabPanel(SettingsStore settings,
                          PrintWorkflow printWorkflow,
                          JScrollPane paramsScrollPane,
                          AtomicBoolean running,
                          Runnable repack,
                          StatusBar statusBar) {
        super(new BorderLayout());
        this.settings = settings;
        this.printWorkflow = printWorkflow;
        this.paramsScrollPane = paramsScrollPane;
        this.repack = repack;
        this.statusBar = statusBar;
        this.settingsExpanded = settings.loadSidebarExpanded();

        cameraPanel = new CameraPanel();
        camera = new Camera(settings.loadCameraIndex(), running, active::get,
                cameraPanel::updateImage, statusBar::error);

        Dimension frameSize = camera.frameSize();
        cameraPanel.setPreferredSize(frameSize);

        previewPanel = new ImagePanel("Dithering preview...");
        previewPanel.setBackground(Color.WHITE);
        previewPanel.setPreferredSize(frameSize);
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

        String[] cameraLabels = Camera.detectCameraNames();
        JComboBox<String> cameraCombo = new JComboBox<>(cameraLabels);
        cameraCombo.setSelectedIndex(Math.min(settings.loadCameraIndex(), cameraLabels.length - 1));
        cameraCombo.setToolTipText("Select camera device");
        cameraCombo.addActionListener(_ -> {
            camera.select(cameraCombo.getSelectedIndex());
            settings.saveCameraIndex(cameraCombo.getSelectedIndex());
        });

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
        LivePreview.continuous("dithering-loop", running, active::get,
                cameraPanel::getCurrentImage, settings, previewPanel::updateImage);
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
        camera.close();
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
}
