package dev.ambershadow.cogfly.elements.profiles;

import com.formdev.flatlaf.FlatLaf;
import com.kitfox.svg.app.beans.SVGIcon;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.asset.Assets;
import dev.ambershadow.cogfly.asset.CogflyAsset;
import dev.ambershadow.cogfly.elements.SelectedPageButtonElement;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.profile.Profile;
import dev.ambershadow.cogfly.profile.ProfileManager;
import dev.ambershadow.cogfly.util.*;
import dev.ambershadow.cogfly.util.swing.FrameManager;
import dev.ambershadow.cogfly.util.swing.HoverLerp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ProfileCardElement extends JPanel {

    public static final Supplier<Color> normal = () -> UIManager.getColor("Button.background").darker();
    public static final Supplier<Color> hover = () -> FlatLaf.isLafDark() ? UIManager.getColor("Button.pressedBackground").brighter() : UIManager.getColor("Button.pressedBackground").darker();

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

        setBackground(normal.get());
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
                pages.add(panel, profile.getName());
                panel.reload();

                CardLayout layout = (CardLayout) pages.getLayout();
                layout.show(pages, profile.getName());

                SelectedPageButtonElement button = FrameManager.getOrCreate().getCurrentPageButton();

                if (button != null) {
                    button.setBackground(UIManager.getColor("Button.background"));
                    button.selected = false;
                }

                pages.revalidate();
                pages.repaint();
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
        HoverLerp.install(normal, hover, this, south, buttonPanel);
    }

    private void updateIcons() {
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

    private void createButtons() {
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
                        voids.add(Cogfly.runAsync(() -> ModUtils.downloadLatestMod(
                                ModData.getMod(modData.getFullName()),
                                profile,
                                false
                        )));
                    }
                    CompletableFuture.allOf(voids.toArray(CompletableFuture[]::new)).thenRun(() -> GameUtils.launchModdedGame(profile)).join();
                    return;
                }
            }
            GameUtils.launchModdedGame(profile);
        });

        edit = new JButton();
        edit.addActionListener(_ -> {
            JDialog prompt = new JDialog(FrameManager.getOrCreate().frame);
            prompt.setTitle("Edit Profile - " + profile.getName());
            prompt.setModal(true);
            prompt.setResizable(false);
            prompt.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            JLabel name = new JLabel("Name: ");
            JTextField nameField = new JTextField(profile.getName());
            JLabel icon = new JLabel("Icon (optional): ");
            JButton button = new JButton("Click here to select a file");
            if (profile.getIconPath() != null)
                button.setText(profile.getIconPath().toString());
            JButton create = new JButton("Update");

            nameField.getDocument().addDocumentListener(new ProfilesScreenElement.NameDocumentListener(nameField, create));
            button.addActionListener(_ -> FileUtils.pickFile((path) -> button.setText(path.toString()), "*", "png", "jpg", "jpeg", "gif"));

            JLabel pth = new JLabel("Path: ");
            JButton btn = new JButton(profile.getGamePath());
            nameField.getDocument().addDocumentListener(new ProfilesScreenElement.NameDocumentListener(nameField, create));
            button.addActionListener(_ -> FileUtils.pickFile((path) -> btn.setText(path.toString()), "Hollow Knight Silksong", "png", "jpg", "jpeg", "gif"));

            create.addActionListener(_ -> {
                if ((profile.getIconPath() == null || !button.getText().equals(profile.getIconPath().toString()))
                && !button.getText().equals("Click here to select a file")) {
                    ProfileManager.changeIcon(profile, button.getText());
                }
                if (!btn.getText().equals(profile.getGamePath())) {
                    profile.setGamePath(btn.getText());
                }
                if (btn.getText().equals(Cogfly.settings.gamePath)) {
                    profile.resetGamePath();
                }
                if (!nameField.getText().equals(profile.getName())) {
                    try {
                        Files.move(profile.getPath(), Path.of(Cogfly.settings.profileSavePath).resolve(nameField.getText()), StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    ProfileManager.loadProfiles();
                }
                ProfilesScreenElement.queueRefresh();
                FrameManager.getOrCreate().getCurrentPage().reload();
                prompt.dispose();
            });
            create.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            create.setMaximumSize(new Dimension(Integer.MAX_VALUE, create.getPreferredSize().height));
            create.setAlignmentX(Component.LEFT_ALIGNMENT);

            // lowkey this jpanel chain sucks but nothing else was working lol
            JPanel e = new JPanel(new BorderLayout());
            JPanel main = new JPanel();
            
            main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
            main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel holder = new JPanel(new BorderLayout(5, 5));
            holder.add(name, BorderLayout.WEST);
            holder.add(nameField, BorderLayout.CENTER);

            JPanel extraHolder = new JPanel(new BorderLayout(5, 5));
            extraHolder.add(icon, BorderLayout.WEST);
            extraHolder.add(button, BorderLayout.CENTER);

            main.add(holder);
            main.add(Box.createVerticalStrut(8));
            main.add(extraHolder);
            if (Cogfly.settings.profileSpecificPaths) {
                JPanel holder3 = new JPanel(new BorderLayout(5, 5));
                holder3.add(pth, BorderLayout.WEST);
                holder3.add(btn, BorderLayout.CENTER);
                main.add(Box.createVerticalStrut(8));
                main.add(holder3);
            }

            JPanel content = new JPanel(new BorderLayout());
            content.add(main, BorderLayout.CENTER);
            content.add(create, BorderLayout.SOUTH);

            prompt.setContentPane(content);
            prompt.pack();
            prompt.setLocationRelativeTo(FrameManager.getOrCreate().frame);
            prompt.setVisible(true);
        });

        copy = new JButton();
        copy.addActionListener(_ -> ProfilesScreenElement.createProfilePrompt((name, icn) -> Cogfly.runAsync(() -> {
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
                Cogfly.throwNonFatalError(e);
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
        if (profile.getPath().equals(Paths.get(Cogfly.settings.gamePath))) {
            remove.setEnabled(false);
            edit.setEnabled(false);
            copy.setEnabled(false);
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
        setBackground(normal.get());
        south.setBackground(normal.get());
        buttonPanel.setBackground(normal.get());
        updateIcons();
        repaint();
    }
}