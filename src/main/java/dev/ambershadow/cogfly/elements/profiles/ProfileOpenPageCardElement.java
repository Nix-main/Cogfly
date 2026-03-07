package dev.ambershadow.cogfly.elements.profiles;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.ModPanelElement;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.util.*;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ProfileOpenPageCardElement extends JPanel {

    private final Profile profile;
    private final JButton updateAll;
    private final JButton remove;
    private final JButton setPath;
    public ProfileOpenPageCardElement(Profile profile) {
        super(new BorderLayout());
        this.profile = profile;
        JPanel upperPanel = new JPanel();
        upperPanel.setPreferredSize(new Dimension(getWidth(), 100));

        JButton launch = new JButton("Launch");
        launch.addActionListener(_ -> {
            List<ModData> outdated = profile.getInstalledMods().stream().filter(m -> m.isOutdated(profile)).toList();
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

        updateAll = new JButton("Update All");
        updateAll.setEnabled(false);
        updateAll.addActionListener(_ -> {
            updateAll.setEnabled(false);
            for (ModData modData : profile.getInstalledMods()) {
                if (!modData.isOutdated(profile)) continue;
                CompletableFuture.runAsync(() -> Utils.downloadLatestMod(
                        ModData.getMod(modData.getFullName()),
                        profile,
                        false
                ));
            }
        });

        JButton copyLogToClipboard = new JButton("Copy Log To Clipboard");
        copyLogToClipboard.addActionListener(_ -> {
            if (Files.exists(profile.getBepInExPath().resolve("LogOutput.log"))){
                Utils.copyFile(profile.getBepInExPath().resolve("LogOutput.log"));
            }
        });

        JButton exportAsId = new JButton("Export As Code");
        exportAsId.addActionListener(_ -> {
            String id = ProfileManager.toId(profile);
            Utils.copyString(id);
            JDialog dialog = new JDialog(FrameManager.getOrCreate().frame);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setTitle("Exported as code");
            dialog.setModal(true);
            dialog.setResizable(false);
            dialog.setLocationRelativeTo(null);
            dialog.setSize(500, 100);
            JTextArea textArea = new JTextArea("Your code: " + id + " has been copied to your clipboard!");
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setOpaque(false);
            textArea.setBorder(null);
            textArea.setFocusable(true);
            dialog.add(textArea, BorderLayout.CENTER);
            dialog.setVisible(true);
        });

        JButton exportAsFile = new JButton("Export As File");
        exportAsFile.addActionListener(_ -> Utils.pickFolder(path -> ProfileManager.toFile(profile, path)));

        JButton openFileLocation = new JButton("Open Profile Folder");
        openFileLocation.addActionListener(_ -> Utils.openProfilePath(profile));

        remove = new JButton("Remove Profile");
        remove.addActionListener(_ -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this profile?",
                    "Confirm Profile Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                ProfileManager.removeProfile(profile);
                FrameManager.getOrCreate().setPage(
                        FrameManager.CogflyPage.PROFILES,
                        FrameManager.getOrCreate().profilesPageButton
                );
            }
        });
        if (profile.getPath().equals(Paths.get(Cogfly.settings.gamePath))){
            remove.setEnabled(false);
        }

        JButton changeProfileIcon = new JButton("Change Icon");
        changeProfileIcon.addActionListener(_ -> {
            JDialog prompt = new JDialog(FrameManager.getOrCreate().frame);
            prompt.setModal(true);
            prompt.setSize(new Dimension(300, 100));
            prompt.setResizable(false);
            prompt.setLocationRelativeTo(null);
            prompt.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            prompt.setContentPane(content);
            JButton customIconButton = new JButton("Select a file");
            JButton defaultIconButton = new JButton("Reset Icon to Default");

            customIconButton.addActionListener(_ -> Utils.pickFile((path) -> {
                ProfileManager.changeIcon(profile, path.toString());
                prompt.dispose();
            }, "*", "png", "jpg", "jpeg", "gif"));
            defaultIconButton.addActionListener(_ -> {
                ProfileManager.changeIcon(profile, "");
                prompt.dispose();
            });

            customIconButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            defaultIconButton.setAlignmentX(Component.CENTER_ALIGNMENT);

            content.add(defaultIconButton);
            content.add(Box.createVerticalStrut(5));
            content.add(customIconButton);
            prompt.setVisible(true);
        });

        setPath = new JButton("Set Per-Profile Game Path");
        setPath.addActionListener(_ -> {
            JDialog prompt = new JDialog(FrameManager.getOrCreate().frame);
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

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(_ -> {
            profile.refreshMods();
            ModPanelElement.redraw(profile);
        });

        JButton install = new JButton("Install Manually");
        install.addActionListener(_ -> Utils.pickFile((path) -> {
            try {
                Files.copy(path, profile.getPluginsPath().resolve(path.getFileName()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            profile.refreshMods();
            ModPanelElement.redraw(profile);
        }, "*", "dll"));

        upperPanel.add(launch);
        upperPanel.add(updateAll);
        upperPanel.add(copyLogToClipboard);
        upperPanel.add(exportAsId);
        upperPanel.add(exportAsFile);
        upperPanel.add(openFileLocation);
        upperPanel.add(changeProfileIcon);
        upperPanel.add(setPath);
        upperPanel.add(remove);
        upperPanel.add(refresh);
        upperPanel.add(install);
        add(upperPanel, BorderLayout.NORTH);
        add(new ModPanelElement(profile), BorderLayout.CENTER);
    }

    public void reload(){
        boolean anyOutdated = profile.getInstalledMods()
                .stream().anyMatch(mod -> mod.isOutdated(profile));
        updateAll.setEnabled(anyOutdated);
        ModPanelElement.redraw(profile);
        if (profile.getPath().equals(Paths.get(Cogfly.settings.gamePath))){
            remove.setEnabled(false);
        }
        setPath.setVisible(Cogfly.settings.profileSpecificPaths);
    }
}
