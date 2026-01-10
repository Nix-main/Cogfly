package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.SettingsDialog;
import dev.ambershadow.cogfly.util.Utils;

import javax.swing.*;
import java.awt.*;

public class GamePathElement extends JPanel {
    public GamePathElement(SettingsDialog parent){

        JLabel label = new JLabel("Game Path ");
        JButton button = new JButton(Cogfly.settings.gamePath);

        button.addActionListener(_ -> Utils.pickFile(path -> {
            String p = path.toFile()
                    .getParentFile().getAbsolutePath();
            button.setText(p);
            parent.updateGamePath(p);
        }, "Hollow Knight Silksong", "exe", "app", "*"));

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
        add(button, c);
    }
}
