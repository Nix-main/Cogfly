package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.SettingsDialog;

import javax.swing.*;

public class InstalledModsOnTopElement extends SettingsElement {
    public InstalledModsOnTopElement(SettingsDialog parent) {
        JLabel label = new JLabel("Show Installed Mods On Top");
        JCheckBox checkBox = new JCheckBox();
        checkBox.addActionListener(_ -> {
            boolean enabled = checkBox.isSelected();
            parent.updateShowInstalledModsOnTop(enabled);
        });
        checkBox.setSelected(Cogfly.settings.showInstalledModsOnTop);
        label.setToolTipText("Always show installed mods before any others");
        add(label, checkBox);
    }
}
