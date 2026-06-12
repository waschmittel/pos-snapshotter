package de.flubba;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

public class StatusBar extends JPanel {

    private static final Color SUCCESS = new Color(0x2E7D32);
    private static final Color ERROR = new Color(0xC62828);
    private static final int AUTO_CLEAR_MS = 5_000;

    private final JLabel label;
    private final Timer clearTimer;

    public StatusBar() {
        super(new BorderLayout());
        Color sep = UIManager.getColor("Separator.foreground");
        if (sep == null) sep = new Color(0xCCCCCC);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, sep),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));

        label = new JLabel(" ");
        Font base = UIManager.getFont("Label.font");
        if (base != null) label.setFont(base);
        add(label, BorderLayout.WEST);

        clearTimer = new Timer(AUTO_CLEAR_MS, _ -> clear());
        clearTimer.setRepeats(false);
    }

    public void info(String text) {
        show(text, normalForeground(), false);
    }

    public void success(String text) {
        show(text, SUCCESS, true);
    }

    public void error(String text) {
        show(text, ERROR, true);
    }

    public void clear() {
        label.setText(" ");
        label.setForeground(normalForeground());
    }

    private void show(String text, Color color, boolean autoClear) {
        label.setForeground(color);
        label.setText(text);
        if (autoClear) {
            clearTimer.restart();
        } else {
            clearTimer.stop();
        }
    }

    private static Color normalForeground() {
        Color c = UIManager.getColor("Label.foreground");
        return c != null ? c : Color.DARK_GRAY;
    }
}
