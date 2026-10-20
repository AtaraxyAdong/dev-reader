package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.github.yuyuanweb.mianshiyaplugin.utils.ThemeUtil;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/** Live font-size controls; incomplete numeric edits never overwrite the saved value. */
final class ReaderFontSizePopup extends JPanel {
    private final ReaderAppearanceSettings settings = ReaderAppearanceSettings.getInstance();
    private final JSlider slider = new JSlider(ReaderAppearanceSettings.MIN_FONT_SIZE, ReaderAppearanceSettings.MAX_FONT_SIZE);
    private final JTextField value = new JTextField(4);
    private final JLabel hint = new JLabel();
    private boolean syncing;

    private ReaderFontSizePopup() {
        super(new BorderLayout(JBUI.scale(10), JBUI.scale(8)));
        setBorder(JBUI.Borders.empty(12));
        JPanel row = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        slider.setPreferredSize(new Dimension(JBUI.scale(190), slider.getPreferredSize().height));
        slider.setMajorTickSpacing(6);
        slider.setMinorTickSpacing(1);
        slider.setPaintTicks(true);
        slider.setSnapToTicks(true);
        slider.getAccessibleContext().setAccessibleName(ReaderMessages.text("font.label"));
        value.getAccessibleContext().setAccessibleName(ReaderMessages.text("font.label"));
        JPanel number = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
        number.add(value);
        number.add(new JLabel("px"));
        row.add(slider, BorderLayout.CENTER);
        row.add(number, BorderLayout.EAST);
        add(row, BorderLayout.NORTH);
        add(hint, BorderLayout.CENTER);
        JButton reset = new JButton(ReaderMessages.text("font.reset"));
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        footer.add(reset);
        add(footer, BorderLayout.SOUTH);
        refreshControls();
        slider.addChangeListener(event -> {
            if (syncing) return;
            settings.setFontSize(slider.getValue());
            refreshControls();
        });
        value.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyTypedValue(); }
            @Override public void removeUpdate(DocumentEvent e) { applyTypedValue(); }
            @Override public void changedUpdate(DocumentEvent e) { applyTypedValue(); }
        });
        value.addActionListener(event -> refreshControls());
        value.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { refreshControls(); }
        });
        reset.addActionListener(event -> {
            settings.setFontSize(0);
            refreshControls();
        });
    }

    private void applyTypedValue() {
        if (syncing) return;
        try {
            int size = Integer.parseInt(value.getText().trim());
            if (size < ReaderAppearanceSettings.MIN_FONT_SIZE || size > ReaderAppearanceSettings.MAX_FONT_SIZE) {
                hint.setText(ReaderMessages.text("font.invalid"));
                return;
            }
            syncing = true;
            try {
                slider.setValue(size);
                settings.setFontSize(size);
                hint.setText(ReaderMessages.text("font.range"));
            } finally { syncing = false; }
        } catch (NumberFormatException invalid) {
            hint.setText(ReaderMessages.text("font.invalid"));
        }
    }

    private void refreshControls() {
        syncing = true;
        try {
            int size = ThemeUtil.readerFontSize();
            slider.setValue(size);
            value.setText(Integer.toString(size));
            hint.setText(ReaderMessages.text(settings.followsIde() ? "font.default" : "font.range"));
        } finally { syncing = false; }
    }

    static void show(Component owner) {
        ReaderFontSizePopup panel = new ReaderFontSizePopup();
        JBPopupFactory.getInstance().createComponentPopupBuilder(panel, panel.value)
                .setTitle(ReaderMessages.text("font.label"))
                .setFocusable(true).setRequestFocus(true)
                .setCancelOnClickOutside(true).setCancelOnWindowDeactivation(true)
                .setResizable(false).setMovable(false)
                .createPopup().showInCenterOf(owner);
    }
}