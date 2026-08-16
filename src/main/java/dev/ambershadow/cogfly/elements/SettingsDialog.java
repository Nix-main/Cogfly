package dev.ambershadow.cogfly.elements;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.profiles.ProfileCardElement;
import dev.ambershadow.cogfly.elements.profiles.ProfilesScreenElement;
import dev.ambershadow.cogfly.elements.settings.*;
import dev.ambershadow.cogfly.profile.ProfileManager;
import dev.ambershadow.cogfly.util.GameUtils;
import dev.ambershadow.cogfly.util.Settings;
import dev.ambershadow.cogfly.util.swing.FrameManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class SettingsDialog extends JDialog {

    public final JButton saveButton;

    private Settings initial;
    private Settings queued;

    public SettingsDialog(Frame parent, String name, boolean modal) {
        super(parent, name, modal);
        resetQueue();

        JPanel panel = new JPanel(new BorderLayout());
        JPanel holder = new JPanel();
        holder.setLayout(new BoxLayout(holder, BoxLayout.Y_AXIS));
        holder.add(new ThemeListElement(this));
        holder.add(new GamePathElement(this));
        holder.add(new ProfileSavePathPanelElement(this));
        holder.add(new ScrollingIncrementElement(this));
        holder.add(new ProfileButtonSizeElement(this));
        holder.add(new FontSizeElement(this));
        holder.add(new BaseGameEnabledElement(this));
        holder.add(new AutoNameSpacingElement(this));
        holder.add(new UseRelativeTimeElement(this));
        holder.add(new PerProfileGamePathsElement(this));
        holder.add(new InstalledModsOnTopElement(this));
        holder.add(new LaunchWithSteamElement(this));
        holder.add(new AllowLaunchArgsElement(this));
        holder.add(new ProfileSourcesPanelElement(this));
        holder.add(new ResetToDefaultElement(this));

        saveButton = new JButton("Apply & Save");
        saveButton.addActionListener(_ -> {
            applyAndSave();
            resetQueue();
            dispose();
        });
        panel.add(saveButton, BorderLayout.SOUTH);
        panel.add(holder, BorderLayout.NORTH);
        add(panel);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(parent);
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

    public <T> T get(Function<Settings, T> func) {
        return func.apply(queued);
    }
    public void update(Consumer<Settings> v) {
        v.accept(queued);
        saveButton.setEnabled(!initial.equals(queued));
    }

    public void setAndClose(Settings settings) {
        queued = settings;
        applyAndSave();
        dispose();
        resetQueue();
    }


    private void applyAndSave() {
        try {
            UIManager.setLookAndFeel(queued.theme);
        } catch (ClassNotFoundException | UnsupportedLookAndFeelException | InstantiationException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        FrameManager.getOrCreate().getCurrentPageButton().setBackground(ProfileCardElement.hover.get());
        if (!Objects.equals(queued.profileSavePath, initial.profileSavePath))
            queued.profileSources.add(initial.profileSavePath);
        Cogfly.settings = queued;
        ProfileManager.loadProfiles();
        if (queued.baseGameEnabled)
            GameUtils.downloadBepInEx(Path.of(queued.gamePath));
        ProfilesScreenElement.queueRefresh();
        FrameManager.getOrCreate().getCurrentPage().reload();
        SwingUtilities.invokeLater(ModPanelElement::redrawAll);
        Cogfly.settings.save();
        SwingUtilities.updateComponentTreeUI(FrameManager.getOrCreate().frame);
    }

    private void resetQueue() {
        queued = Settings.load(Cogfly.dataJson);
        initial = Settings.load(Cogfly.dataJson);
    }
}