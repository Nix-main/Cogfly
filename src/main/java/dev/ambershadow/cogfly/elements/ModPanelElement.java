package dev.ambershadow.cogfly.elements;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.util.Profile;
import dev.ambershadow.cogfly.util.Utils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ModPanelElement extends JPanel {
    private static final HashMap<Profile, ModPanelElement> panels = new HashMap<>();

    public static void redraw(Profile profile) {
        if (panels.containsKey(profile))
            panels.get(profile).redrawPanel();
    }


    private final Profile profile;
    private final JTextField searchField;
    private final JPanel buttonsPanel;
    private Cogfly.SortingType current;


    public ModPanelElement(Profile profile) {
        super(new BorderLayout());
        this.profile = profile;
        panels.put(profile, this);
        setBorder(BorderFactory.createEmptyBorder());
        setPreferredSize(new Dimension(1100, 525));
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));

        JPanel sortingPanel = new JPanel();
        sortingPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        searchField = new JTextField();
        searchField.setToolTipText("Search mods...");

        JComboBox<String> sortingOrder = new JComboBox<>();
        sortingOrder.addItem("Name");
        sortingOrder.addItem("Downloads");
        sortingOrder.addItem("Date Created");
        sortingOrder.addItem("Date Updated");
        sortingOrder.addItem("Installed");
        sortingOrder.setSelectedIndex(0);
        current = Cogfly.SortingType.values()[0];

        JComboBox<String> sortingDirection = new JComboBox<>();
        sortingDirection.addItem("Ascending");
        sortingDirection.addItem("Descending");
        sortingDirection.setSelectedIndex(1);

        sortingPanel.add(sortingDirection);
        sortingPanel.add(sortingOrder);

        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(sortingPanel, BorderLayout.EAST);

        searchPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.Y_AXIS));

        buttonsPanel.setBorder(
                BorderFactory.createEmptyBorder(0, 5, 0, 0)
        );

        sortingOrder.addActionListener(_ -> {
            Cogfly.SortingType sortingType = Cogfly.SortingType.values()[sortingOrder.getSelectedIndex()];
            current = sortingType;
            //noinspection DataFlowIssue
            Cogfly.sortList(sortingType, sortingDirection.getSelectedItem().toString());
            redrawPanel();
        });

        sortingDirection.addActionListener(_ -> {
            Cogfly.SortingType sortingType = Cogfly.SortingType.values()[sortingOrder.getSelectedIndex()];
            current = sortingType;
            //noinspection DataFlowIssue
            Cogfly.sortList(sortingType, sortingDirection.getSelectedItem().toString());
            redrawPanel();
        });

        JScrollPane scrollPane = new JScrollPane(
                buttonsPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );

        scrollPane.setBorder(null);

        add(searchPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);


        redrawPanel();

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterButtons();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterButtons();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterButtons();
            }
        });

        searchField.setSize(getSize());
        buttonsPanel.setSize(getSize());
        scrollPane.setSize(getSize());
    }

    private void refreshButtons(List<ModData> mods) {
        buttonsPanel.removeAll();
        for (ModData mod : mods) {
            JPanel modPanel = new JPanel();
            modPanel.setLayout(new BoxLayout(modPanel, BoxLayout.Y_AXIS));

            JPanel rowPanel = new JPanel();
            rowPanel.setLayout(new BoxLayout(rowPanel, BoxLayout.X_AXIS));

            JToggleButton toggleButton;
            if (mod.getName().toLowerCase().contains("bepinex"))
                toggleButton = new JToggleButton("▼ " + mod.getName().replace("_", " "));
            else {
                String name = mod.getName().replace("_", " ");
                name = Cogfly.settings.modNameSpaces ? name.replaceAll("(?<=[a-z])(?=[A-Z])", " ") : name;
                toggleButton = new JToggleButton("▼ " + name);
            }

            toggleButton.setHorizontalAlignment(SwingConstants.LEFT);
            toggleButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            toggleButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, toggleButton.getPreferredSize().height));
            JButton installButton = new JButton();
            installButton.setText(mod.isInstalled(profile) ? mod.isOutdated(profile) ? "Update" : "Uninstall" : "Install");
            installButton.setPreferredSize(new Dimension(100, installButton.getPreferredSize().height));
            installButton.setAlignmentY(Component.CENTER_ALIGNMENT);

            rowPanel.add(toggleButton);
            rowPanel.add(Box.createHorizontalGlue());
            rowPanel.add(installButton);

            rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, toggleButton.getPreferredSize().height));
            rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            modPanel.add(rowPanel);

            JPanel infoPanel = new JPanel();
            infoPanel.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            infoPanel.add(new JLabel("Description: " + mod.getDescription()));
            infoPanel.add(new JLabel("Author: " + mod.getAuthor()));
            infoPanel.add(new JLabel("Latest version: " + mod.getVersionNumber()));
            infoPanel.add(new JLabel("Total downloads: " + mod.getTotalDownloads()));
            Instant created = Instant.parse(mod.getDateCreated());
            ZoneId userZone = ZoneId.systemDefault();
            ZonedDateTime localCreated = created.atZone(userZone);
            Instant updated = Instant.parse(mod.getDateModified());
            ZonedDateTime localModified = updated.atZone(userZone);
            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            infoPanel.add(new JLabel("Date created: " + localCreated.format(formatter)));
            infoPanel.add(new JLabel("Date updated: " + localModified.format(formatter)));
            if (!mod.getDependencies().isEmpty()) {
                infoPanel.add(new JLabel(" "));
                infoPanel.add(new JLabel("Dependencies"));
            }
            for (String string : mod.getDependencies()){
                String[] strs = string.trim().replaceAll("_", " ").split("-");
                JLabel label;
                if (strs.length > 2) {
                    label = new JLabel("\t• " + strs[strs.length - 2] + " - " + strs[strs.length - 1]);
                } else {
                    label = new JLabel("\t• " + strs[strs.length-1]);
                }
                label.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
                infoPanel.add(label);
            }

            JButton open = new JButton("Open Thunderstore Page");
            open.addActionListener(_ -> {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    try {
                        Desktop.getDesktop().browse(mod.getPackageUrl());
                    } catch (IOException a) {
                        throw new RuntimeException(a);
                    }
                }
            });
            JButton openWebsite = new JButton("Open Project Website");
            openWebsite.addActionListener(_ -> {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    try {
                        Desktop.getDesktop().browse(mod.getWebsiteUrl());
                    } catch (IOException a) {
                        throw new RuntimeException(a);
                    }
                }
            });

            Box buttonBox = Box.createHorizontalBox();
            buttonBox.add(open);
            if (mod.getWebsiteUrl() != null) {
                buttonBox.add(Box.createRigidArea(new Dimension(10, 0)));
                buttonBox.add(openWebsite);
            }

            buttonBox.setAlignmentX(Component.LEFT_ALIGNMENT);

            infoPanel.add(buttonBox);

            infoPanel.setVisible(false);
            infoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            modPanel.add(infoPanel);

            toggleButton.addActionListener(_ -> {
                infoPanel.setVisible(toggleButton.isSelected());
                if (toggleButton.isSelected()) {
                    toggleButton.setText(toggleButton.getText().replace('▼', '▲'));
                } else {
                    toggleButton.setText(toggleButton.getText().replace('▲', '▼'));
                }
            });

            installButton.addActionListener(_ -> {
                if (mod.isInstalled(profile) && !mod.isOutdated(profile)) Utils.removeMod(mod, profile);
                else Utils.downloadMod(mod, profile);
                filterButtons();
            });

            buttonsPanel.add(modPanel);
            buttonsPanel.add(Box.createVerticalStrut(5));
        }
        buttonsPanel.revalidate();
        buttonsPanel.repaint();
    }

    private void filterButtons() {
        String query = searchField.getText().toLowerCase();
        List<ModData> filtered = new ArrayList<>();
        for (ModData mod : Cogfly.getDisplayedMods(current, profile)) {
            if (mod.getName().replaceAll(" ", "")
                    .toLowerCase().contains(query.replaceAll(" ", ""))) {
                filtered.add(mod);
            }
        }
        refreshButtons(filtered);
    }

    private void redrawPanel(){
        String query = searchField.getText();
        if (query.isEmpty())
            refreshButtons(Cogfly.getDisplayedMods(current, profile));
        else
            filterButtons();
    }
}