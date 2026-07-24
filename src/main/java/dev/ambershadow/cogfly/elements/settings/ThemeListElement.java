package dev.ambershadow.cogfly.elements.settings;

import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.SettingsDialog;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class ThemeListElement extends SettingsElement {

    public ThemeListElement(SettingsDialog parent) {
        JComboBox<UIManager.LookAndFeelInfo> combo =
                new JComboBox<>(FlatAllIJThemes.INFOS);
        add(new JLabel("Theme "), combo);

        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected,
                                                          boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof UIManager.LookAndFeelInfo info) {
                    setText(info.getName());
                }
                return this;
            }
        });

        combo.addActionListener(_ -> {
            UIManager.LookAndFeelInfo info =
                    (UIManager.LookAndFeelInfo) combo.getSelectedItem();
            if (info == null)
                return;
            if (Objects.equals(info.getClassName(), UIManager.getLookAndFeel().getClass().getName()))
                return;
            parent.update(s -> s.theme = info.getClassName());
            String theme = Cogfly.settings.theme;
            try {
                UIManager.setLookAndFeel(info.getClassName());
                SwingUtilities.updateComponentTreeUI(parent);
                UIManager.setLookAndFeel(theme);
                parent.pack();
            } catch (ClassNotFoundException | UnsupportedLookAndFeelException | InstantiationException |
                     IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });

        LookAndFeel currentLaf = UIManager.getLookAndFeel();
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).getClassName().equals(currentLaf.getClass().getName())) {
                combo.setSelectedIndex(i);
                break;
            }
        }
    }
}