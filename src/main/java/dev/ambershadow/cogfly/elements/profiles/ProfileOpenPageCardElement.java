package dev.ambershadow.cogfly.elements.profiles;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.ModPanelElement;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.profile.Profile;
import dev.ambershadow.cogfly.profile.ProfileManager;
import dev.ambershadow.cogfly.util.*;
import dev.ambershadow.cogfly.util.swing.FrameManager;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ProfileOpenPageCardElement extends JPanel {

    private final Profile profile;
    private final JButton updateAll;
    private final JProgressBar progressBar;
    public void setBar(boolean val){
        progressBar.setVisible(val);
    }
    public ProfileOpenPageCardElement(Profile profile) {
        super(new BorderLayout());
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
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

        updateAll = new JButton("Update All");
        updateAll.setEnabled(false);
        updateAll.addActionListener(_ -> {
            updateAll.setEnabled(false);
            for (ModData modData : profile.getInstalledMods()) {
                if (!modData.isOutdated(profile)) continue;
                Cogfly.runAsync(() -> ModUtils.downloadLatestMod(
                        ModData.getMod(modData.getFullName()),
                        profile,
                        false
                ));
            }
        });

        JButton copyLogToClipboard = new JButton("Copy Log To Clipboard");
        copyLogToClipboard.addActionListener(_ -> {
            if (Files.exists(profile.getBepInExPath().resolve("LogOutput.log"))){
                Cogfly.copyFile(profile.getBepInExPath().resolve("LogOutput.log"));
            }
        });

        JButton exportAsId = new JButton("Export As Code");
        exportAsId.addActionListener(_ -> {
            String id = ProfileManager.toId(profile);
            Cogfly.copyString(id);
            JOptionPane.showMessageDialog(
                null, 
                "Your code: " + id + " has been copied to your clipboard!", 
                "Copied!",
                JOptionPane.PLAIN_MESSAGE
            );
        });

        JButton exportAsFile = new JButton("Export As File");
        exportAsFile.addActionListener(_ -> FileUtils.pickFolder(path -> ProfileManager.toFile(profile, path)));

        JButton openFileLocation = new JButton("Open Profile Folder");
        openFileLocation.addActionListener(_ -> FileUtils.openProfilePath(profile));

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(_ -> {
            profile.refreshMods();
            ModPanelElement.redraw(profile);
        });

        JButton install = new JButton("Install Manually");
        install.addActionListener(_ -> FileUtils.pickFile((path) -> ModUtils.downloadManualMod(path, profile, true), "*", "zip", "dll"));

        upperPanel.add(launch);
        upperPanel.add(updateAll);
        upperPanel.add(copyLogToClipboard);
        upperPanel.add(exportAsId);
        upperPanel.add(exportAsFile);
        upperPanel.add(openFileLocation);
        upperPanel.add(refresh);
        upperPanel.add(install);

        JPanel centerPanel = new JPanel();
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        centerPanel.add(progressBar);

        add(upperPanel);
        add(Box.createVerticalGlue());
        add(centerPanel);
        add(Box.createVerticalGlue());
        add(new ModPanelElement(profile, this));
    }

    public void reload(){
        boolean anyOutdated = profile.getInstalledMods()
                .stream().anyMatch(mod -> mod.isOutdated(profile));
        updateAll.setEnabled(anyOutdated);
        ModPanelElement.redraw(profile);
        progressBar.setVisible(ModUtils.isDownloading(profile));
    }
}
