package dev.ambershadow.cogfly.elements.profiles;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.ModPanelElement;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.util.*;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ProfileOpenPageCardElement extends JPanel {

    private final Profile profile;
    private final JButton updateAll;
    private final JButton remove;
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
            ProfileManager.removeProfile(profile);
            FrameManager.getOrCreate().setPage(
                    FrameManager.CogflyPage.PROFILES,
                    FrameManager.getOrCreate().profilesPageButton
            );
        });
        if (profile.getPath().equals(Paths.get(Cogfly.settings.gamePath))){
            remove.setEnabled(false);
        }

        JButton changeProfileIcon = new JButton("Change Icon");
        changeProfileIcon.addActionListener(_ -> {
            JDialog prompt = new JDialog(FrameManager.getOrCreate().frame);
            prompt.setModal(true);
            prompt.setSize(new Dimension(300, 150));
            prompt.setResizable(false);
            prompt.setLocationRelativeTo(null);
            prompt.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

            JPanel customIconPanel = new JPanel();
            JLabel customIconLabel = new JLabel("Custom Icon:");
            JButton customIconButton = new JButton("Click here to select a file");

            JPanel defaultIconPanel = new JPanel();
            JButton defaultIconButton = new JButton("Reset Icon to Default");

            customIconPanel.add(customIconLabel);
            customIconPanel.add(customIconButton);
            defaultIconPanel.add(defaultIconButton);

            JButton applyButton = new JButton("Apply");
            applyButton.setPreferredSize(new Dimension(50, 20));
            applyButton.addActionListener(_ -> {
                if (customIconButton.getText().equals("Click here to select a file")) {
                    ProfileManager.changeIcon(profile, customIconButton.getText(), true);
                } else {
                    ProfileManager.changeIcon(profile, customIconButton.getText(), false);
                }
                prompt.dispose();
            });
            customIconButton.addActionListener(_ -> Utils.pickFile((path) -> customIconButton.setText(path.toString()), "*", "png", "jpg", "jpeg", "gif"));
            defaultIconButton.addActionListener(_ -> customIconButton.setText("Click here to select a file"));
            applyButton.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            prompt.add(customIconPanel, BorderLayout.CENTER);
            prompt.add(defaultIconPanel, BorderLayout.NORTH);
            prompt.add(applyButton, BorderLayout.SOUTH);
            prompt.setVisible(true);
        });


        upperPanel.add(launch);
        upperPanel.add(updateAll);
        upperPanel.add(copyLogToClipboard);
        upperPanel.add(exportAsId);
        upperPanel.add(exportAsFile);
        upperPanel.add(openFileLocation);
        upperPanel.add(changeProfileIcon);
        upperPanel.add(remove);
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
    }
}
