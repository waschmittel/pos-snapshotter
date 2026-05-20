package de.flubba;

import com.formdev.flatlaf.FlatLightLaf;

public class Main {
    public static void main(String[] args) {
        boolean enableServer = false;
        boolean headless = false;

        for (String arg : args) {
            if ("--server".equals(arg)) enableServer = true;
            if ("--headless".equals(arg)) headless = true;
        }

        SettingsStore settingsStore = new SettingsStore();

        if (enableServer) {
            try {
                new WebServer(8080, settingsStore).start();
            } catch (Exception e) {
                System.err.println("Failed to start web server: " + e.getMessage());
            }
        }

        if (headless) {
            System.out.println("Running in headless mode. Press Ctrl+C to exit.");
            return;
        }

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
