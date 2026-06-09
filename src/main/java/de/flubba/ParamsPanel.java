package de.flubba;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class ParamsPanel extends JPanel {

    private final SettingsStore settings;

    private final JComboBox<String> printerCombo;
    private final JComboBox<DiffusionMatrix> matrixCombo;
    private final JSpinner preDitheringGammaSpinner;
    private final JSlider preDitheringGammaSlider;
    private final JSpinner sharpnessSpinner;
    private final JSlider sharpnessSlider;
    private final JSpinner contrastSpinner;
    private final JSlider contrastSlider;
    private final JSpinner grayLevelsSpinner;
    private final JSpinner claheTilesXSpinner;
    private final JSpinner claheClipLimitSpinner;
    private final JSlider claheClipLimitSlider;

    public ParamsPanel(SettingsStore settings) {
        super(new GridBagLayout());
        this.settings = settings;

        DitherParams saved = settings.currentDitherParams();
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 8, 0);

        JPanel generalPanel = createGroupPanel("General");

        String[] printers = PrinterService.getAvailablePrinters();
        printerCombo = new JComboBox<>(printers);
        String savedPrinter = settings.loadPrinterName();
        if (savedPrinter != null) {
            printerCombo.setSelectedItem(savedPrinter);
        } else {
            printerCombo.setSelectedItem(PrinterService.findDefaultPrinter());
        }
        printerCombo.setToolTipText("Target ESC/POS printer");
        printerCombo.addActionListener(_ -> settings.savePrinterName((String) printerCombo.getSelectedItem()));
        addSettingRow(generalPanel, "Printer:", printerCombo, 0);

        matrixCombo = new JComboBox<>(DiffusionMatrix.values());
        matrixCombo.setSelectedItem(saved.diffusionMatrix());
        matrixCombo.setToolTipText("<html>Error-diffusion dithering matrix.<br>"
                + "Floyd-Steinberg = balanced default.<br>"
                + "Jarvis-Judice-Ninke = smoother, slower.<br>"
                + "Sierra-Lite = sharper, faster.<br>"
                + "Flubba = custom tuning.</html>");
        matrixCombo.addActionListener(_ -> syncParams());
        addSettingRow(generalPanel, "Diffusion:", matrixCombo, 1);

        grayLevelsSpinner = new JSpinner(new SpinnerNumberModel(saved.grayLevels(), 2, 12, 1));
        grayLevelsSpinner.setToolTipText("Number of intensity levels the thermal head produces (2 = pure b/w, more = grayscale)");
        grayLevelsSpinner.addChangeListener(_ -> syncParams());
        addSettingRow(generalPanel, "Gray levels:", grayLevelsSpinner, 2);

        add(generalPanel, gbc);
        gbc.gridy++;

        JPanel adjustPanel = createGroupPanel("Image Adjustments");
        preDitheringGammaSlider = new JSlider(1, 30, (int) (saved.preDitheringGamma() * 10));
        preDitheringGammaSlider.setMajorTickSpacing(10);
        preDitheringGammaSlider.setPaintTicks(true);
        preDitheringGammaSpinner = new JSpinner(new SpinnerNumberModel(saved.preDitheringGamma(), 0.1, 3.0, 0.1));
        String gammaTip = "Brightness curve. <1 darkens, >1 brightens. Default 1.0";
        preDitheringGammaSlider.setToolTipText(gammaTip);
        preDitheringGammaSpinner.setToolTipText(gammaTip);
        linkSliderSpinner(preDitheringGammaSlider, preDitheringGammaSpinner, 10.0);
        addSettingRow(adjustPanel, "Brightness γ:", preDitheringGammaSlider, preDitheringGammaSpinner, 0);

        contrastSlider = new JSlider(5, 30, (int) (saved.contrast() * 10));
        contrastSlider.setMajorTickSpacing(10);
        contrastSlider.setPaintTicks(true);
        contrastSpinner = new JSpinner(new SpinnerNumberModel(saved.contrast(), 0.5, 3.0, 0.1));
        String contrastTip = "Contrast multiplier around mid-gray. Default 1.0";
        contrastSlider.setToolTipText(contrastTip);
        contrastSpinner.setToolTipText(contrastTip);
        linkSliderSpinner(contrastSlider, contrastSpinner, 10.0);
        addSettingRow(adjustPanel, "Contrast:", contrastSlider, contrastSpinner, 1);

        sharpnessSlider = new JSlider(0, 50, (int) (saved.sharpness() * 10));
        sharpnessSlider.setMajorTickSpacing(10);
        sharpnessSlider.setPaintTicks(true);
        sharpnessSpinner = new JSpinner(new SpinnerNumberModel(saved.sharpness(), 0.0, 5.0, 0.1));
        String sharpnessTip = "Unsharp-mask strength. 0 = off. Excess values cause halos.";
        sharpnessSlider.setToolTipText(sharpnessTip);
        sharpnessSpinner.setToolTipText(sharpnessTip);
        linkSliderSpinner(sharpnessSlider, sharpnessSpinner, 10.0);
        addSettingRow(adjustPanel, "Sharpness:", sharpnessSlider, sharpnessSpinner, 2);

        add(adjustPanel, gbc);
        gbc.gridy++;

        JPanel clahePanel = createGroupPanel("Local Contrast (CLAHE)");
        clahePanel.setToolTipText("Contrast Limited Adaptive Histogram Equalization — boosts local detail without blowing out highlights");
        claheTilesXSpinner = new JSpinner(new SpinnerNumberModel(saved.claheTilesX(), 1, 32, 1));
        claheTilesXSpinner.setToolTipText("Horizontal tile count. More tiles = more local adaptation. 1 disables CLAHE.");
        claheTilesXSpinner.addChangeListener(_ -> syncParams());
        addSettingRow(clahePanel, "Tiles X:", claheTilesXSpinner, 0);

        claheClipLimitSlider = new JSlider(10, 80, (int) (saved.claheClipLimit() * 10));
        claheClipLimitSlider.setMajorTickSpacing(20);
        claheClipLimitSlider.setPaintTicks(true);
        claheClipLimitSpinner = new JSpinner(new SpinnerNumberModel(saved.claheClipLimit(), 1.0, 8.0, 0.1));
        String clipTip = "Contrast clip limit. Higher = more aggressive local boost (risks noise).";
        claheClipLimitSlider.setToolTipText(clipTip);
        claheClipLimitSpinner.setToolTipText(clipTip);
        linkSliderSpinner(claheClipLimitSlider, claheClipLimitSpinner, 10.0);
        addSettingRow(clahePanel, "Clip Limit:", claheClipLimitSlider, claheClipLimitSpinner, 1);

        add(clahePanel, gbc);
        gbc.gridy++;

        JButton resetButton = new JButton("Reset to Defaults", new FlatSVGIcon("icons/reset.svg", 16, 16));
        resetButton.setToolTipText("Restore all dithering parameters to defaults");
        resetButton.addActionListener(_ -> resetToDefaults());
        gbc.insets = new Insets(8, 4, 0, 4);
        add(resetButton, gbc);

        gbc.gridy++;
        gbc.weighty = 1.0;
        add(new JPanel(), gbc);

        setPreferredSize(new Dimension(300, 600));
    }

    public String getSelectedPrinter() {
        return (String) printerCombo.getSelectedItem();
    }

    private JPanel createGroupPanel(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD));
        panel.setBorder(BorderFactory.createCompoundBorder(
                border,
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        return panel;
    }

    private void addSettingRow(JPanel panel, String labelText, Component control, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 4, 2, 8);
        panel.add(new JLabel(labelText), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(2, 0, 2, 4);
        panel.add(control, gbc);
    }

    private void addSettingRow(JPanel panel, String labelText, JSlider slider, JSpinner spinner, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 4, 2, 8);
        panel.add(new JLabel(labelText), gbc);

        JPanel combo = new JPanel(new BorderLayout(4, 0));
        combo.add(slider, BorderLayout.CENTER);
        spinner.setPreferredSize(new Dimension(72, spinner.getPreferredSize().height));
        combo.add(spinner, BorderLayout.EAST);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(2, 0, 2, 4);
        panel.add(combo, gbc);
    }

    private void linkSliderSpinner(JSlider slider, JSpinner spinner, double factor) {
        slider.addChangeListener(_ -> {
            if (slider.getValueIsAdjusting()) {
                spinner.setValue(slider.getValue() / factor);
            }
        });
        spinner.addChangeListener(_ -> {
            slider.setValue((int) (((Number) spinner.getValue()).doubleValue() * factor));
            syncParams();
        });
        ((JSpinner.NumberEditor) spinner.getEditor()).getFormat().setMinimumFractionDigits(1);
    }

    private void syncParams() {
        var params = new DitherParams(
                (DiffusionMatrix) matrixCombo.getSelectedItem(),
                (double) preDitheringGammaSpinner.getValue(),
                (double) sharpnessSpinner.getValue(),
                (double) contrastSpinner.getValue(),
                (int) grayLevelsSpinner.getValue(),
                (int) claheTilesXSpinner.getValue(),
                (double) claheClipLimitSpinner.getValue()
        );
        settings.updateDitherParams(params);
    }

    private void resetToDefaults() {
        settings.resetDitherParams();
        var defaults = DitherParams.defaults();
        matrixCombo.setSelectedItem(defaults.diffusionMatrix());
        preDitheringGammaSpinner.setValue(defaults.preDitheringGamma());
        preDitheringGammaSlider.setValue((int) (defaults.preDitheringGamma() * 10));
        sharpnessSpinner.setValue(defaults.sharpness());
        sharpnessSlider.setValue((int) (defaults.sharpness() * 10));
        contrastSpinner.setValue(defaults.contrast());
        contrastSlider.setValue((int) (defaults.contrast() * 10));
        grayLevelsSpinner.setValue(defaults.grayLevels());
        claheTilesXSpinner.setValue(defaults.claheTilesX());
        claheClipLimitSpinner.setValue(defaults.claheClipLimit());
        claheClipLimitSlider.setValue((int) (defaults.claheClipLimit() * 10));
    }
}
