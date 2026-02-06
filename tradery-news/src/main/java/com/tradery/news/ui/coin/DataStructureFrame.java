package com.tradery.news.ui.coin;

import com.formdev.flatlaf.util.SystemInfo;
import com.tradery.news.ui.IntelConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

/**
 * Standalone window for the ERD schema editor with unified title bar.
 */
public class DataStructureFrame extends JFrame {

    private final ErdPanel erdPanel;
    private final SchemaRegistry schemaRegistry;

    public DataStructureFrame(EntityStore store, Consumer<Void> onDataChanged) {
        super("Data Structure");
        this.schemaRegistry = new SchemaRegistry(store);

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

        // Create ERD panel first so toolbar lambdas can reference it
        erdPanel = new ErdPanel();
        erdPanel.setRegistry(schemaRegistry);
        erdPanel.setOnDataChanged(onDataChanged);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(30, 32, 36));

        // Header bar (unified with title bar)
        JPanel headerBar = createHeaderBar();
        mainPanel.add(headerBar, BorderLayout.NORTH);
        mainPanel.add(erdPanel, BorderLayout.CENTER);

        setContentPane(mainPanel);
    }

    private JPanel createHeaderBar() {
        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setBackground(new Color(38, 40, 44));
        headerBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 52, 56)));

        // Left: Layout mode toggles (badge-style like IntelFrame News/Coin Relations)
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        leftPanel.setOpaque(false);
        if (SystemInfo.isMacOS) {
            leftPanel.setBorder(new EmptyBorder(0, 70, 0, 0));
        }

        ButtonGroup layoutGroup = new ButtonGroup();

        JToggleButton manualBtn = createLayoutToggle("Manual");
        manualBtn.setSelected(true);
        manualBtn.addActionListener(e -> erdPanel.manualLayout());
        layoutGroup.add(manualBtn);
        leftPanel.add(manualBtn);

        // When save label is clicked inside the ERD, select the Manual toggle
        erdPanel.setOnManualSelected(() -> manualBtn.setSelected(true));

        JToggleButton treeBtn = createLayoutToggle("Tree");
        treeBtn.addActionListener(e -> { erdPanel.treeLayout(); erdPanel.fitToView(); });
        layoutGroup.add(treeBtn);
        leftPanel.add(treeBtn);

        JToggleButton springBtn = createLayoutToggle("Spring");
        springBtn.addActionListener(e -> erdPanel.springLayout());
        layoutGroup.add(springBtn);
        leftPanel.add(springBtn);

        headerBar.add(leftPanel, BorderLayout.WEST);

        // Center: Title
        JLabel titleLabel = new JLabel("Data Structure", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(new Color(160, 160, 170));
        headerBar.add(titleLabel, BorderLayout.CENTER);

        // Right: Fit + Entity Type
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        rightPanel.setOpaque(false);

        JButton fitBtn = new JButton("Fit");
        fitBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        fitBtn.setMargin(new Insets(6, 14, 6, 14));
        fitBtn.addActionListener(e -> erdPanel.fitToView());
        rightPanel.add(fitBtn);

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
        rightPanel.add(addEntityTypeBtn);

        headerBar.add(rightPanel, BorderLayout.EAST);

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
