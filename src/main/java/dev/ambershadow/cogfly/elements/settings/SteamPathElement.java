package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.SettingsDialog;
import dev.ambershadow.cogfly.util.FileUtils;

import javax.swing.*;

public class SteamPathElement extends SettingsElement {
    public SteamPathElement(SettingsDialog parent) {

        JLabel label = new JLabel("Steam Path ");
        JButton button = new JButton(Cogfly.settings.steamPath);

        button.addActionListener(_ -> FileUtils.pickFolder(path -> {
            String p = path.toAbsolutePath().toString();
            button.setText(p);
            parent.update(s -> s.steamPath = p);
        }));
        add(label, button);
    }
}
