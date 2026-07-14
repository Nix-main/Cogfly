package dev.ambershadow.cogfly.elements;

import com.formdev.flatlaf.FlatLaf;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.profiles.ProfileCardElement;
import dev.ambershadow.cogfly.elements.settings.*;
import dev.ambershadow.cogfly.util.FrameManager;
import dev.ambershadow.cogfly.util.ProfileManager;
import dev.ambershadow.cogfly.util.Settings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

public class SettingsDialog extends JDialog {

    public final JButton saveButton;

    private Settings initial;
    private Settings queued;

    public SettingsDialog(Frame parent, String name, boolean modal) {
        super(parent, name, modal);
        resetQueue();
        setResizable(false);
        JPanel panel = new JPanel(new BorderLayout());
        JPanel holder = new JPanel();
        holder.setLayout(new BoxLayout(holder, BoxLayout.Y_AXIS));
        holder.add(new ThemeListElement(this));
        holder.add(new GamePathElement(this));
        holder.add(new ProfileSavePathPanelElement(this));
        holder.add(new ScrollingIncrementElement(this));
        holder.add(new ProfileButtonSizeElement(this));
        holder.add(new BaseGameEnabledElement(this));
        holder.add(new AutoNameSpacingElement(this));
        holder.add(new UseRelativeTimeElement(this));
        holder.add(new PerProfileGamePathsElement(this));
        holder.add(new InstalledModsOnTopElement(this));
        holder.add(new LaunchWithSteamElement(this));
        holder.add(new AllowLaunchArgsElement(this));
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
    public void update(Consumer<Settings> v){
        v.accept(queued);
        saveButton.setEnabled(!initial.equals(queued));
    }


    private void applyAndSave(){
        try {
            UIManager.setLookAndFeel(queued.theme);
        } catch (ClassNotFoundException | UnsupportedLookAndFeelException | InstantiationException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        ProfileCardElement.normal = UIManager.getColor("Button.background").darker();
        ProfileCardElement.hover = UIManager.getColor("Button.pressedBackground");
        ProfileCardElement.hover = FlatLaf.isLafDark() ? ProfileCardElement.hover.brighter() : ProfileCardElement.hover.darker();
        FrameManager.getOrCreate().getCurrentPageButton().setBackground(ProfileCardElement.hover);
        Cogfly.settings = queued;
        if (!queued.profileSources.equals(initial.profileSources))
            ProfileManager.loadProfiles();
        if (queued.baseGameEnabled)
            Cogfly.downloadBepInEx(Path.of(queued.gamePath));
        SwingUtilities.invokeLater(ModPanelElement::redrawAll);
        SwingUtilities.updateComponentTreeUI(FrameManager.getOrCreate().frame);
        SwingUtilities.updateComponentTreeUI(this);
        Cogfly.settings.save();
    }

    private void resetQueue(){
        queued = Settings.load(Cogfly.dataJson);
        initial = Settings.load(Cogfly.dataJson);
    }
}