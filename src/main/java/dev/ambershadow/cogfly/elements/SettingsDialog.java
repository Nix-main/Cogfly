package dev.ambershadow.cogfly.elements;

import com.formdev.flatlaf.FlatLaf;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.asset.Assets;
import dev.ambershadow.cogfly.elements.profiles.ProfileCardElement;
import dev.ambershadow.cogfly.elements.settings.*;
import dev.ambershadow.cogfly.util.FrameManager;
import dev.ambershadow.cogfly.util.Profile;
import dev.ambershadow.cogfly.util.ProfileManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SettingsDialog extends JDialog {

    public JButton saveButton;
    public SettingsDialog(Frame parent, String name, boolean modal) {
        super(parent, name, modal);
        resetQueue();
        setSize(650, 300);
        setResizable(false);
        JPanel panel = new JPanel(new BorderLayout());
        JPanel holder = new JPanel();
        holder.setLayout(new BoxLayout(holder, BoxLayout.Y_AXIS));
        holder.add(new ThemeListElement(this));
        holder.add(new GamePathElement(this));
        holder.add(new ProfileSavePathPanelElement(this));
        holder.add(new ScrollingIncrementElement(this));
        holder.add(new BaseGameEnabledElement(this));
        holder.add(new AutoNameSpacingElement(this));
        holder.add(new UseRelativeTimeElement(this));
        holder.add(new ProfileSourcesPanelElement(this));

        saveButton = new JButton("Apply & Save");
        saveButton.addActionListener(_ -> {
            applyAndSave();
            resetQueue();
            dispose();
        });
        panel.add(saveButton, BorderLayout.SOUTH);
        panel.add(holder, BorderLayout.NORTH);
        add(panel);

        saveButton.setEnabled(false);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (saveButton.isEnabled()) {
                    String[] options = {"Save & Close", "Don't Save"};

                    int choice = JOptionPane.showOptionDialog(
                            SettingsDialog.this,
                            "You have unsaved settings. Do you want to save them?",
                            "Confirm Exit",
                            JOptionPane.DEFAULT_OPTION,
                            JOptionPane.WARNING_MESSAGE,
                            null,
                            options,
                            options[0]
                    );

                    if (choice == 0) {
                        applyAndSave();
                    }
                    dispose();
                    resetQueue();
                }
            }
        });
    }

    private String initialTheme;
    private String initialGamePath;
    private List<String> initialProfileSources;
    private String initialSavePath;
    private boolean initialAutoNameSpacing;
    private boolean initialBaseGameEnabled;
    private int initialScrollIncrement;
    private boolean initialRelativeTime;
    private String queuedTheme;
    private String queuedGamePath;
    public List<String> queuedProfileSources;
    public String queuedProfileSavePath;
    private boolean queuedNameSpaceBool;
    private boolean queuedBaseGameBool;
    private int queuedScrollIncrement;
    private boolean queuedRelativeTime;

    public void updateTheme(UIManager.LookAndFeelInfo theme){
        queuedTheme = theme.getClassName();
        update();
    }
    public void updateGamePath(String path){
        queuedGamePath = path;
        update();
    }
    public void updateProfileSavePath(String path){
        queuedProfileSavePath = path;
        update();
    }

    public void updateModNameSpacing(boolean spaces){
        queuedNameSpaceBool = spaces;
        update();
    }

    public void updateRelativeTime(boolean relative){
        queuedRelativeTime = relative;
        update();
    }
    public void updateBaseGameModding(boolean allowed){
        queuedBaseGameBool = allowed;
        update();
    }

    public void updateScrollIncrement(int n){
        queuedScrollIncrement = n;
        update();
    }

    public void updateProfileSources(){
        update();
    }

    private void update() {
        boolean dirty =
                !Objects.equals(initialTheme, queuedTheme) ||
                        !Objects.equals(initialGamePath, queuedGamePath) ||
                        !Objects.equals(initialSavePath, queuedProfileSavePath) ||
                        !Objects.equals(initialProfileSources, queuedProfileSources) ||
                        initialAutoNameSpacing != queuedNameSpaceBool ||
                        initialBaseGameEnabled != queuedBaseGameBool ||
                        initialScrollIncrement != queuedScrollIncrement ||
                        initialRelativeTime != queuedRelativeTime;

        saveButton.setEnabled(dirty);
    }


    private void applyAndSave(){
        try {
            UIManager.setLookAndFeel(queuedTheme);
        } catch (ClassNotFoundException | UnsupportedLookAndFeelException | InstantiationException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        SwingUtilities.updateComponentTreeUI(FrameManager.getOrCreate().frame);
        SwingUtilities.updateComponentTreeUI(this);
        ProfileCardElement.normal = UIManager.getColor("Button.background").darker();
        ProfileCardElement.hover = UIManager.getColor("Button.pressedBackground");
        ProfileCardElement.hover = FlatLaf.isLafDark() ? ProfileCardElement.hover.brighter() : ProfileCardElement.hover.darker();
        FrameManager.getOrCreate().getCurrentPageButton().setBackground(ProfileCardElement.hover);

        Cogfly.settings.profileSources = queuedProfileSources;
        Cogfly.settings.modNameSpaces = queuedNameSpaceBool;
        Cogfly.settings.baseGameEnabled = queuedBaseGameBool;
        Cogfly.settings.profileSavePath = queuedProfileSavePath;
        Cogfly.settings.gamePath = queuedGamePath;
        Cogfly.settings.theme = queuedTheme;
        Cogfly.settings.scrollingIncrement = queuedScrollIncrement;
        Cogfly.settings.useRelativeTime = queuedRelativeTime;

        if (!queuedProfileSources.equals(initialProfileSources))
            ProfileManager.loadProfiles();
        ProfileManager.baseGame = new Profile("Base Game", Paths.get(Cogfly.settings.gamePath), Assets.silksongIcon.getAsIcon());
        if (!queuedGamePath.equals(initialGamePath))
            Cogfly.downloadBepInEx(Paths.get(queuedGamePath));

        Cogfly.settings.save();
    }

    private void resetQueue(){
        queuedProfileSources = new ArrayList<>(Cogfly.settings.profileSources);
        queuedNameSpaceBool = Cogfly.settings.modNameSpaces;
        queuedBaseGameBool = Cogfly.settings.baseGameEnabled;
        queuedProfileSavePath = Cogfly.settings.profileSavePath;
        queuedGamePath = Cogfly.settings.gamePath;
        queuedTheme = Cogfly.settings.theme;
        queuedScrollIncrement = Cogfly.settings.scrollingIncrement;
        queuedRelativeTime = Cogfly.settings.useRelativeTime;
        initialProfileSources = new ArrayList<>(Cogfly.settings.profileSources);
        initialAutoNameSpacing = Cogfly.settings.modNameSpaces;
        initialBaseGameEnabled = Cogfly.settings.baseGameEnabled;
        initialSavePath = Cogfly.settings.profileSavePath;
        initialGamePath = Cogfly.settings.gamePath;
        initialTheme = Cogfly.settings.theme;
        initialScrollIncrement = Cogfly.settings.scrollingIncrement;
        initialRelativeTime = Cogfly.settings.useRelativeTime;
    }
}