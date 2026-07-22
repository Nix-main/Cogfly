package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.SettingsDialog;
import dev.ambershadow.cogfly.util.FileUtils;

import javax.swing.*;

public class GamePathElement extends SettingsElement {
    public GamePathElement(SettingsDialog parent){

        JLabel label = new JLabel("Game Path ");
        JButton button = new JButton(Cogfly.settings.gamePath);

        button.addActionListener(_ -> FileUtils.pickFile(path -> {
            String p = path.toFile()
                    .getParentFile().getAbsolutePath();
            button.setText(p);
            parent.update(s -> s.gamePath = p);
        }, "Hollow Knight Silksong", "exe", "app", ""));
        add(label, button);
    }
}
