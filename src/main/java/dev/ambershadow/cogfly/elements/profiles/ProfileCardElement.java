package dev.ambershadow.cogfly.elements.profiles;

import com.formdev.flatlaf.FlatLaf;
import dev.ambershadow.cogfly.elements.SelectedPageButtonElement;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.util.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ProfileCardElement extends JPanel {

    private float hoverProgress = 0f;
    private Timer hoverTimer;
    public static Color normal = UIManager.getColor("Button.background").darker();
    public static Color hover = UIManager.getColor("Button.pressedBackground");
    private LookAndFeel lastLaf = null;
    public ProfileCardElement(Profile profile, Icon icon) {
        setPreferredSize(new Dimension(200, 160));
        setLayout(new BorderLayout(8, 8));
        ProfileOpenPageCardElement panel = new ProfileOpenPageCardElement(profile);
        panel.setName(profile.getName());
        FrameManager.getOrCreate().getPagePanel().add(panel, profile.getName());

        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.DARK_GRAY),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        JLabel iconLabel = new JLabel(icon, JLabel.CENTER);
        JLabel nameLabel = new JLabel(profile.getName(), JLabel.CENTER);
        JButton launchButton = new JButton("Launch");

        launchButton.addActionListener(_ -> {
            List<ModData> outdated = profile.getInstalledMods().stream().filter(mod -> mod.isOutdated(profile)).toList();
            if (!outdated.isEmpty()) {
                List<Object> msg = new ArrayList<>();
                msg.add("This profile has outdated mods.");
                msg.add("");
                for (ModData modData : outdated) {
                    msg.add("• " + modData.getName());
                }
                msg.add("");
                msg.add("Would you like to update them?");
                int result = JOptionPane.showConfirmDialog(
                        FrameManager.getOrCreate().frame,
                        msg.toArray(),
                        "Outdated Mods",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    List<CompletableFuture<Void>> voids = new ArrayList<>();
                    for (ModData modData : outdated) {
                        voids.add(CompletableFuture.runAsync(() -> Utils.downloadLatestMod(
                                ModData.getMod(modData.getFullName()),
                                profile,
                                false
                        )));
                    }
                    CompletableFuture.allOf(voids.toArray(CompletableFuture[]::new)).thenRun(() -> Utils.launchModdedGame(profile)).join();
                    return;
                }
            }
            Utils.launchModdedGame(profile);
        });

        add(iconLabel, BorderLayout.CENTER);
        add(nameLabel, BorderLayout.NORTH);
        add(launchButton, BorderLayout.SOUTH);

        setBackground(normal);
        MouseAdapter mouseHandler = new MouseAdapter() {

            @Override
            public void mouseEntered(MouseEvent e) {
                animateHover(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                animateHover(false);
                for (Component component : getComponents())
                    if (component instanceof JButton button)
                        button.setBorderPainted(false);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isDescendingFrom(
                        e.getComponent(), launchButton)) {
                    return;
                }
                JPanel pages = FrameManager.getOrCreate().getPagePanel();
                panel.reload();
                ((CardLayout)pages.getLayout()).show(pages, profile.getName());
                SelectedPageButtonElement button = FrameManager.getOrCreate().getCurrentPageButton();
                button.setBackground(UIManager.getColor("Button.background"));
                button.selected = false;
            }
        };

        addMouseListener(mouseHandler);

        for (Component c : getComponents()) {
            c.addMouseListener(mouseHandler);
        }

        Timer colorUpdate = new Timer(100, _ -> {
            normal = UIManager.getColor("Button.background").darker();
            hover = UIManager.getColor("Button.pressedBackground");
            if (lastLaf != UIManager.getLookAndFeel()) {
                lastLaf = UIManager.getLookAndFeel();
                setBackground(normal);
            }
            hover = FlatLaf.isLafDark() ? hover.brighter() : hover.darker();
        });
        colorUpdate.start();
    }

    private static Color lerp(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int b2 = (int) (a.getBlue()  + (b.getBlue()  - a.getBlue())  * t);
        return new Color(r, g, b2);
    }

    private void animateHover(boolean hovering) {
        if (hoverTimer != null && hoverTimer.isRunning()) {
            hoverTimer.stop();
        }

        float target = hovering ? 1f : 0f;

        hoverTimer = new Timer(16, e -> {
            float speed = 0.12f;

            if (hoverProgress < target) {
                hoverProgress = Math.min(target, hoverProgress + speed);
            } else {
                hoverProgress = Math.max(target, hoverProgress - speed);
            }

            setBackground(lerp(normal, hover, hoverProgress));
            repaint();

            if (hoverProgress == target) {
                ((Timer) e.getSource()).stop();
            }
        });
        hoverTimer.start();
    }
}