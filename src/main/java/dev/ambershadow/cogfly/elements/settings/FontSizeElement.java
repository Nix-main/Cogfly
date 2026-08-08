package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.SettingsDialog;

import javax.swing.*;

public class FontSizeElement extends SettingsElement {
    public FontSizeElement(SettingsDialog parent) {
        JLabel label = new JLabel("Mod List Font Size");
        JComboBox<Integer> box = new JComboBox<>();
        box.addItem(8);
        box.addItem(12);
        box.addItem(16);
        box.addItem(20);
        box.addItem(24);
        box.addItem(28);
        box.addItem(32);
        box.addItem(36);
        box.addItem(40);
        box.setSelectedItem(Cogfly.settings.modFontSize);
        box.addActionListener(_ -> {
            @SuppressWarnings("DataFlowIssue") int n = (int)box.getSelectedItem();
            parent.update(s -> s.modFontSize = n);
        });
        label.setToolTipText("The font size of mods and their descriptions in the mod list.");
        add(label, box);
    }
}
