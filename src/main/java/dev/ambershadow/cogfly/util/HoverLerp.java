package dev.ambershadow.cogfly.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

public  class HoverLerp {

    private HoverLerp() {}
    public static void install(
            JComponent component,
            Supplier<Color> normal,
            Supplier<Color> hover
    ) {
        float[] progress = { 0f };
        Timer[] timer = { null };
        component.setBackground(normal.get());
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                animateTo(1f);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                animateTo(0f);
            }

            private void animateTo(float target) {
                if (timer[0] != null && timer[0].isRunning())
                    timer[0].stop();

                timer[0] = new Timer(16, ev -> {
                    float speed = 0.12f;

                    if (progress[0] < target)
                        progress[0] = Math.min(target, progress[0] + speed);
                    else
                        progress[0] = Math.max(target, progress[0] - speed);

                    component.setBackground(
                            lerp(normal.get(), hover.get(), progress[0])
                    );
                    component.repaint();

                    if (progress[0] == target)
                        ((Timer) ev.getSource()).stop();
                });
                timer[0].start();
            }
        };

        component.addMouseListener(adapter);

        for (Component c : component.getComponents()) {
            c.addMouseListener(adapter);
        }
    }

    private static Color lerp(Color a, Color b, float t) {
        t = Math.clamp(t, 0, 1);
        return new Color(
                a.getRed() + (b.getRed() - a.getRed()) * t,
                a.getGreen() + (b.getGreen() - a.getGreen()) * t,
                a.getBlue() + (b.getBlue() - a.getBlue()) * t
        );
    }
}
