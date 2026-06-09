package de.flubba;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.JButton;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

final class UiTheme {

    private UiTheme() {
    }

    static Color accent() {
        Color c = UIManager.getColor("Component.accentColor");
        return c != null ? c : new Color(0, 120, 215);
    }

    static Color accentHover() {
        Color base = accent();
        return new Color(
                Math.min(255, base.getRed() + 20),
                Math.min(255, base.getGreen() + 20),
                Math.min(255, base.getBlue() + 20));
    }

    static JButton primaryAction(String text, String iconPath) {
        JButton button = new JButton(text, new FlatSVGIcon(iconPath, 32, 32));
        applyPrimary(button, baseFont().deriveFont(Font.BOLD, 28f));
        button.setPreferredSize(new Dimension(0, 72));
        return button;
    }

    static JButton primarySmall(String text, String iconPath) {
        JButton button = new JButton(text, new FlatSVGIcon(iconPath, 16, 16));
        applyPrimary(button, baseFont().deriveFont(Font.BOLD, 14f));
        return button;
    }

    private static void applyPrimary(JButton button, Font font) {
        button.setFont(font);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setBackground(accent());
        button.setForeground(Color.WHITE);
    }

    private static Font baseFont() {
        Font f = UIManager.getFont("Button.font");
        return f != null ? f : new Font(Font.DIALOG, Font.PLAIN, 13);
    }
}
