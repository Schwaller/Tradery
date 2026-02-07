package com.tradery.desk.ui;

import com.tradery.ui.ThemeHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Settings dialog for Tradery Desk.
 * Currently supports theme switching (shared with other Tradery apps).
 */
public class DeskSettingsDialog extends JDialog {

    public DeskSettingsDialog(JFrame owner) {
        super(owner, "Settings", true);
        setSize(400, 500);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Theme section
        JLabel themeLabel = new JLabel("Theme");
        themeLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        themeLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
        content.add(themeLabel, BorderLayout.NORTH);

        List<String> themes = ThemeHelper.getAvailableThemes();
        DefaultListModel<String> listModel = new DefaultListModel<>();
        themes.forEach(listModel::addElement);

        JList<String> themeList = new JList<>(listModel);
        themeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        themeList.setSelectedValue(ThemeHelper.getCurrentTheme(), true);
        themeList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = themeList.getSelectedValue();
                if (selected != null) {
                    ThemeHelper.setTheme(selected);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(themeList);
        content.add(scrollPane, BorderLayout.CENTER);

        // Close button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        buttonPanel.add(closeBtn);
        content.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(content);
    }
}
