package com.tradery.news.ui.coin;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemInfo;
import com.tradery.news.ui.IntelConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

/**
 * Standalone window for the ERD schema editor with unified title bar.
 */
public class DataStructureFrame extends JFrame {

    private final ErdPanel erdPanel;
    private SchemaRegistry schemaRegistry;

    public DataStructureFrame(EntityStore store, Consumer<Void> onDataChanged) {
        super("Data Structure");
        this.schemaRegistry = null; // loaded off EDT below

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Restore window size/position from config or use large default
        IntelConfig config = IntelConfig.get();
        if (config.getDataStructureWidth() > 0 && config.getDataStructureHeight() > 0) {
            setSize(config.getDataStructureWidth(), config.getDataStructureHeight());
            if (config.getDataStructureX() >= 0 && config.getDataStructureY() >= 0) {
                setLocation(config.getDataStructureX(), config.getDataStructureY());
            } else {
                setLocationRelativeTo(null);
            }
        } else {
            // Default: 80% of screen size
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int width = (int) (screen.width * 0.8);
            int height = (int) (screen.height * 0.8);
            setSize(width, height);
            setLocationRelativeTo(null);
        }

        // Save window state on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                IntelConfig cfg = IntelConfig.get();
                cfg.setDataStructureWidth(getWidth());
                cfg.setDataStructureHeight(getHeight());
                cfg.setDataStructureX(getX());
                cfg.setDataStructureY(getY());
                cfg.save();
            }
        });

        // Transparent title bar (macOS unified style)
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        // Create ERD panel first so toolbar lambdas can reference it
        erdPanel = new ErdPanel();
        erdPanel.setOnDataChanged(onDataChanged);

        // Load schema registry off EDT, then set it
        new Thread(() -> {
            SchemaRegistry reg = new SchemaRegistry(store);
            SwingUtilities.invokeLater(() -> {
                schemaRegistry = reg;
                erdPanel.setRegistry(reg);
            });
        }).start();

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(30, 32, 36));

        // Header bar (unified with title bar)
        JPanel headerBar = createHeaderBar();
        mainPanel.add(headerBar, BorderLayout.NORTH);
        mainPanel.add(erdPanel, BorderLayout.CENTER);

        setContentPane(mainPanel);
    }

    private JPanel createHeaderBar() {
        int barHeight = 52;

        JPanel headerBar = new JPanel(new GridBagLayout());
        headerBar.setBackground(new Color(38, 40, 44));
        headerBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 52, 56)));
        headerBar.setPreferredSize(new Dimension(0, barHeight));
        headerBar.setMinimumSize(new Dimension(0, barHeight));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;

        // Left: Layout mode toggles with FlatLaf placeholder for traffic lights
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.WEST;
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setOpaque(false);
        JPanel leftContent = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftContent.setOpaque(false);
        if (SystemInfo.isMacOS) {
            JPanel buttonsPlaceholder = new JPanel();
            buttonsPlaceholder.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER, "mac");
            buttonsPlaceholder.setOpaque(false);
            leftContent.add(buttonsPlaceholder);
        }

        ButtonGroup layoutGroup = new ButtonGroup();

        JToggleButton manualBtn = createLayoutToggle("Manual");
        manualBtn.setSelected(true);
        manualBtn.addActionListener(e -> erdPanel.manualLayout());
        layoutGroup.add(manualBtn);
        leftContent.add(manualBtn);

        // When save label is clicked inside the ERD, select the Manual toggle
        erdPanel.setOnManualSelected(() -> manualBtn.setSelected(true));

        JToggleButton treeBtn = createLayoutToggle("Tree");
        treeBtn.addActionListener(e -> erdPanel.treeLayout());
        layoutGroup.add(treeBtn);
        leftContent.add(treeBtn);

        JToggleButton springBtn = createLayoutToggle("Spring");
        springBtn.addActionListener(e -> erdPanel.springLayout());
        layoutGroup.add(springBtn);
        leftContent.add(springBtn);

        // Separator
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 20));
        sep.setForeground(new Color(60, 62, 66));
        leftContent.add(sep);

        // Flow mode toggle (separate from layout group)
        boolean initialFlowMode = IntelConfig.get().isErdFlowMode();
        erdPanel.setFlowMode(initialFlowMode);

        JToggleButton flowToggle = createLayoutToggle("Flow");
        flowToggle.setSelected(initialFlowMode);
        flowToggle.addActionListener(e -> {
            boolean flow = flowToggle.isSelected();
            erdPanel.setFlowMode(flow);
            IntelConfig cfg = IntelConfig.get();
            cfg.setErdFlowMode(flow);
            cfg.save();
        });
        leftContent.add(flowToggle);

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.fill = GridBagConstraints.HORIZONTAL;
        lc.weightx = 1.0;
        leftPanel.add(leftContent, lc);
        headerBar.add(leftPanel, gbc);

        // Center: Title
        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        JLabel titleLabel = new JLabel("Data Structure");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(new Color(160, 160, 170));
        headerBar.add(titleLabel, gbc);

        // Right: Fit + Entity Type
        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.EAST;
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);
        JPanel rightContent = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightContent.setOpaque(false);

        JButton fitBtn = new JButton("Fit");
        fitBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        fitBtn.setMargin(new Insets(6, 14, 6, 14));
        fitBtn.addActionListener(e -> erdPanel.fitToView());
        rightContent.add(fitBtn);

        JButton addEntityTypeBtn = new JButton("New Type");
        addEntityTypeBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        addEntityTypeBtn.setMargin(new Insets(6, 14, 6, 14));
        addEntityTypeBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Entity type name:", "Add Entity Type",
                JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                String id = name.trim().toLowerCase().replaceAll("\\s+", "_");
                SchemaType type = new SchemaType(id, name.trim(),
                    new Color(100 + (int)(Math.random() * 120), 100 + (int)(Math.random() * 120), 100 + (int)(Math.random() * 120)),
                    SchemaType.KIND_ENTITY);
                type.setDisplayOrder(schemaRegistry.entityTypes().size());
                SchemaAttribute nameAttr = new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0);
                type.addAttribute(nameAttr);
                schemaRegistry.save(type);
                schemaRegistry.addAttribute(id, nameAttr);
            }
        });
        rightContent.add(addEntityTypeBtn);

        GridBagConstraints rc = new GridBagConstraints();
        rc.anchor = GridBagConstraints.EAST;
        rc.fill = GridBagConstraints.HORIZONTAL;
        rc.weightx = 1.0;
        rightPanel.add(rightContent, rc);
        headerBar.add(rightPanel, gbc);

        return headerBar;
    }

    private JToggleButton createLayoutToggle(String text) {
        JToggleButton btn = new JToggleButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setMargin(new Insets(6, 14, 6, 14));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setBackground(new Color(38, 40, 44));
        btn.setForeground(new Color(160, 160, 170));
        btn.addChangeListener(e -> {
            if (btn.isSelected()) {
                btn.setBackground(new Color(55, 57, 61));
                btn.setForeground(new Color(220, 220, 230));
            } else {
                btn.setBackground(new Color(38, 40, 44));
                btn.setForeground(new Color(160, 160, 170));
            }
        });
        return btn;
    }
}
