package de.flubba;

import lombok.extern.slf4j.Slf4j;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.rtf.RTFEditorKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;

@Slf4j
public class TextPrintPanel extends JPanel {
    private static final Path SAVE_DIR = Path.of(System.getProperty("user.home"), ".posSnapshotter");
    private static final Path AUTO_SAVE_FILE = SAVE_DIR.resolve("lastDocument.rtf");
    private static final int PRINTER_WIDTH = 512;

    private final JTextPane editor;
    private final RTFEditorKit rtfKit = new RTFEditorKit();
    private final Timer autoSaveTimer;

    // toolbar controls
    private final JToggleButton boldButton;
    private final JToggleButton italicButton;
    private final JToggleButton underlineButton;
    private final JSpinner fontSizeSpinner;
    private final JComboBox<String> fontFamilyCombo;
    private final AtomicReference<DitherParams> currentParams;

    public TextPrintPanel(AtomicReference<DitherParams> currentParams) {
        this.currentParams = currentParams;
        setLayout(new BorderLayout());

        editor = new JTextPane();
        editor.setFont(new Font("Serif", Font.PLAIN, 16));
        editor.setContentType("text/rtf");

        // --- Toolbar ---
        boldButton = new JToggleButton("B");
        boldButton.setFont(new Font("Serif", Font.BOLD, 14));
        boldButton.setToolTipText("Bold");
        boldButton.addActionListener(_ -> applyStyle(StyleConstants.Bold, boldButton.isSelected()));

        italicButton = new JToggleButton("I");
        italicButton.setFont(new Font("Serif", Font.ITALIC, 14));
        italicButton.setToolTipText("Italic");
        italicButton.addActionListener(_ -> applyStyle(StyleConstants.Italic, italicButton.isSelected()));

        underlineButton = new JToggleButton("U");
        underlineButton.setFont(new Font("Serif", Font.PLAIN, 14));
        underlineButton.setToolTipText("Underline");
        underlineButton.addActionListener(_ -> applyStyle(StyleConstants.Underline, underlineButton.isSelected()));

        fontSizeSpinner = new JSpinner(new SpinnerNumberModel(16, 8, 72, 1));
        fontSizeSpinner.setToolTipText("Font size");
        fontSizeSpinner.addChangeListener(_ -> applyStyle(StyleConstants.FontSize, (int) fontSizeSpinner.getValue()));

        String[] fonts = {"Serif", "SansSerif", "Monospaced"};
        fontFamilyCombo = new JComboBox<>(fonts);
        fontFamilyCombo.setToolTipText("Font family");
        fontFamilyCombo.addActionListener(_ -> applyStyle(StyleConstants.FontFamily, (String) fontFamilyCombo.getSelectedItem()));

        JButton alignLeftButton = new JButton("\u25C0");
        alignLeftButton.setToolTipText("Align left");
        alignLeftButton.addActionListener(_ -> applyAlignment(StyleConstants.ALIGN_LEFT));

        JButton alignCenterButton = new JButton("\u25CF");
        alignCenterButton.setToolTipText("Align center");
        alignCenterButton.addActionListener(_ -> applyAlignment(StyleConstants.ALIGN_CENTER));

        JButton alignRightButton = new JButton("\u25B6");
        alignRightButton.setToolTipText("Align right");
        alignRightButton.addActionListener(_ -> applyAlignment(StyleConstants.ALIGN_RIGHT));

        JButton printButton = SnapshotterFrame.createActionButton("Print");
        printButton.addActionListener(_ -> printText());

        JButton openButton = new JButton("Open");
        openButton.addActionListener(_ -> openFile());

        JButton saveButton = new JButton("Save");
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
        toolbar.add(openButton);
        toolbar.add(saveButton);
        toolbar.add(printButton);

        JScrollPane scrollPane = new JScrollPane(editor);
        scrollPane.setPreferredSize(new Dimension(800, 512));

        add(toolbar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        // --- Caret listener to sync toolbar state ---
        editor.addCaretListener(_ -> updateToolbarState());

        // --- Auto-save with 2s debounce ---
        autoSaveTimer = new Timer(2000, _ -> autoSave());
        autoSaveTimer.setRepeats(false);
        editor.getStyledDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { autoSaveTimer.restart(); }
            @Override public void removeUpdate(DocumentEvent e) { autoSaveTimer.restart(); }
            @Override public void changedUpdate(DocumentEvent e) { autoSaveTimer.restart(); }
        });

        // --- Restore last content ---
        loadAutoSaved();
    }

    private void applyStyle(Object attribute, Object value) {
        StyledDocument doc = editor.getStyledDocument();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();

        if (start == end) {
            // No selection: apply to future typing via input attributes
            var attrs = new SimpleAttributeSet(editor.getInputAttributes());
            if (value instanceof Boolean b) {
                StyleConstants.setBold(attrs, attribute == StyleConstants.Bold && b);
                StyleConstants.setItalic(attrs, attribute == StyleConstants.Italic && b);
                StyleConstants.setUnderline(attrs, attribute == StyleConstants.Underline && b);
                // Only set the specific attribute being toggled
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
        AttributeSet attrs = editor.getInputAttributes();
        boldButton.setSelected(StyleConstants.isBold(attrs));
        italicButton.setSelected(StyleConstants.isItalic(attrs));
        underlineButton.setSelected(StyleConstants.isUnderline(attrs));
        fontSizeSpinner.setValue(StyleConstants.getFontSize(attrs));
        String family = StyleConstants.getFontFamily(attrs);
        fontFamilyCombo.setSelectedItem(family);
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
        StyledDocument doc = editor.getStyledDocument();
        if (doc.getLength() == 0) return null;

        // Create offscreen editor at printer width
        JTextPane offscreen = new JTextPane();
        offscreen.setStyledDocument(doc);
        offscreen.setSize(PRINTER_WIDTH, Short.MAX_VALUE);
        Dimension preferred = offscreen.getPreferredSize();
        int height = preferred.height;
        offscreen.setSize(PRINTER_WIDTH, height);

        // Force layout
        offscreen.doLayout();

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

    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Rich Text Files (*.rtf)", "rtf"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadRtfFile(chooser.getSelectedFile());
        }
    }

    private void saveFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Rich Text Files (*.rtf)", "rtf"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".rtf")) {
                file = new File(file.getAbsolutePath() + ".rtf");
            }
            saveRtfToFile(file);
        }
    }

    private void loadRtfFile(File file) {
        try (var fis = new FileInputStream(file)) {
            editor.setText("");
            rtfKit.read(fis, editor.getStyledDocument(), 0);
        } catch (Exception e) {
            log.error("Error loading RTF file: {}", file, e);
            JOptionPane.showMessageDialog(this, "Error loading file: " + e.getMessage(),
                    "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveRtfToFile(File file) {
        try (var fos = new FileOutputStream(file)) {
            rtfKit.write(fos, editor.getStyledDocument(), 0, editor.getStyledDocument().getLength());
        } catch (Exception e) {
            log.error("Error saving RTF file: {}", file, e);
            JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // --- Auto-save / restore ---

    private void autoSave() {
        try {
            Files.createDirectories(SAVE_DIR);
            saveRtfToFile(AUTO_SAVE_FILE.toFile());
            log.debug("Auto-saved editor content");
        } catch (Exception e) {
            log.warn("Auto-save failed", e);
        }
    }

    private void loadAutoSaved() {
        if (Files.exists(AUTO_SAVE_FILE)) {
            loadRtfFile(AUTO_SAVE_FILE.toFile());
            log.info("Restored editor content from auto-save");
        }
    }

    /** Save content before shutdown. */
    public void saveBeforeShutdown() {
        autoSaveTimer.stop();
        autoSave();
    }
}
