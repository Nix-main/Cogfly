package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.elements.SettingsDialog;
import dev.ambershadow.cogfly.util.FileUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.Paths;

public class ManageProfileSourcesDialog extends JDialog {

    public final JTable table;
    public ManageProfileSourcesDialog(SettingsDialog base) {
        super(base, "Manage Profile Sources", true);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setSize(800, 320);
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Path"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        base.get(s -> s.profileSources).forEach(
                source -> model.addRow(new Object[]{source}));
        JPanel tableWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        setResizable(false);
        table = new JTable(model);
        table.setCellSelectionEnabled(true);
        table.setColumnSelectionAllowed(false);
        table.setShowGrid(true);
        table.setGridColor(new Color(9, 125, 141));
        table.setRowHeight(25);
        table.setBorder(BorderFactory.createLineBorder(new Color(9, 125, 141)));

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(700, 200));
        tableWrapper.add(scrollPane, BorderLayout.CENTER);

        add(tableWrapper);

        JPanel buttonWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton button1 = new JButton("Add");
        button1.addActionListener(_ -> FileUtils.pickFolder((path) -> {
            if (path.equals(Paths.get(base.<String>get(s -> s.profileSavePath))))
                return;
            model.setRowCount(model.getRowCount() + 1);
            table.setModel(model);
            table.setValueAt(path.toString(), model.getRowCount()-1, 0);
            base.update(s -> s.profileSources.add(path.toString()));
        }));
        JButton button2 = new JButton("Remove");
        button2.setEnabled(table.getSelectedRow() != -1);
        button2.addActionListener(_ -> {
            int row = table.getSelectedRow();
            if (row >= 0 && row < base.get(s -> s.profileSources).size()) {
                base.update(s -> s.profileSources.remove(row));
                model.removeRow(row);
                table.setModel(model);
            }
        });
        Timer timer = new Timer(50, _ -> button2.setEnabled(table.getSelectedRow() != -1));
        timer.start();
        buttonWrapper.add(button1, BorderLayout.EAST);
        buttonWrapper.add(Box.createHorizontalStrut(200));
        buttonWrapper.add(button2, BorderLayout.WEST);
        add(buttonWrapper, BorderLayout.SOUTH);
    }
}
