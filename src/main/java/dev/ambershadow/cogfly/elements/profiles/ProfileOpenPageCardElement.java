package dev.ambershadow.cogfly.elements.profiles;

import dev.ambershadow.cogfly.asset.Assets;
import dev.ambershadow.cogfly.elements.ModPanelElement;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.util.*;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;

public class ProfileOpenPageCardElement extends JPanel {

    private final Profile profile;
    private final JButton updateAll;
    public ProfileOpenPageCardElement(Profile profile) {
        super(new BorderLayout());
        this.profile = profile;
        JPanel upperPanel = new JPanel();
        upperPanel.setPreferredSize(new Dimension(getWidth(), 100));

        JButton launch = new JButton("Launch");
        launch.addActionListener(_ -> Utils.launchModdedGame(ProfileManager.getCurrentProfile()));

        updateAll = new JButton("Update All");
        updateAll.setEnabled(false);
        updateAll.addActionListener(_ -> profile.getInstalledMods().stream().filter(ModData::isOutdated)
                .forEach(mod -> Utils.downloadMod(mod, profile)));

        JButton copyLogToClipboard = new JButton("Copy Log To Clipboard");
        copyLogToClipboard.addActionListener(_ -> {
            if (Files.exists(profile.getBepInExPath().resolve("LogOutput.log"))){
                Utils.copyFile(profile.getBepInExPath().resolve("LogOutput.log"));
            }
        });
        copyLogToClipboard.setIcon(Assets.copy.getAsIcon());

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

        upperPanel.add(launch);
        upperPanel.add(updateAll);
        upperPanel.add(copyLogToClipboard);
        upperPanel.add(exportAsId);
        upperPanel.add(exportAsFile);
        upperPanel.add(openFileLocation);
        add(upperPanel, BorderLayout.NORTH);
        add(new ModPanelElement(), BorderLayout.CENTER);
    }

    public void reload(){
        boolean anyOutdated = profile.getInstalledMods()
                .stream().anyMatch(ModData::isOutdated);
        updateAll.setEnabled(anyOutdated);
    }
}
