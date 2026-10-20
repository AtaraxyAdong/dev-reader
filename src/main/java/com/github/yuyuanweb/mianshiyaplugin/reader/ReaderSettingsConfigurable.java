package com.github.yuyuanweb.mianshiyaplugin.reader;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/** Application settings for the reading window's visible name. */
public final class ReaderSettingsConfigurable implements SearchableConfigurable {
    private JPanel panel;
    private JTextField nameField;

    @Override public @NotNull String getId() { return "mianshiya.reader.settings"; }
    @Override public String getDisplayName() { return "阅读设置"; }

    @Override public JComponent createComponent() {
        if (panel != null) return panel;
        panel = new JPanel(new BorderLayout());
        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = JBUI.insets(0, 0, 8, 8);
        constraints.gridx = 0;
        constraints.gridy = 0;
        JLabel label = new JLabel("侧栏窗口名称：");
        fields.add(label, constraints);
        nameField = new JTextField(24);
        label.setLabelFor(nameField);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        fields.add(nameField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 2;
        fields.add(new JLabel("留空使用“阅读”。应用后，所有项目的阅读窗口立即更新。"), constraints);
        panel.add(fields, BorderLayout.NORTH);
        reset();
        return panel;
    }

    @Override public boolean isModified() {
        return nameField != null && !ReaderAppearanceSettings.normalizeToolWindowName(nameField.getText())
                .equals(ReaderAppearanceSettings.getInstance().getToolWindowName());
    }

    @Override public void apply() {
        if (nameField == null) return;
        ReaderAppearanceSettings.getInstance().setToolWindowName(nameField.getText());
        reset();
    }

    @Override public void reset() {
        if (nameField != null) nameField.setText(ReaderAppearanceSettings.getInstance().getToolWindowName());
    }

    @Override public void disposeUIResources() { panel = null; nameField = null; }
}
