package de.flubba;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.SystemFileChooser;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JButton;
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
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
public class ImageTabPanel extends JPanel {

    private final SettingsStore settings;
    private final PrintWorkflow printWorkflow;
    private final Runnable repack;
    private final Runnable selectThisTab;
    private final StatusBar statusBar;

    private final ImagePanel sourceImagePanel;
    private final ImagePanel imageDitheredPreview;
    private final JScrollPane paramsScrollPane;
    private final JPanel panelsContainer;
    private final JToggleButton imageSettingsButton;
    private final JButton printButton;

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicReference<BufferedImage> loadedOriginalImage = new AtomicReference<>();
    private boolean settingsExpanded;

    public ImageTabPanel(SettingsStore settings,
                         PrintWorkflow printWorkflow,
                         JScrollPane paramsScrollPane,
                         AtomicBoolean running,
                         Runnable repack,
                         Runnable selectThisTab,
                         StatusBar statusBar) {
        super(new BorderLayout());
        this.settings = settings;
        this.printWorkflow = printWorkflow;
        this.paramsScrollPane = paramsScrollPane;
        this.repack = repack;
        this.selectThisTab = selectThisTab;
        this.statusBar = statusBar;
        this.settingsExpanded = settings.loadSidebarExpanded();

        sourceImagePanel = new ImagePanel("Drop image here or click 'Load Image…'");
        sourceImagePanel.setBackground(new Color(0xF2F2F2));
        sourceImagePanel.setPreferredSize(new Dimension(640, 427));

        imageDitheredPreview = new ImagePanel("Dithering preview…");
        imageDitheredPreview.setBackground(Color.WHITE);
        imageDitheredPreview.setPreferredSize(new Dimension(640, 427));

        panelsContainer = new JPanel(new GridLayout(1, settingsExpanded ? 2 : 1));
        panelsContainer.add(sourceImagePanel);
        if (settingsExpanded) panelsContainer.add(imageDitheredPreview);
        imageDitheredPreview.setVisible(settingsExpanded);

        JButton loadButton = UiTheme.primarySmall("Load Image…", "icons/open.svg");
        loadButton.setToolTipText("Open image file (Ctrl+O)");
        loadButton.addActionListener(_ -> loadImageFromFile());

        printButton = SnapshotterFrame.createActionButton("Print Image", "icons/print.svg");
        printButton.setToolTipText("Print loaded image (Ctrl+Enter)");
        printButton.addActionListener(_ -> printFileImage());
        printButton.setEnabled(false);

        imageSettingsButton = new JToggleButton(new FlatSVGIcon("icons/settings.svg", 16, 16));
        imageSettingsButton.setToolTipText("Show dithering settings + preview");
        imageSettingsButton.setFocusPainted(false);
        imageSettingsButton.setSelected(settingsExpanded);
        imageSettingsButton.addActionListener(_ -> {
            settingsExpanded = imageSettingsButton.isSelected();
            settings.saveSidebarExpanded(settingsExpanded);
            updateLayout();
        });

        JPanel imageRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        imageRightPanel.add(loadButton);
        imageRightPanel.add(imageSettingsButton);

        JPanel imageBottomPanel = new JPanel(new BorderLayout());
        imageBottomPanel.add(printButton, BorderLayout.CENTER);
        imageBottomPanel.add(imageRightPanel, BorderLayout.EAST);

        add(panelsContainer, BorderLayout.CENTER);
        add(imageBottomPanel, BorderLayout.SOUTH);

        installShortcuts();
        LivePreview.continuous("image-dithering-loop", running, active::get,
                sourceImagePanel::getCurrentImage, settings, imageDitheredPreview::updateImage);
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

    public Consumer<File> imageDropHandler() {
        return this::processImageFile;
    }

    private void installShortcuts() {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        var imap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        var amap = getActionMap();
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, mask), "open-image");
        amap.put("open-image", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadImageFromFile();
            }
        });
        imap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, mask), "print-image");
        amap.put("print-image", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (printButton.isEnabled()) printFileImage();
            }
        });
    }

    private void updateLayout() {
        paramsScrollPane.setVisible(settingsExpanded);
        imageDitheredPreview.setVisible(settingsExpanded);
        panelsContainer.removeAll();
        ((GridLayout) panelsContainer.getLayout()).setColumns(settingsExpanded ? 2 : 1);
        panelsContainer.add(sourceImagePanel);
        if (settingsExpanded) {
            panelsContainer.add(imageDitheredPreview);
        }
        panelsContainer.revalidate();
        repack.run();
    }

    private void loadImageFromFile() {
        var fc = new SystemFileChooser();
        fc.setDialogTitle("Load Image");
        fc.setCurrentDirectory(new File(settings.loadLastImageDirectory()));
        fc.setFileFilter(new SystemFileChooser.FileNameExtensionFilter(
                "Images (png, jpg, bmp, gif)", "png", "jpg", "jpeg", "bmp", "gif"));

        if (fc.showOpenDialog(this) != SystemFileChooser.APPROVE_OPTION) return;
        processImageFile(fc.getSelectedFile());
    }

    private void processImageFile(File file) {
        settings.saveLastImageDirectory(file.getParent());
        try {
            BufferedImage raw = ImageIO.read(file);
            if (raw == null) {
                statusBar.error("Could not decode image: " + file.getName());
                JOptionPane.showMessageDialog(this,
                        "Could not decode image: " + file.getName(),
                        "Load Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            loadedOriginalImage.set(raw);
            sourceImagePanel.updateImage(raw);
            printButton.setEnabled(true);
            selectThisTab.run();
            statusBar.info("Loaded " + file.getName() + " (" + raw.getWidth() + "×" + raw.getHeight() + ")");
            log.info("Loaded image from file: {} ({}x{})", file.getName(), raw.getWidth(), raw.getHeight());
        } catch (IOException e) {
            log.error("Failed to load image", e);
            statusBar.error("Failed to load image: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Failed to load image:\n" + e.getMessage(),
                    "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void printFileImage() {
        BufferedImage original = loadedOriginalImage.get();
        if (original == null) {
            statusBar.info("Load an image first");
            return;
        }
        try {
            printWorkflow.printFitted(original);
            statusBar.success("Image sent to printer");
            log.info("Printed image from file: {}x{}", original.getWidth(), original.getHeight());
        } catch (IOException e) {
            log.error("Failed to print image", e);
            statusBar.error("Print failed: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Failed to print image:\n" + e.getMessage(),
                    "Print Error", JOptionPane.ERROR_MESSAGE);
        }
    }

}
