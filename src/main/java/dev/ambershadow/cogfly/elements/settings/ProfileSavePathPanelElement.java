package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.util.Utils;

import javax.swing.*;
import java.awt.*;

public class ProfileSavePathPanelElement extends JPanel {

    public ProfileSavePathPanelElement(SettingsPanelElement parent) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JLabel label = new JLabel("Profile Save Path: ");
        JButton button = new JButton(Cogfly.settings.profileSavePath);

        button.addActionListener(_ -> Utils.pickFolder(path -> {
            Cogfly.settings.profileSavePath = path.toFile().getAbsolutePath();
            button.setText(Cogfly.settings.profileSavePath);
            parent.onSettingChanged(true);
        }));


        panel.add(label, BorderLayout.WEST);
        panel.add(button, BorderLayout.EAST);
        add(panel);
    }
}
