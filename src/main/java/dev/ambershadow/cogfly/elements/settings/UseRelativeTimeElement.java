package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.SettingsDialog;

import javax.swing.*;

public class UseRelativeTimeElement extends SettingsElement {

    public UseRelativeTimeElement(SettingsDialog parent) {
        JLabel label = new JLabel("Use Relative Time For Dates");
        JCheckBox checkBox = new JCheckBox();
        checkBox.addActionListener(_ -> parent.update(s -> s.useRelativeTime = checkBox.isSelected()));
        checkBox.setSelected(Cogfly.settings.useRelativeTime);
        label.setToolTipText("Changes \"Date Created\" and \"Date Updated\" values in mod descriptions to use relative time.");
        add(label, checkBox);
    }
}
