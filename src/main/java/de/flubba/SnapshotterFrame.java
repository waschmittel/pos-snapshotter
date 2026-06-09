package de.flubba;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FrameGrabber;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class SnapshotterFrame extends JFrame {

    private final SettingsStore settings = new SettingsStore();
    private final ParamsPanel paramsPanel = new ParamsPanel(settings);
    private final PrintWorkflow printWorkflow = new PrintWorkflow(settings, paramsPanel::getSelectedPrinter);
    private final StatusBar statusBar = new StatusBar();

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final CameraTabPanel cameraTab;
    private final ImageTabPanel imageTab;
    private final TextPrintPanel textTab;
    private final JTabbedPane tabbedPane;

    public SnapshotterFrame() throws FrameGrabber.Exception {
        super("POS Snapshotter");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        setIconImage(new FlatSVGIcon("icons/camera.svg", 64, 64).getImage());

        JScrollPane paramsScrollPane = new JScrollPane(paramsPanel);
        paramsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        paramsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        boolean sidebarOpen = settings.loadSidebarExpanded();
        paramsScrollPane.setVisible(sidebarOpen);

        cameraTab = new CameraTabPanel(settings, printWorkflow, paramsScrollPane, running, this::smartPack, statusBar);
        imageTab = new ImageTabPanel(settings, printWorkflow, paramsScrollPane, running, this::smartPack,
                () -> tabbedPane().setSelectedIndex(1), statusBar);
        textTab = new TextPrintPanel(settings, printWorkflow, statusBar);

        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Webcam", new FlatSVGIcon("icons/camera.svg", 16, 16), cameraTab);
        tabbedPane.addTab("Image", new FlatSVGIcon("icons/open.svg", 16, 16), imageTab);
        tabbedPane.addTab("Text", new FlatSVGIcon("icons/align-left.svg", 16, 16), textTab);
        tabbedPane.addChangeListener(_ -> onTabChanged());

        // Sidebar starts attached to camera tab
        cameraTab.attachSidebar();
        cameraTab.setActive(true);

        tabbedPane.setSelectedIndex(Math.min(settings.loadLastTab(), tabbedPane.getTabCount() - 1));

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        statusBar.info("Ready");
        setupDragAndDrop();
        installShortcuts();
        smartPack();
        setLocationRelativeTo(null);
    }

    private JTabbedPane tabbedPane() {
        return tabbedPane;
    }

    private void onTabChanged() {
        int selected = tabbedPane.getSelectedIndex();
        settings.saveLastTab(selected);

        cameraTab.detachSidebar();
        imageTab.detachSidebar();

        cameraTab.setActive(selected == 0);
        imageTab.setActive(selected == 1);

        switch (selected) {
            case 0 -> cameraTab.attachSidebar();
            case 1 -> imageTab.attachSidebar();
            default -> { /* text tab: no sidebar */ }
        }
        revalidate();
        repaint();
    }

    private void installShortcuts() {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JComponent root = getRootPane();
        bindShortcut(root, KeyStroke.getKeyStroke(KeyEvent.VK_1, mask), "tab-1", () -> tabbedPane.setSelectedIndex(0));
        bindShortcut(root, KeyStroke.getKeyStroke(KeyEvent.VK_2, mask), "tab-2", () -> tabbedPane.setSelectedIndex(1));
        bindShortcut(root, KeyStroke.getKeyStroke(KeyEvent.VK_3, mask), "tab-3", () -> tabbedPane.setSelectedIndex(2));
    }

    private static void bindShortcut(JComponent c, KeyStroke stroke, String name, Runnable action) {
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
        c.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
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

    private void setupDragAndDrop() {
        setDropTarget(new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY);
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        if (files != null && !files.isEmpty()) {
                            imageTab.imageDropHandler().accept(files.get(0));
                        }
                        dtde.dropComplete(true);
                    } else {
                        dtde.rejectDrop();
                    }
                } catch (Exception e) {
                    log.error("Drag and drop failed", e);
                    statusBar.error("Drag-and-drop failed: " + e.getMessage());
                    dtde.dropComplete(false);
                }
            }
        }));
    }

    private void shutdown() {
        running.set(false);
        textTab.saveBeforeShutdown();
        cameraTab.shutdown();
        dispose();
    }

    static JButton createActionButton(String text, String iconPath) {
        return UiTheme.primaryAction(text, iconPath);
    }
}
