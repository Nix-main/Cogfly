package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.SettingsDialog;

import javax.swing.*;

public class ScrollingIncrementElement extends SettingsElement {
    public ScrollingIncrementElement(SettingsDialog parent) {
        JLabel label = new JLabel("Scrolling Increment");
        JComboBox<Integer> box = new JComboBox<>();
        box.addItem(8);
        box.addItem(16);
        box.addItem(24);
        box.addItem(32);
        box.addItem(64);

        box.setSelectedItem(Cogfly.settings.scrollingIncrement);
        box.addActionListener(_ -> {
            @SuppressWarnings("DataFlowIssue") int n = (int)box.getSelectedItem();
            parent.updateScrollIncrement(n);
        });
        label.setToolTipText("The amount of pixels that one notch on your scroll wheel moves the bar down. Alternatively, scroll speed.");
        add(label, box);
    }
}
