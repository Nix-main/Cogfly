package dev.ambershadow.cogfly.elements.profiles;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.util.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class ProfilesScreenElement extends JPanel implements ReloadablePage {

    public static final Icon icon = UIManager.getIcon("OptionPane.informationIcon");
    public static final BiConsumer<String, String> defaultCallback = (name, path) -> {
        ProfileManager.createProfile(name,
                path.equals("Click here to select a file") ? "" : path);
        FrameManager.getOrCreate().getCurrentPage().reload();
    };
    private static boolean refreshQueued = false;
    public static void queueRefresh(){
        refreshQueued = true;
    }
    public static void createPrompt(BiConsumer<String, String> consumer, Runnable extra){
        JDialog prompt = new JDialog(FrameManager.getOrCreate().frame);
        prompt.setModal(true);
        prompt.setSize(new Dimension(300, 150));
        prompt.setResizable(false);
        prompt.setLocationRelativeTo(null);
        prompt.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel holder = new JPanel();
        JLabel name = new JLabel("Name: ");
        JTextField nameField = new JTextField("");
        JLabel icon = new JLabel("Icon (optional): ");
        JButton button = new JButton("Click here to select a file");
        JPanel extraHolder = new JPanel();
        extraHolder.add(icon);
        extraHolder.add(button);

        JButton create = new JButton("Create");
        create.setEnabled(false);
        create.setPreferredSize(new Dimension(50, 20));
        create.addActionListener(_ -> {
            prompt.dispose();
            consumer.accept(nameField.getText(), button.getText());
            extra.run();
        });

        nameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateValidity();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateValidity();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {}

            private void updateValidity(){
                boolean valid = nameField.getText().matches("\\w+");
                create.setEnabled(valid);
                nameField.setToolTipText("Profile names can only contain letters, numbers, and underscores.");
                forceTooltip(valid);
                if (!valid)
                    return;
                boolean exists = Files.exists(Paths.get(Cogfly.settings.profileSavePath).resolve(nameField.getText()));
                create.setEnabled(!exists);
                nameField.setToolTipText("A profile with this name already exists in the profile save folder.");
                forceTooltip(exists);
            }

            private void forceTooltip(boolean show){
                if (show){
                    ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                    toolTipManager.setInitialDelay(0);
                    MouseEvent phantomEvent = new MouseEvent(
                            nameField,
                            MouseEvent.MOUSE_MOVED,
                            System.currentTimeMillis(),
                            0,
                            nameField.getWidth() / 2,
                            nameField.getHeight() / 2,
                            0,
                            false
                    );
                    toolTipManager.mouseMoved(phantomEvent);
                } else {
                    ToolTipManager ttm = ToolTipManager.sharedInstance();

                    MouseEvent exitEvent = new MouseEvent(
                            nameField,
                            MouseEvent.MOUSE_EXITED,
                            System.currentTimeMillis(),
                            0,
                            -1,
                            -1,
                            0,
                            false
                    );

                    ttm.mouseExited(exitEvent);
                }
            }
        });

        button.addActionListener(_ -> Utils.pickFile((path) -> button.setText(path.toString()), "*", "png", "jpg", "jpeg", "gif"));
        create.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        holder.add(name, BorderLayout.WEST);
        holder.add(nameField, BorderLayout.EAST);
        prompt.add(holder, BorderLayout.NORTH);
        prompt.add(extraHolder, BorderLayout.CENTER);
        prompt.add(create, BorderLayout.SOUTH);
        prompt.setVisible(true);
    }
    private final JPanel parentPanel;
    private final JScrollPane pane;
    public ProfilesScreenElement() {
        JPanel upperPanel = new JPanel();
        upperPanel.setPreferredSize(new Dimension(getWidth(), 30));

        JButton launchVanilla = new JButton("Launch Vanilla Game");
        launchVanilla.addActionListener(_ -> Cogfly.launchGameAsync(false, "", Cogfly.settings.gamePath));

        JButton importFromFile = new JButton("Import From File");
        importFromFile.addActionListener(_ -> Utils.pickFile((path) -> ProfileManager.fromFile(path, (profile, outdated) -> {
            if (outdated.length > 0) {
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
                    CompletableFuture.allOf(voids.toArray(CompletableFuture[]::new)).join();
                }
            }
            drawProfiles();
        }), "*", "r2z"));

        JButton importFromCode = new JButton("Import From Code");
        importFromCode.addActionListener(_ -> {
            String input = JOptionPane.showInputDialog("Enter Profile Code");
            ProfileManager.fromId(input, (profile, outdated) -> {
                if (outdated.length > 0){
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
                        CompletableFuture.allOf(voids.toArray(CompletableFuture[]::new)).join();
                    }
                }
                drawProfiles();
            });
        });

        parentPanel = new JPanel();

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapper.add(parentPanel);

        pane = new JScrollPane(wrapper, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);


        JButton createProfile = new JButton("Create Profile");
        createProfile.addActionListener(_ -> createPrompt(defaultCallback, () -> {}));

        JButton createShortcut = new JButton("Create Shortcut");
        createShortcut.addActionListener(_ -> {
            JDialog dialog = new JDialog(FrameManager.getOrCreate().frame, "Create Shortcut", true);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setLocationRelativeTo(null);
            List<Profile> profiles = new ArrayList<>(ProfileManager.profiles);
            JComboBox<String> profileComboBox = new JComboBox<>();
            profiles.forEach(profile -> profileComboBox.addItem(profile.getName() + " (" + profile.getPath() + ")"));
            String def = switch(Utils.OperatingSystem.current()) {
                case WINDOWS -> FileSystemView.getFileSystemView().getHomeDirectory().getAbsolutePath();
                case MAC -> Paths.get(System.getProperty("user.home"), "Desktop").toAbsolutePath().toString();
                case LINUX -> Paths.get(System.getProperty("user.home"), ".local/share/applications").toAbsolutePath().toString();
                case OTHER -> "";
            };
            JButton path = new JButton(def);
            path.addActionListener(_ -> Utils.pickFolder((folder) ->
                path.setText(folder.toAbsolutePath().toString())));
            JButton create = new JButton("Create");
            create.addActionListener(_ -> {
                dialog.dispose();
                Profile profile = profiles.get(profileComboBox.getSelectedIndex());
                Path loc = Path.of(path.getText());
                try {
                    switch (Utils.OperatingSystem.current()){
                        case WINDOWS -> {
                            String cmd =
                                    String.format("$s=(New-Object -COM WScript.Shell).CreateShortcut('%s');" +
                                            "$s.TargetPath='explorer.exe';" +
                                            "$s.Arguments='cogfly://launch/%s';" +
                                            "$s.IconLocation='%s,0';" +
                                            "$s.Save()",
                                            loc.resolve(profile.getName() + ".lnk").toAbsolutePath(),
                                            profile.getName(),
                                            Cogfly.localDataPath.resolve("icon.ico").toAbsolutePath());

                                new ProcessBuilder(
                                        "powershell.exe",
                                        "-Command",
                                        cmd
                                ).start();
                        }
                        case LINUX -> {
                            try(InputStream entry = Cogfly.getResource("/Profile.desktop").openStream()) {
                                String desktop = new String(
                                        entry.readAllBytes(),
                                        StandardCharsets.UTF_8
                                );
                                desktop = desktop.replace("PROFILE_NAME", profile.getName());
                                desktop = desktop.replace("ICON_PATH", Cogfly.localDataPath.resolve("icon.png").toAbsolutePath().toString());
                                Path file = loc.resolve(profile.getName() + ".desktop");
                                Files.writeString(file, desktop);
                                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
                                perms.add(PosixFilePermission.OWNER_EXECUTE);
                                perms.add(PosixFilePermission.GROUP_EXECUTE);
                                perms.add(PosixFilePermission.OTHERS_EXECUTE);
                                Files.setPosixFilePermissions(file, perms);
                            }
                        }
                        case MAC -> {
                            Path file = loc.resolve(profile.getName() + ".app");
                            ProcessBuilder builder = new ProcessBuilder(
                                    "osacompile", "-o", file.toString());
                            builder.redirectErrorStream(true);
                            Process process = builder.start();
                            try (OutputStream out = process.getOutputStream()) {
                                out.write(("do shell script \"open cogfly://launch/" + profile.getName() + "\"").getBytes(StandardCharsets.UTF_8));
                                process.waitFor();
                                Files.copy(Cogfly.localDataPath.resolve("icon.icns"), file.resolve("Contents/Resources/applet.icns"), StandardCopyOption.REPLACE_EXISTING);
                            }
                            String output;
                            try (InputStream in = process.getInputStream()) {
                                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                            }
                            int exit = process.waitFor();
                            if (exit != 0)
                                throw new RuntimeException("osacompile failed with exit code " + exit + ": " + output);
                            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
                            perms.add(PosixFilePermission.OWNER_EXECUTE);
                            perms.add(PosixFilePermission.GROUP_EXECUTE);
                            perms.add(PosixFilePermission.OTHERS_EXECUTE);
                            Files.setPosixFilePermissions(file, perms);
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            JPanel panel = new JPanel();
            panel.add(new JLabel("Profile: "), BorderLayout.WEST);
            panel.add(profileComboBox, BorderLayout.EAST);
            JPanel pathPanel = new JPanel();
            pathPanel.add(new JLabel("Path: "), BorderLayout.WEST);
            pathPanel.add(path, BorderLayout.EAST);
            dialog.add(panel, BorderLayout.NORTH);
            dialog.add(pathPanel, BorderLayout.CENTER);
            dialog.add(create, BorderLayout.SOUTH);
            dialog.pack();
            dialog.setVisible(true);
        });

        upperPanel.add(createProfile);
        upperPanel.add(launchVanilla);
        upperPanel.add(importFromFile);
        upperPanel.add(importFromCode);
        upperPanel.add(createShortcut);

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(1150, 600));
        add(upperPanel, BorderLayout.NORTH);
        add(pane, BorderLayout.CENTER);
        drawProfiles();
    }

    public void drawProfiles(){
        if (!Cogfly.createdProfiles)
            return;
        int maxPerRow = 5;
        List<Profile> profiles = new ArrayList<>();
        if (Cogfly.settings.baseGameEnabled) {
            profiles.add(ProfileManager.baseGame);
        }
        profiles.addAll(ProfileManager.profiles);
        int totalProfiles = profiles.size();
        parentPanel.setLayout(new BoxLayout(parentPanel, BoxLayout.Y_AXIS));
        parentPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel rowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 16));
        parentPanel.removeAll();
        for (int i = 1; i <= totalProfiles; i++) {
            Icon icon = ProfilesScreenElement.icon;
            if (profiles.get(i-1).getIcon() != null)
                icon = profiles.get(i-1).getIcon();
            rowPanel.add(new ProfileCardElement(profiles.get(i-1), icon));

            if (i % maxPerRow == 0) {
                parentPanel.add(rowPanel);
                rowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 16));
            }
        }
        if (rowPanel.getComponentCount() > 0) {
            parentPanel.add(rowPanel);
        }
        revalidate();
        repaint();
    }

    @Override
    public void reload() {
        if (ProfileManager.profiles.stream().anyMatch(profile -> !Files.exists(profile.getPath()))) {
            ProfileManager.loadProfiles();
            JOptionPane.showMessageDialog(FrameManager.getOrCreate().frame, "One of your profiles wasn't found on the system, so they're being reloaded. If you changed or deleted a profile in your file system while Cogfly is open, note that this is dangerous behavior.");
            refreshQueued = true;
        }
        pane.getVerticalScrollBar().setUnitIncrement(Cogfly.settings.scrollingIncrement);
        if (refreshQueued) {
            Cogfly.logger.info("Redrawing profiles...");
            drawProfiles();
            refreshQueued = false;
        }
        // speeds things up a ton to only refresh when needed
    }
}
