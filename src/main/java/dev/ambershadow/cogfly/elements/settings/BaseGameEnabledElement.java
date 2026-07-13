package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.SettingsDialog;

import javax.swing.*;

public class BaseGameEnabledElement extends SettingsElement {

    public BaseGameEnabledElement(SettingsDialog parent) {
        JLabel label = new JLabel("Allow Base Game Modding");
        JCheckBox checkBox = new JCheckBox();
        checkBox.addActionListener(_ -> {
            boolean enabled = checkBox.isSelected();
            parent.updateBaseGameModding(enabled);
        });
        checkBox.setSelected(Cogfly.settings.baseGameEnabled);
        label.setToolTipText("If enabled, this displays a new profile for installing mods directly to your game directory. This is primarily for mod developers.");
        add(label, checkBox);
    }
}
