package com.tradery.ui.settings;

import com.tradery.ui.ThemeHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Base settings dialog with macOS integrated title bar, built-in Appearance
 * section (theme combo via {@link ThemeHelper}), and a Close button bar.
 * Subclasses override {@link #addSections()} to provide app-specific sections.
 */
public abstract class SettingsDialog extends JDialog {

    protected SettingsDialog(Window owner) {
        super(owner, "Settings", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Integrated macOS title bar (string literals to avoid FlatLaf module dep)
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty("FlatLaf.macOS.windowButtonsSpacing", "large");

        initComponents();
        pack();
        setMinimumSize(new Dimension(500, 600));
        setLocationRelativeTo(owner);
    }

    /**
     * Return app-specific section panels to add after the Appearance section.
     * Each panel is wrapped via {@link #createSection(String, JPanel, boolean)}.
     * Return an empty list if no extra sections are needed.
     */
    protected abstract List<SectionEntry> addSections();

    private void initComponents() {
        JPanel contentPane = new JPanel(new BorderLayout());
        setContentPane(contentPane);

        // 52px header bar with centered title
        int barHeight = 52;
        JPanel headerWrapper = new JPanel(new BorderLayout());
        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setPreferredSize(new Dimension(0, barHeight));

        // Spacers to keep title clear of macOS traffic light buttons
        JPanel leftSpacer = new JPanel();
        leftSpacer.setPreferredSize(new Dimension(80, 0));
        leftSpacer.setOpaque(false);
        headerBar.add(leftSpacer, BorderLayout.WEST);

        JPanel rightSpacer = new JPanel();
        rightSpacer.setPreferredSize(new Dimension(80, 0));
        rightSpacer.setOpaque(false);
        headerBar.add(rightSpacer, BorderLayout.EAST);

        JLabel titleLabel = new JLabel("Settings", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        headerBar.add(titleLabel, BorderLayout.CENTER);

        headerWrapper.add(headerBar, BorderLayout.CENTER);
        headerWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        contentPane.add(headerWrapper, BorderLayout.NORTH);

        // Sections
        JPanel sectionsPanel = new JPanel();
        sectionsPanel.setLayout(new BoxLayout(sectionsPanel, BoxLayout.Y_AXIS));
        sectionsPanel.add(createSection("Appearance", createThemeContent(), false));

        for (SectionEntry entry : addSections()) {
            sectionsPanel.add(createSection(entry.title(), entry.content(), true));
        }

        // Button bar with separator above
        JPanel buttonBar = new JPanel(new BorderLayout());
        buttonBar.add(new JSeparator(), BorderLayout.NORTH);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setBorder(new EmptyBorder(10, 16, 10, 16));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        buttonPanel.add(closeBtn);
        buttonBar.add(buttonPanel, BorderLayout.CENTER);

        // Scrollable sections area
        JPanel sectionsWrapper = new JPanel(new BorderLayout());
        sectionsWrapper.add(sectionsPanel, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(sectionsWrapper);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel bodyPanel = new JPanel(new BorderLayout());
        bodyPanel.add(scrollPane, BorderLayout.CENTER);
        bodyPanel.add(buttonBar, BorderLayout.SOUTH);

        contentPane.add(bodyPanel, BorderLayout.CENTER);
    }

    /**
     * Create a titled section panel with optional separator above.
     * Subclasses can use this to build custom sections.
     */
    protected JPanel createSection(String title, JPanel content, boolean showSeparator) {
        JPanel section = new JPanel(new BorderLayout());

        if (showSeparator) {
            section.add(new JSeparator(), BorderLayout.NORTH);
        }

        JPanel inner = new JPanel(new BorderLayout(0, 6));
        inner.setBorder(new EmptyBorder(10, 20, 10, 20));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize2D() + 1f));
        inner.add(titleLabel, BorderLayout.NORTH);
        inner.add(content, BorderLayout.CENTER);

        section.add(inner, BorderLayout.CENTER);
        return section;
    }

    private JPanel createThemeContent() {
        JPanel panel = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 0, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Theme:"), gbc);

        JComboBox<String> themeCombo = new JComboBox<>();
        for (String themeName : ThemeHelper.getAvailableThemes()) {
            themeCombo.addItem(themeName);
        }
        themeCombo.setSelectedItem(ThemeHelper.getCurrentTheme());
        themeCombo.addActionListener(e -> {
            String selected = (String) themeCombo.getSelectedItem();
            if (selected != null) {
                ThemeHelper.setTheme(selected);
            }
        });

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(themeCombo, gbc);

        return panel;
    }

    /**
     * A section entry for subclasses to return from {@link #addSections()}.
     */
    public record SectionEntry(String title, JPanel content) {}
}
