package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.SettingsDialog;

import javax.swing.*;
import java.awt.*;

public class UseRelativeTimeElement extends JPanel {

    public UseRelativeTimeElement(SettingsDialog parent) {
        JLabel label = new JLabel("Use Relative Time For Dates");
        JCheckBox checkBox = new JCheckBox();
        checkBox.addActionListener(_ -> {
            boolean enabled = checkBox.isSelected();
            parent.updateRelativeTime(enabled);
        });
        checkBox.setSelected(Cogfly.settings.useRelativeTime);
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(5, 10, 5, 10);
        add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.anchor = GridBagConstraints.EAST;
        c.fill = GridBagConstraints.NONE;
        add(checkBox, c);
    }
}
