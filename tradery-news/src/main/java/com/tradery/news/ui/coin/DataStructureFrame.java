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

        // Left: spacer for macOS traffic lights
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        leftPanel.setOpaque(false);
        if (SystemInfo.isMacOS) {
            leftPanel.setBorder(new EmptyBorder(0, 70, 0, 0));
        }

        JButton addEntityTypeBtn = createHeaderButton("+ Entity Type");
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
                erdPanel.autoLayout();
            }
        });
        leftPanel.add(addEntityTypeBtn);

        headerBar.add(leftPanel, BorderLayout.WEST);

        // Center: Title
        JLabel titleLabel = new JLabel("Data Structure", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(new Color(160, 160, 170));
        headerBar.add(titleLabel, BorderLayout.CENTER);

        // Right: Layout buttons
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        rightPanel.setOpaque(false);

        JButton treeLayoutBtn = createHeaderButton("Tree Layout");
        treeLayoutBtn.addActionListener(e -> { erdPanel.treeLayout(); erdPanel.fitToView(); });
        rightPanel.add(treeLayoutBtn);

        JButton autoLayoutBtn = createHeaderButton("Spring Layout");
        autoLayoutBtn.addActionListener(e -> erdPanel.autoLayout());
        rightPanel.add(autoLayoutBtn);

        JButton fitBtn = createHeaderButton("Fit to View");
        fitBtn.addActionListener(e -> erdPanel.fitToView());
        rightPanel.add(fitBtn);

        JToggleButton pinBtn = new JToggleButton("Pin All");
        pinBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        pinBtn.setMargin(new Insets(6, 14, 6, 14));
        pinBtn.addActionListener(e -> {
            boolean pinned = pinBtn.isSelected();
            erdPanel.setPinAll(pinned);
            pinBtn.setText(pinned ? "Unpin All" : "Pin All");
        });
        rightPanel.add(pinBtn);

        headerBar.add(rightPanel, BorderLayout.EAST);

        return headerBar;
    }

    private JButton createHeaderButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btn.setMargin(new Insets(6, 14, 6, 14));
        return btn;
    }
}
