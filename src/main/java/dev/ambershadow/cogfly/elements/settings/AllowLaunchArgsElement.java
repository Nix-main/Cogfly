package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.SettingsDialog;
import dev.ambershadow.cogfly.util.Utils;

import javax.swing.*;

public class AllowLaunchArgsElement extends SettingsElement {

    public AllowLaunchArgsElement(SettingsDialog parent) {
        JLabel label = new JLabel("Allow Launch Args");
        JCheckBox checkBox = new JCheckBox();
        checkBox.addActionListener(_ -> {
            boolean enabled = checkBox.isSelected();
            parent.updateSteamLaunchArgs(enabled);
        });
        checkBox.setSelected(Cogfly.settings.acceptedSteamArgs);
        label.setToolTipText("Whether to allow Cogfly to directly write to the user's Steam Launch Arguments. Irrelevant on Windows, as they aren't necessary for Launch With Steam.");
        add(label, checkBox);
        checkBox.setEnabled(Utils.OperatingSystem.current() != Utils.OperatingSystem.WINDOWS);
    }
}