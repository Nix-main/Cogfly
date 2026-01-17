package dev.ambershadow.cogfly.elements.profiles;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.util.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ProfilesScreenElement extends JPanel implements ReloadablePage {

    private final JPanel parentPanel;
    private final JScrollPane pane;
    public ProfilesScreenElement() {
        JPanel upperPanel = new JPanel();
        upperPanel.setPreferredSize(new Dimension(getWidth(), 30));

        JButton launchVanilla = new JButton("Launch Vanilla Game");
        launchVanilla.addActionListener(_ -> Cogfly.launchGameAsync(""));

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
        createProfile.addActionListener(_ -> {
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
            create.setPreferredSize(new Dimension(50, 20));
            create.addActionListener(_ -> {
                prompt.dispose();
                ProfileManager.createProfile(nameField.getText(),
                        button.getText().equals("Click here to select a file") ? "" :
                                button.getText());
                drawProfiles();
            });
            button.addActionListener(_ -> Utils.pickFile((path) -> button.setText(path.toString()), "*", "png", "jpg", "jpeg", "gif"));
            create.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            holder.add(name, BorderLayout.WEST);
            holder.add(nameField, BorderLayout.EAST);
            prompt.add(holder, BorderLayout.NORTH);
            prompt.add(extraHolder, BorderLayout.CENTER);
            prompt.add(create, BorderLayout.SOUTH);
            prompt.setVisible(true);
        });

        upperPanel.add(createProfile);
        upperPanel.add(launchVanilla);
        upperPanel.add(importFromFile);
        upperPanel.add(importFromCode);

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(1150, 600));
        add(upperPanel, BorderLayout.NORTH);
        add(pane, BorderLayout.CENTER);
        drawProfiles();
    }

    public void drawProfiles(){
        parentPanel.removeAll();
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

        for (int i = 1; i <= totalProfiles; i++) {
            Icon icon = UIManager.getIcon("OptionPane.informationIcon");
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
        pane.getVerticalScrollBar().setUnitIncrement(Cogfly.settings.scrollingIncrement);
        drawProfiles();
    }
}
