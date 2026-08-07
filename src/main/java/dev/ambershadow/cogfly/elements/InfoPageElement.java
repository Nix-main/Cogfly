package dev.ambershadow.cogfly.elements;

import com.kitfox.svg.app.beans.SVGIcon;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.asset.Assets;
import dev.ambershadow.cogfly.elements.profiles.ProfileCardElement;
import dev.ambershadow.cogfly.util.FileUtils;
import dev.ambershadow.cogfly.util.GameUtils;
import dev.ambershadow.cogfly.util.swing.HoverLerp;
import dev.ambershadow.cogfly.util.swing.ReloadablePage;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.URISyntaxException;

public class InfoPageElement extends JPanel implements ReloadablePage {

    private final JButton[] buttons = new JButton[3];
    public InfoPageElement() {
        setLayout(new BorderLayout());

        JLabel image = new JLabel(Assets.centralIcon.getAsScaledIcon(1 / 3f));
        image.setHorizontalAlignment(SwingConstants.CENTER);
        add(image, BorderLayout.NORTH);
        add(createButtons(), BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel("Links");
        label.setFont(new Font("Arial", Font.BOLD, 24));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label, BorderLayout.NORTH);
        panel.add(createLinks(), BorderLayout.SOUTH);
        add(panel, BorderLayout.SOUTH);
    }

    public JScrollPane createLinks() {
        String[] text = {
                "Modding Discord",
                "Source Code",
                "My Patreon"
        };
        String[] links = {
                "https://discord.gg/VDsg3HmWuB",
                "https://github.com/Nix-main/Cogfly",
                "https://patreon.com/c/AmberShadowo"
        };
        Dimension size = new Dimension(150, 125);
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        for (int i = 0; i < Assets.linkIcons.length; i++) {
            SVGIcon icon = new SVGIcon();
            try {
                icon.setSvgURI(Assets.linkIcons[i].url().toURI());
                icon.setPreferredSize(new Dimension(75, 0));
                icon.setAutosize(SVGIcon.AUTOSIZE_HORIZ);
                icon.setAntiAlias(true);
                buttons[i] = new JButton(text[i], icon);
                buttons[i].setForeground(Color.WHITE);
                buttons[i].setFont(new Font("Arial", Font.PLAIN, 14));
                buttons[i].setHorizontalTextPosition(SwingConstants.CENTER);
                buttons[i].setVerticalTextPosition(SwingConstants.TOP);
                buttons[i].setIconTextGap(8);
                buttons[i].setPreferredSize(size);
                buttons[i].setToolTipText(links[i]);
                final String link = links[i];
                buttons[i].addActionListener(_ -> FileUtils.openURI(URI.create(link)));
                HoverLerp.install(ProfileCardElement.normal, ProfileCardElement.hover, buttons[i]);
                panel.add(buttons[i]);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return new JScrollPane(panel, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
    }

    public JPanel createButtons() {
        Dimension dim = new Dimension(175, 40);
        Dimension max = new Dimension(Integer.MAX_VALUE, 40);

        JButton savesButton = new JButton("Open Saves Folder");
        savesButton.setIcon(Assets.openSaves.getAsIconWithColor(Color.RED));
        savesButton.setHorizontalAlignment(SwingConstants.LEFT);
        savesButton.setPreferredSize(dim);
        savesButton.setMaximumSize(max);
        savesButton.addActionListener(_ -> FileUtils.openSavePath());

        JButton logsButton = new JButton("Open Logs Folder");
        logsButton.setIcon(Assets.openSaves.getAsIconWithColor(Color.BLUE));
        logsButton.setHorizontalAlignment(SwingConstants.LEFT);
        logsButton.setPreferredSize(dim);
        logsButton.setMaximumSize(max);
        logsButton.addActionListener(_ -> FileUtils.openPath(Cogfly.localDataPath.resolve("logs")));

        JButton launchVanilla = new JButton("Launch Vanilla Game");
        launchVanilla.setHorizontalAlignment(SwingConstants.CENTER);
        launchVanilla.setPreferredSize(dim);
        launchVanilla.setMaximumSize(max);
        launchVanilla.addActionListener(_ -> GameUtils.launchGameAsync(false, "", Cogfly.settings.gamePath));

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

        buttons.add(savesButton);
        buttons.add(Box.createHorizontalGlue());
        buttons.add(Box.createHorizontalStrut(20));
        buttons.add(launchVanilla);
        buttons.add(Box.createHorizontalStrut(20));
        buttons.add(Box.createHorizontalGlue());
        buttons.add(logsButton);

        buttons.setBorder(
                BorderFactory.createEmptyBorder(0, 300, 0, 300)
        );

        UIManager.addPropertyChangeListener(e -> {
            if ("lookAndFeel".equals(e.getPropertyName())) {
                SwingUtilities.invokeLater(this::reload);
            }
        });

        return buttons;
    }

    @Override
    public void reload() {
        for (JButton button : buttons) {
            button.setBackground(ProfileCardElement.normal.get());
        }
    }
}
