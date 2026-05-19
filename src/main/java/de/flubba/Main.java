package de.flubba;

import com.formdev.flatlaf.FlatLightLaf;

public class Main {
    public static void main(String[] args) {
        System.setProperty("flatlaf.useSystemFileChooser", "true");
        FlatLightLaf.setup();

        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                var frame = new SnapshotterFrame();
                frame.setVisible(true);
            } catch (Exception e) {
                throw new RuntimeException("Failed to start application", e);
            }
        });
    }
}
