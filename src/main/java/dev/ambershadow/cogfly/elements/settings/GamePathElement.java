package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.util.Utils;

import javax.swing.*;
import java.awt.*;

public class GamePathElement extends JPanel {
    public GamePathElement(SettingsPanelElement parent){

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JLabel label = new JLabel("Game Path: ");
        JButton button = new JButton(Cogfly.settings.gamePath);

        button.addActionListener(_ -> Utils.pickFile(path -> {
            Cogfly.settings.gamePath = path.toFile()
                    .getParentFile().getAbsolutePath();
            button.setText(Cogfly.settings.gamePath);
            parent.onSettingChanged(true);
        }, "exe", "app", "x86_64"));


        panel.add(label, BorderLayout.WEST);
        panel.add(button, BorderLayout.EAST);
        add(panel);
    }
}
