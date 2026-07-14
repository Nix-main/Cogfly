package dev.ambershadow.cogfly.elements.profiles;

import com.formdev.flatlaf.FlatLaf;
import com.kitfox.svg.app.beans.SVGIcon;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.asset.Assets;
import dev.ambershadow.cogfly.asset.CogflyAsset;
import dev.ambershadow.cogfly.elements.SelectedPageButtonElement;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.util.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class ProfileCardElement extends JPanel {

    public static Color normal = UIManager.getColor("Button.background").darker();
    public static Color hover = UIManager.getColor("Button.pressedBackground");

    private JPanel buttonPanel;
    private JPanel south;
    private JButton launch;
    private JButton edit;
    private JButton copy;
    private JButton remove;
    private final Profile profile;
    public ProfileCardElement(Profile profile, Icon icon) {
        setPreferredSize(new Dimension(200, 160));
        setLayout(new BorderLayout(8, 8));
        ProfileOpenPageCardElement panel = new ProfileOpenPageCardElement(profile);
        panel.setName(profile.getName());
        FrameManager.getOrCreate().getPagePanel().add(panel, profile.getName());
        this.profile = profile;
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.DARK_GRAY),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        JLabel iconLabel = new JLabel(icon, JLabel.CENTER);
        JLabel nameLabel = new JLabel(profile.getName(), JLabel.CENTER);
        add(iconLabel, BorderLayout.CENTER);
        add(nameLabel, BorderLayout.NORTH);
        createButtons();

        setBackground(normal);
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isDescendingFrom(
                        e.getComponent(), buttonPanel)) {
                    return;
                }
                JPanel pages = FrameManager.getOrCreate().getPagePanel();
                ProfileOpenPageCardElement panel = new ProfileOpenPageCardElement(profile);
                panel.setName(profile.getName());
                FrameManager.getOrCreate().getPagePanel().add(panel, profile.getName());
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

        updateColors();

        UIManager.addPropertyChangeListener(e -> {
            if ("lookAndFeel".equals(e.getPropertyName())) {
                SwingUtilities.invokeLater(this::updateColors);
            }
        });
        HoverLerp.install(() -> normal, () -> hover, this, south, buttonPanel);
    }

    private void updateIcons(){
        CogflyAsset[] profileIcons = Assets.getProfileIcons();
        SVGIcon[] icons = new SVGIcon[profileIcons.length];
        for (int i = 0; i < profileIcons.length; i++) {
            SVGIcon svg = new SVGIcon();
            try {
                svg.setSvgURI(profileIcons[i].url().toURI());
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            svg.setPreferredSize(new Dimension(Cogfly.settings.profileButtonSize, 0));
            svg.setAutosize(SVGIcon.AUTOSIZE_HORIZ);
            svg.setAntiAlias(true);
            icons[i] = svg;
        }
        launch.setIcon(icons[0]);
        edit.setIcon(icons[1]);
        copy.setIcon(icons[2]);
        remove.setIcon(icons[3]);
    }

    private void createButtons(){
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        launch = new JButton();
        launch.addActionListener(_ -> {
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

        edit = new JButton();
        edit.addActionListener(_ -> {
            JDialog dialog = new JDialog();
            dialog.setTitle("Edit Profile - " + profile.getName());
            dialog.setModal(true);
            dialog.setAlwaysOnTop(true);
            dialog.setLocationRelativeTo(null);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            JPanel holder = new JPanel();
            holder.setLayout(new BoxLayout(holder, BoxLayout.Y_AXIS));

            if (Cogfly.settings.profileSpecificPaths) {
                JButton setPath = new JButton("Set Custom Game Path");
                setPath.addActionListener(_ -> {
                    JDialog prompt = new JDialog(dialog);
                    prompt.setModal(true);
                    prompt.setSize(new Dimension(500, 125));
                    prompt.setResizable(false);
                    prompt.setLocationRelativeTo(null);
                    prompt.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                    JPanel content = new JPanel();
                    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
                    prompt.setContentPane(content);
                    JLabel current = new JLabel(profile.getGamePath());
                    JButton customPathButton = new JButton("Select Game Path");
                    JButton resetPathButton = new JButton("Reset Path");

                    customPathButton.addActionListener(_ -> Utils.pickFile((path) -> {
                        profile.setGamePath(path.toFile().getParentFile().getAbsolutePath());
                        prompt.dispose();
                    }, "Hollow Knight Silksong", "exe", "app", ""));
                    resetPathButton.addActionListener(_ -> {
                        profile.resetGamePath();
                        prompt.dispose();
                    });

                    customPathButton.setAlignmentX(Component.CENTER_ALIGNMENT);
                    resetPathButton.setAlignmentX(Component.CENTER_ALIGNMENT);
                    current.setAlignmentX(Component.CENTER_ALIGNMENT);

                    content.add(current);
                    content.add(Box.createVerticalStrut(5));
                    content.add(customPathButton);
                    content.add(Box.createVerticalStrut(5));
                    content.add(resetPathButton);
                    prompt.setVisible(true);
                });
                setPath.setAlignmentX(Component.CENTER_ALIGNMENT);
                holder.add(setPath);
            }
            JButton changeProfileIcon = new JButton("Change Icon");
            changeProfileIcon.addActionListener(_ -> {
                JDialog prompt = new JDialog(dialog);
                prompt.setModal(true);
                prompt.setSize(new Dimension(300, 100));
                prompt.setResizable(false);
                prompt.setLocationRelativeTo(null);
                prompt.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                JPanel content = new JPanel();
                prompt.setContentPane(content);
                JButton customIconButton = new JButton("Select a file");
                JButton defaultIconButton = new JButton("Reset Icon to Default");

                customIconButton.addActionListener(_ -> Utils.pickFile((path) -> {
                    ProfileManager.changeIcon(profile, path.toString());
                    prompt.dispose();
                    ProfilesScreenElement.queueRefresh();
                }, "*", "png", "jpg", "jpeg", "gif"));
                defaultIconButton.addActionListener(_ -> {
                    ProfileManager.changeIcon(profile, "");
                    prompt.dispose();
                    ProfilesScreenElement.queueRefresh();
                });

                customIconButton.setAlignmentX(Component.CENTER_ALIGNMENT);
                defaultIconButton.setAlignmentX(Component.CENTER_ALIGNMENT);

                content.add(defaultIconButton, BorderLayout.NORTH);
                content.add(Box.createVerticalStrut(5));
                content.add(customIconButton, BorderLayout.CENTER);
                prompt.setVisible(true);
            });
            changeProfileIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
            holder.add(Box.createVerticalStrut(5));
            holder.add(changeProfileIcon);
            dialog.setContentPane(holder);
            dialog.pack();
            dialog.setVisible(true);
        });

        copy = new JButton();
        copy.addActionListener(_ -> ProfilesScreenElement.createPrompt((name, icn) -> CompletableFuture.runAsync(() -> {
            try (Stream<Path> files = Files.walk(profile.getPath())) {
                Files.createDirectory(Path.of(Cogfly.settings.profileSavePath).resolve(name));
                Path source = Paths.get(icn);
                try {
                    Files.copy(source, Path.of(Cogfly.settings.profileSavePath).resolve(name).resolve("icon." + source.getFileName().toString()
                            .split("\\.")[1]));
                } catch (ArrayIndexOutOfBoundsException ignored) {}
                for (Path path : files.toList()) {
                    Path targetPath = Path.of(Cogfly.settings.profileSavePath).resolve(name).resolve(profile.getPath().relativize(path));

                    if (Files.isDirectory(path)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }

                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((_, e) -> {
            if (e != null) {
                Cogfly.logger.error("", e);
                Utils.throwNonFatalError(e);
            }
            ProfileManager.loadProfiles();
            ProfilesScreenElement.queueRefresh();
            FrameManager.getOrCreate().getCurrentPage().reload();
        }), () -> {}));

        remove = new JButton();
        remove.addActionListener(_ -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this profile? This will delete this folder: " + profile.getPath(),
                    "Confirm Profile Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                ProfileManager.removeProfile(profile);
                FrameManager.getOrCreate().setPage(
                        FrameManager.CogflyPage.PROFILES,
                        FrameManager.getOrCreate().profilesPageButton
                );
                ProfilesScreenElement.queueRefresh();
            }
        });
        if (profile.getPath().equals(Paths.get(Cogfly.settings.gamePath))){
            remove.setEnabled(false);
        }
        buttonPanel.add(launch);
        buttonPanel.add(edit);
        buttonPanel.add(copy);
        buttonPanel.add(remove);
        south = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        south.add(buttonPanel);
        updateIcons();
        add(south, BorderLayout.SOUTH);
    }

    void updateColors() {
        normal = UIManager.getColor("Button.background").darker();
        Color base = UIManager.getColor("Button.pressedBackground");
        hover = FlatLaf.isLafDark() ? base.brighter() : base.darker();
        setBackground(normal);
        south.setBackground(normal);
        buttonPanel.setBackground(normal);
        updateIcons();
        repaint();
    }
}