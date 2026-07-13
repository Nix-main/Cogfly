package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.SettingsDialog;

import javax.swing.*;

public class PerProfileGamePathsElement extends SettingsElement {

    public PerProfileGamePathsElement(SettingsDialog parent) {
        JLabel label = new JLabel("Allow Per-Profile Game Paths");
        JCheckBox checkBox = new JCheckBox();
        checkBox.addActionListener(_ -> parent.update(s -> s.profileSpecificPaths = checkBox.isSelected()));
        checkBox.setSelected(Cogfly.settings.profileSpecificPaths);
        label.setToolTipText("Allows you to point specific profiles to different game installations. This feature is primarily for speedrunners.");
        add(label, checkBox);
    }
}
