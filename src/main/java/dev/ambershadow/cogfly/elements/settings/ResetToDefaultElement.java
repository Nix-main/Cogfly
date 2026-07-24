package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.SettingsDialog;
import dev.ambershadow.cogfly.util.Settings;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;

public class ResetToDefaultElement extends SettingsElement {
    public ResetToDefaultElement(SettingsDialog parent) {
        JButton button = new JButton("Reset To Default");
        button.addActionListener(_ -> {
            int a = JOptionPane.showConfirmDialog(parent, "This will reset ALL settings and persistent values, are you sure you want to do this?", "Confirm Reset", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (a == JOptionPane.YES_OPTION) {
                try {
                    Files.delete(Cogfly.dataJson);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                parent.setAndClose(Settings.load(Cogfly.dataJson));
            }
        });
        button.setToolTipText("Reset all settings to their default.");
        button.setPreferredSize(new Dimension(625, button.getPreferredSize().height));
        add(button);
    }
}
