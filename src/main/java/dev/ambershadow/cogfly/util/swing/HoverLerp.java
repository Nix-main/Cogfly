package dev.ambershadow.cogfly.util.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class HoverLerp {

    private HoverLerp() {}
    public static void install(
            Supplier<Color> normal,
            Supplier<Color> hover,
            JComponent... components
    ) {
        List<JComponent> c = Arrays.stream(components).toList();
        c.forEach(a -> a.setBackground(normal.get()));
        Timer timer = new Timer(16, null);
        float[] progress = { 0f };
        float[] target = {0f};
        Color[] n = {normal.get()};
        Color[] h = {hover.get()};
        timer.addActionListener(_ -> {
            float speed = 0.12f;

            if (progress[0] < target[0])
                progress[0] = Math.min(target[0], progress[0] + speed);
            else
                progress[0] = Math.max(target[0], progress[0] - speed);
            c.forEach(component -> {
                component.setBackground(
                        lerp(n[0], h[0], progress[0])
                );
                component.repaint();
            });
            if (progress == target)
                timer.stop();
        });
        timer.start();
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                n[0] = normal.get();
                h[0] = hover.get();
                target[0] = 1;
                if (!timer.isRunning())
                    timer.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                target[0] = 0;
            }
        };

        c.forEach(component -> {
            component.addMouseListener(adapter);

            for (Component v : component.getComponents()) {
                v.addMouseListener(adapter);
            }
        });

        UIManager.addPropertyChangeListener(e -> {
            if ("lookAndFeel".equals(e.getPropertyName())) {
                n[0] = normal.get();
                h[0] = hover.get();
                SwingUtilities.invokeLater(() -> c.forEach(a -> {
                    a.setBackground(normal.get());
                    a.repaint();
                }));
            }
        });
    }

    private static Color lerp(Color a, Color b, float t) {
        t = Math.clamp(t, 0, 1);
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)
        );
    }
}
