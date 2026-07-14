package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.SettingsDialog;

import javax.swing.*;

public class ProfileButtonSizeElement extends SettingsElement {
    public ProfileButtonSizeElement(SettingsDialog parent) {
        JLabel label = new JLabel("Profile Button Size");
        JComboBox<Integer> box = new JComboBox<>();
        box.addItem(10);
        box.addItem(15);
        box.addItem(20);
        box.addItem(25);
        box.addItem(30);

        box.setSelectedItem(Cogfly.settings.profileButtonSize);
        box.addActionListener(_ -> {
            @SuppressWarnings("DataFlowIssue") int n = (int)box.getSelectedItem();
            parent.update(s -> s.profileButtonSize = n);
        });
        label.setToolTipText("The size in pixels of the buttons on Cogfly's profile cards.");
        add(label, box);
    }
}
