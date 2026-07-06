package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.SettingsDialog;

import javax.swing.*;

public class LaunchWithSteamElement extends SettingsElement {

    public LaunchWithSteamElement(SettingsDialog parent) {
        JLabel label = new JLabel("Launch With Steam");
        JCheckBox checkBox = new JCheckBox();
        checkBox.addActionListener(_ -> {
            boolean enabled = checkBox.isSelected();
            parent.updateLaunchWithSteam(enabled);
        });
        checkBox.setSelected(Cogfly.settings.launchWithSteam);
        label.setToolTipText("Allows the Steam Client to own the process. Enables controller compatability and playtime tracking but requires confirming in Steam each launch. A similar affect can be achieved by pressing \"Launch\" with Steam open. Does nothing if not using steam.");
        add(label, checkBox);
    }
}
