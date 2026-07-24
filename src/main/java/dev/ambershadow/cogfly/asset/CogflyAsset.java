package dev.ambershadow.cogfly.asset;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;

public record CogflyAsset(URL url) {

    public Image getAsImage() {
        return Toolkit.getDefaultToolkit().getImage(url);
    }
    public Icon getAsIcon() {
        return new ImageIcon(url);
    }

    public Icon getAsScaledIcon(float multiplier) {
        ImageIcon icon = new ImageIcon(url);
        Image img = icon.getImage();
        Image scaled = img.getScaledInstance(
                (int)(img.getWidth(icon.getImageObserver())*multiplier), (int)(img.getHeight(icon.getImageObserver())*multiplier), Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    public Icon getAsIconWithColor(Color color) {
        Icon icon = getAsIcon();
        int w = icon.getIconWidth();
        int h = icon.getIconHeight();

        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        icon.paintIcon(null, g, 0, 0);
        g.setComposite(AlphaComposite.SrcAtop);
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();

        return new ImageIcon(image);
    }
}
