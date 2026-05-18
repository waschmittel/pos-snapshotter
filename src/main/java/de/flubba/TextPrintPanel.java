package de.flubba;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.SystemFileChooser;
import lombok.extern.slf4j.Slf4j;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class TextPrintPanel extends JPanel {
    private static final Path SAVE_DIR = Path.of(System.getProperty("user.home"), ".posSnapshotter");
    private static final Path AUTO_SAVE_FILE = SAVE_DIR.resolve("lastDocument.html");
    private static final int PRINTER_WIDTH = 512;

    private final JTextPane editor;
    private final HTMLEditorKit htmlKit = new HTMLEditorKit();
    private final Timer autoSaveTimer;
    private final Timer previewTimer;
    private final PreviewPanel previewPanel;

    // toolbar controls
    private final JToggleButton boldButton;
    private final JToggleButton italicButton;
    private final JToggleButton underlineButton;
    private final JSpinner fontSizeSpinner;
    private final JComboBox<String> fontFamilyCombo;
    private final AtomicReference<DitherParams> currentParams;

    private boolean updatingToolbar = false;

    public TextPrintPanel(AtomicReference<DitherParams> currentParams) {
        this.currentParams = currentParams;
        setLayout(new BorderLayout());

        editor = new JTextPane();
        editor.setCaret(new PersistentCaret());
        editor.setFont(new Font("Serif", Font.PLAIN, 16));
        editor.setContentType("text/html");

        // --- Shortcuts ---
        setupShortcuts();

        // --- Toolbar ---
        boldButton = new JToggleButton(new FlatSVGIcon("icons/bold.svg", 16, 16));
        boldButton.setToolTipText("Bold (Ctrl+B)");
        boldButton.setFocusable(false);
        boldButton.addActionListener(_ -> applyStyle(StyleConstants.Bold, boldButton.isSelected()));

        italicButton = new JToggleButton(new FlatSVGIcon("icons/italic.svg", 16, 16));
        italicButton.setToolTipText("Italic (Ctrl+I)");
        italicButton.setFocusable(false);
        italicButton.addActionListener(_ -> applyStyle(StyleConstants.Italic, italicButton.isSelected()));

        underlineButton = new JToggleButton(new FlatSVGIcon("icons/underline.svg", 16, 16));
        underlineButton.setToolTipText("Underline (Ctrl+U)");
        underlineButton.setFocusable(false);
        underlineButton.addActionListener(_ -> applyStyle(StyleConstants.Underline, underlineButton.isSelected()));

        fontSizeSpinner = new JSpinner(new SpinnerNumberModel(16, 8, 72, 1));
        fontSizeSpinner.setToolTipText("Font size");
        // Only buttons non-focusable, text field stays focusable to allow typing
        for (Component child : fontSizeSpinner.getComponents()) {
            if (child instanceof JButton) child.setFocusable(false);
        }

        fontSizeSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (!updatingToolbar) {
                    applyStyle(StyleConstants.FontSize, fontSizeSpinner.getValue());
                }
            }
        });

        String[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        fontFamilyCombo = new JComboBox<>(fonts);
        fontFamilyCombo.setSelectedItem("Serif");
        fontFamilyCombo.setToolTipText("Font family");
        fontFamilyCombo.setRenderer(new FontListRenderer());
        fontFamilyCombo.setFocusable(false);
        fontFamilyCombo.addActionListener(_ -> {
            if (!updatingToolbar) {
                applyStyle(StyleConstants.FontFamily, fontFamilyCombo.getSelectedItem());
            }
        });

        JButton alignLeftButton = new JButton(new FlatSVGIcon("icons/align-left.svg", 16, 16));
        alignLeftButton.setToolTipText("Align left");
        alignLeftButton.setFocusable(false);
        alignLeftButton.addActionListener(_ -> applyAlignment(StyleConstants.ALIGN_LEFT));

        JButton alignCenterButton = new JButton(new FlatSVGIcon("icons/align-center.svg", 16, 16));
        alignCenterButton.setToolTipText("Align center");
        alignCenterButton.setFocusable(false);
        alignCenterButton.addActionListener(_ -> applyAlignment(StyleConstants.ALIGN_CENTER));

        JButton alignRightButton = new JButton(new FlatSVGIcon("icons/align-right.svg", 16, 16));
        alignRightButton.setToolTipText("Align right");
        alignRightButton.setFocusable(false);
        alignRightButton.addActionListener(_ -> applyAlignment(StyleConstants.ALIGN_RIGHT));

        JButton alignJustifyButton = new JButton(new FlatSVGIcon("icons/align-justify.svg", 16, 16));
        alignJustifyButton.setToolTipText("Align justified");
        alignJustifyButton.setFocusable(false);
        alignJustifyButton.addActionListener(_ -> applyAlignment(StyleConstants.ALIGN_JUSTIFIED));

        JButton resetContentsButton = new JButton(new FlatSVGIcon("icons/reset.svg", 16, 16));
        resetContentsButton.setToolTipText("Clear text");
        resetContentsButton.setFocusable(false);
        resetContentsButton.addActionListener(_ -> {
            if (JOptionPane.showConfirmDialog(this, "Clear all text?", "Clear", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                resetDocument();
            }
        });

        JButton printButton = new JButton("Print", new FlatSVGIcon("icons/print.svg", 16, 16));
        printButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        printButton.setBackground(new Color(0, 120, 215));
        printButton.setForeground(Color.WHITE);
        printButton.setOpaque(true);
        printButton.setBorderPainted(false);
        printButton.setFocusable(false);
        printButton.addActionListener(_ -> printText());

        JButton openButton = new JButton(new FlatSVGIcon("icons/open.svg", 16, 16));
        openButton.setToolTipText("Open");
        openButton.setFocusable(false);
        openButton.addActionListener(_ -> openFile());

        JButton saveButton = new JButton(new FlatSVGIcon("icons/save.svg", 16, 16));
        saveButton.setToolTipText("Save");
        saveButton.setFocusable(false);
        saveButton.addActionListener(_ -> saveFile());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        toolbar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        toolbar.add(boldButton);
        toolbar.add(italicButton);
        toolbar.add(underlineButton);
        toolbar.add(fontSizeSpinner);
        toolbar.add(fontFamilyCombo);
        toolbar.add(alignLeftButton);
        toolbar.add(alignCenterButton);
        toolbar.add(alignRightButton);
        toolbar.add(alignJustifyButton);
        toolbar.add(resetContentsButton);
        toolbar.add(openButton);
        toolbar.add(saveButton);
        toolbar.add(printButton);

        JScrollPane editorScrollPane = new JScrollPane(editor);
        editorScrollPane.setPreferredSize(new Dimension(400, 512));

        previewPanel = new PreviewPanel();
        JScrollPane previewScrollPane = new JScrollPane(previewPanel);
        previewScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        previewScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        int sbWidth = previewScrollPane.getVerticalScrollBar().getPreferredSize().width;
        if (sbWidth <= 0) sbWidth = 15; // fallback
        Dimension previewSize = new Dimension(PRINTER_WIDTH + sbWidth, 512);
        previewScrollPane.setPreferredSize(previewSize);
        previewScrollPane.setMinimumSize(previewSize);
        previewScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorScrollPane, previewScrollPane);
        splitPane.setResizeWeight(1.0); // editor gets the extra space when resizing

        add(toolbar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        // --- Caret listener to sync toolbar state ---
        editor.addCaretListener(_ -> updateToolbarState());

        // --- Live Preview & Auto-save ---
        previewTimer = new Timer(1000, _ -> updatePreview());
        previewTimer.setRepeats(false);

        autoSaveTimer = new Timer(2000, _ -> autoSave());
        autoSaveTimer.setRepeats(false);

        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                restartTimers();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                restartTimers();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                restartTimers();
            }
        });

        // --- Restore last content ---
        loadAutoSaved();
        updatePreview();
    }

    private void restartTimers() {
        previewTimer.restart();
        autoSaveTimer.restart();
    }

    private void resetDocument() {
        var newDoc = (HTMLDocument) htmlKit.createDefaultDocument();
        editor.setDocument(newDoc);

        // Re-attach document listener to the new instance
        newDoc.addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { restartTimers(); }
            @Override public void removeUpdate(DocumentEvent e) { restartTimers(); }
            @Override public void changedUpdate(DocumentEvent e) { restartTimers(); }
        });

        // Trigger UI sync
        updateToolbarState();
        restartTimers();
    }

    private void updatePreview() {
        SwingUtilities.invokeLater(() -> {
            BufferedImage image = renderEditorToImage();
            if (image != null) {
                BufferedImage dithered = Dithering.toDitheredImage(image, currentParams.get());
                previewPanel.setImage(dithered);
            }
        });
    }

    private void setupShortcuts() {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_B, mask), "toggle-bold");
        editor.getActionMap().put("toggle-bold", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boldButton.doClick();
            }
        });

        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_I, mask), "toggle-italic");
        editor.getActionMap().put("toggle-italic", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                italicButton.doClick();
            }
        });

        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_U, mask), "toggle-underline");
        editor.getActionMap().put("toggle-underline", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                underlineButton.doClick();
            }
        });

        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_P, mask), "print-shortcut");
        editor.getActionMap().put("print-shortcut", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                printText();
            }
        });
    }

    private void applyStyle(Object attribute, Object value) {
        StyledDocument doc = editor.getStyledDocument();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();

        if (start == end) {
            // No selection: apply to future typing via input attributes
            var attrs = new SimpleAttributeSet(editor.getInputAttributes());
            if (value instanceof Boolean b) {
                if (attribute == StyleConstants.Bold) StyleConstants.setBold(attrs, b);
                if (attribute == StyleConstants.Italic) StyleConstants.setItalic(attrs, b);
                if (attribute == StyleConstants.Underline) StyleConstants.setUnderline(attrs, b);
            } else if (attribute == StyleConstants.FontSize) {
                StyleConstants.setFontSize(attrs, (int) value);
            } else if (attribute == StyleConstants.FontFamily) {
                StyleConstants.setFontFamily(attrs, (String) value);
            }
            editor.setCharacterAttributes(attrs, false);
        } else {
            // Selection exists: apply to selected text
            var attrs = new SimpleAttributeSet();
            if (value instanceof Boolean b) {
                if (attribute == StyleConstants.Bold) StyleConstants.setBold(attrs, b);
                if (attribute == StyleConstants.Italic) StyleConstants.setItalic(attrs, b);
                if (attribute == StyleConstants.Underline) StyleConstants.setUnderline(attrs, b);
            } else if (attribute == StyleConstants.FontSize) {
                StyleConstants.setFontSize(attrs, (int) value);
            } else if (attribute == StyleConstants.FontFamily) {
                StyleConstants.setFontFamily(attrs, (String) value);
            }
            doc.setCharacterAttributes(start, end - start, attrs, false);
        }
        editor.requestFocusInWindow();
    }

    private void applyAlignment(int alignment) {
        StyledDocument doc = editor.getStyledDocument();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        var attrs = new SimpleAttributeSet();
        StyleConstants.setAlignment(attrs, alignment);
        doc.setParagraphAttributes(start, end - start, attrs, false);
        editor.requestFocusInWindow();
    }

    private void updateToolbarState() {
        updatingToolbar = true;
        try {
            AttributeSet attrs = editor.getInputAttributes();
            boldButton.setSelected(StyleConstants.isBold(attrs));
            italicButton.setSelected(StyleConstants.isItalic(attrs));
            underlineButton.setSelected(StyleConstants.isUnderline(attrs));
            fontSizeSpinner.setValue(StyleConstants.getFontSize(attrs));
            String family = StyleConstants.getFontFamily(attrs);
            fontFamilyCombo.setSelectedItem(family);
        } finally {
            updatingToolbar = false;
        }
    }

    private void printText() {
        try {
            BufferedImage image = renderEditorToImage();
            if (image == null) return;
            var chunks = Dithering.toDitheredChunksPortrait(image, currentParams.get());
            Main.printIt(chunks);
        } catch (IOException e) {
            log.error("Error printing text", e);
            JOptionPane.showMessageDialog(this, "Print error: " + e.getMessage(),
                    "Print Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private BufferedImage renderEditorToImage() {
        if (editor.getDocument().getLength() == 0) return null;

        // Create offscreen editor at printer width
        JTextPane offscreen = new JTextPane();
        offscreen.setEditorKit(htmlKit);
        offscreen.setDocument(editor.getDocument());
        offscreen.setSize(PRINTER_WIDTH, 10000); // large enough height

        // Force layout of views
        var ui = offscreen.getUI();
        var rootView = ui.getRootView(offscreen);
        rootView.setSize(PRINTER_WIDTH, 10000);
        int height = (int) rootView.getPreferredSpan(javax.swing.text.View.Y_AXIS);
        offscreen.setSize(PRINTER_WIDTH, height);

        BufferedImage img = new BufferedImage(PRINTER_WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, PRINTER_WIDTH, height);
        offscreen.paint(g2);
        g2.dispose();
        return img;
    }

    // --- File I/O ---

    private Frame getParentFrame() {
        return (Frame) SwingUtilities.getWindowAncestor(this);
    }

    private void openFile() {
        var fc = new SystemFileChooser();
        fc.setDialogTitle("Open HTML File");
        fc.setFileFilter(new SystemFileChooser.FileNameExtensionFilter("HTML Files (*.html)", "html"));

        if (fc.showOpenDialog(getParentFrame()) == SystemFileChooser.APPROVE_OPTION) {
            loadHtmlFile(fc.getSelectedFile());
        }
    }

    private void saveFile() {
        var fc = new SystemFileChooser();
        fc.setDialogTitle("Save HTML File");
        fc.setFileFilter(new SystemFileChooser.FileNameExtensionFilter("HTML Files (*.html)", "html"));
        fc.setSelectedFile(new File("document.html"));

        if (fc.showSaveDialog(getParentFrame()) == SystemFileChooser.APPROVE_OPTION) {
            var file = fc.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".html")) {
                file = new File(file.getAbsolutePath() + ".html");
            }
            saveHtmlToFile(file);
        }
    }

    private void loadHtmlFile(File file) {
        try (var fis = new FileInputStream(file)) {
            editor.setText("");
            htmlKit.read(fis, editor.getDocument(), 0);
        } catch (Exception e) {
            log.error("Error loading HTML file: {}", file, e);
            JOptionPane.showMessageDialog(this, "Error loading file: " + e.getMessage(),
                    "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveHtmlToFile(File file) {
        try (var fos = new FileOutputStream(file)) {
            htmlKit.write(fos, editor.getDocument(), 0, editor.getDocument().getLength());
        } catch (Exception e) {
            log.error("Error saving HTML file: {}", file, e);
            JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // --- Auto-save / restore ---

    private void autoSave() {
        try {
            Files.createDirectories(SAVE_DIR);
            saveHtmlToFile(AUTO_SAVE_FILE.toFile());
            log.debug("Auto-saved editor content");
        } catch (Exception e) {
            log.warn("Auto-save failed", e);
        }
    }

    private void loadAutoSaved() {
        if (Files.exists(AUTO_SAVE_FILE)) {
            loadHtmlFile(AUTO_SAVE_FILE.toFile());
            log.info("Restored editor content from auto-save");
        }
    }

    /**
     * Save content before shutdown.
     */
    public void saveBeforeShutdown() {
        autoSaveTimer.stop();
        previewTimer.stop();
        autoSave();
    }

    /**
     * Custom renderer that displays the font name in its own font.
     */
    private static class FontListRenderer extends DefaultListCellRenderer {
        private final Map<String, Font> fontCache = new HashMap<>();

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof String fontName) {
                Font font = fontCache.computeIfAbsent(fontName, name -> new Font(name, Font.PLAIN, 14));
                setFont(font);
                setText(fontName);
            }
            return this;
        }
    }

    /**
     * Panel to display the dithered preview at actual size.
     */
    private static class PreviewPanel extends JPanel {
        private BufferedImage image;

        void setImage(BufferedImage img) {
            this.image = img;
            if (img != null) {
                setPreferredSize(new Dimension(img.getWidth(), img.getHeight()));
            }
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image != null) {
                g.drawImage(image, 0, 0, null);
            } else {
                g.setColor(Color.LIGHT_GRAY);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Color.DARK_GRAY);
                g.drawString("No preview available", 20, 30);
            }
        }
    }
}
