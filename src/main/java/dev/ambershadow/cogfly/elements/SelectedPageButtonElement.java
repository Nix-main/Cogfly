package dev.ambershadow.cogfly.elements;

import javax.swing.*;
import java.awt.*;

public class SelectedPageButtonElement extends JButton {

    public boolean selected;
    public SelectedPageButtonElement(String name) {
        super(name);
    }

    @Override
    protected void paintBorder(Graphics g) {
        if (selected || isSelected())
            setBorderPainted(true);

    }
}
