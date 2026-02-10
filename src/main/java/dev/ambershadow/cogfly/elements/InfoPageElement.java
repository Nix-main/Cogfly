package dev.ambershadow.cogfly.elements;

import com.kitfox.svg.app.beans.SVGIcon;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.asset.Assets;
import dev.ambershadow.cogfly.elements.profiles.ProfileCardElement;
import dev.ambershadow.cogfly.util.HoverLerp;
import dev.ambershadow.cogfly.util.ReloadablePage;
import dev.ambershadow.cogfly.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;

public class InfoPageElement extends JPanel implements ReloadablePage {


    private JButton github;
    private JButton discord;
    public InfoPageElement() {
        setLayout(new BorderLayout());

        JLabel image = new JLabel(Assets.centralIcon.getAsScaledIcon(0.333333f));
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

    public JScrollPane createLinks(){
        Dimension size = new Dimension(150, 125);
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        SVGIcon icon = new SVGIcon();
        try {
            icon.setSvgURI(Assets.github.url().toURI());
        } catch (URISyntaxException e){
            throw new RuntimeException(e);
        }
        icon.setPreferredSize(new Dimension(75, 0));
        icon.setAutosize(SVGIcon.AUTOSIZE_HORIZ);
        icon.setAntiAlias(true);
        github = new JButton("Source Code", icon);
        github.setForeground(Color.WHITE);
        github.setFont(new Font("Arial", Font.PLAIN, 14));
        github.setHorizontalTextPosition(SwingConstants.CENTER);
        github.setVerticalTextPosition(SwingConstants.TOP);
        github.setIconTextGap(8);
        github.setPreferredSize(size);
        github.setToolTipText("https://github.com/nix-main/Cogfly");
        github.addActionListener(_ -> Utils.openURI(URI.create("https://github.com/nix-main/Cogfly")));
        HoverLerp.install(github, () -> ProfileCardElement.normal, () -> ProfileCardElement.hover);
        panel.add(github);

        SVGIcon icon2 = new SVGIcon();
        try {
            icon2.setSvgURI(Assets.discord.url().toURI());
        } catch (URISyntaxException e){
            throw new RuntimeException(e);
        }
        icon2.setPreferredSize(new Dimension(75, 0));
        icon2.setAutosize(SVGIcon.AUTOSIZE_HORIZ);
        icon2.setAntiAlias(true);
        discord = new JButton("Modding Discord", icon2);
        discord.setForeground(Color.WHITE);
        discord.setFont(new Font("Arial", Font.PLAIN, 14));
        discord.setHorizontalTextPosition(SwingConstants.CENTER);
        discord.setVerticalTextPosition(SwingConstants.TOP);
        discord.setIconTextGap(8);
        discord.setPreferredSize(size);
        discord.setToolTipText("https://discord.gg/VDsg3HmWuB");
        discord.addActionListener(_ -> Utils.openURI(URI.create("https://discord.gg/VDsg3HmWuB")));
        HoverLerp.install(discord, () -> ProfileCardElement.normal, () -> ProfileCardElement.hover);
        panel.add(discord);

        return new JScrollPane(panel, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
    }

    public JPanel createButtons(){
        Dimension dim = new Dimension(175, 40);
        Dimension max = new Dimension(Integer.MAX_VALUE, 40);

        JButton savesButton = new JButton("Open Saves Folder");
        savesButton.setIcon(Assets.openSaves.getAsIconWithColor(Color.RED));
        savesButton.setHorizontalAlignment(SwingConstants.LEFT);
        savesButton.setPreferredSize(dim);
        savesButton.setMaximumSize(max);
        savesButton.addActionListener(_ -> Utils.openSavePath());

        JButton logsButton = new JButton("Open Logs Folder");
        logsButton.setIcon(Assets.openSaves.getAsIconWithColor(Color.BLUE));
        logsButton.setHorizontalAlignment(SwingConstants.LEFT);
        logsButton.setPreferredSize(dim);
        logsButton.setMaximumSize(max);
        logsButton.addActionListener(_ -> Utils.openPath(Paths.get(Cogfly.localDataPath).resolve("logs")));

        JButton launchVanilla = new JButton("Launch Vanilla Game");
        launchVanilla.setHorizontalAlignment(SwingConstants.CENTER);
        launchVanilla.setPreferredSize(dim);
        launchVanilla.setMaximumSize(max);
        launchVanilla.addActionListener(_ -> Cogfly.launchGameAsync(false, "", Cogfly.settings.gamePath));

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

        return buttons;
    }

    @Override
    public void reload() {
        discord.setBackground(ProfileCardElement.normal);
        github.setBackground(ProfileCardElement.normal);
    }
}
