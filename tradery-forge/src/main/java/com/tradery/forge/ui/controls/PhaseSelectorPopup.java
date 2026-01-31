package com.tradery.forge.ui.controls;

import com.tradery.core.model.Phase;
import com.tradery.forge.ApplicationContext;
import com.tradery.forge.io.PhaseStore;
import com.tradery.forge.ui.charts.ChartConfig;
import com.tradery.ui.controls.IndicatorSelectorPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * Popup for selecting which phases to display as overlays on the price chart.
 * Phases are grouped by category with colored swatches.
 * Follows the same popup pattern as IndicatorSelectorPopup.
 */
public class PhaseSelectorPopup extends JDialog {

    private static final Map<String, Color> CATEGORY_COLORS = Map.of(
        "Trend", new Color(0x22C55E),
        "Session", new Color(0x3B82F6),
        "Time", new Color(0x06B6D4),
        "Calendar", new Color(0xF97316),
        "Funding", new Color(0xA855F7),
        "Moon", new Color(0x6366F1),
        "Custom", new Color(0x6B7280)
    );

    private static final int ALPHA = 38; // ~15% of 255

    private final Map<String, JCheckBox> checkBoxes = new LinkedHashMap<>();
    private final Runnable onChanged;
    private boolean initializing = true;

    public PhaseSelectorPopup(Window owner, Runnable onChanged) {
        super(owner, "Phases", ModalityType.MODELESS);
        this.onChanged = onChanged;

        setUndecorated(true);
        setResizable(false);

        initComponents();
        syncFromConfig();
        initializing = false;

        // Close on focus lost
        addWindowFocusListener(new WindowFocusListener() {
            @Override public void windowGainedFocus(WindowEvent e) {}
            @Override public void windowLostFocus(WindowEvent e) {
                SwingUtilities.invokeLater(() -> {
                    Window focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
                    if (focused != PhaseSelectorPopup.this) {
                        dispose();
                    }
                });
            }
        });

        // Close on Escape
        getRootPane().registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Close on click outside
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e instanceof MouseEvent me && me.getID() == MouseEvent.MOUSE_PRESSED) {
                Point clickPoint = me.getLocationOnScreen();
                if (isVisible() && !getBounds().contains(clickPoint)) {
                    dispose();
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    private void initComponents() {
        PhaseStore phaseStore = ApplicationContext.getInstance().getPhaseStore();
        List<Phase> allPhases = phaseStore.loadAll();

        // Group by category
        Map<String, List<Phase>> grouped = new LinkedHashMap<>();
        for (Phase phase : allPhases) {
            String cat = phase.getCategory() != null ? phase.getCategory() : "Custom";
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(phase);
        }

        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
        contentPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground"), 1),
            new EmptyBorder(8, 12, 8, 12)
        ));

        contentPane.add(IndicatorSelectorPanel.createSectionHeader("PHASE OVERLAYS"));

        for (Map.Entry<String, List<Phase>> entry : grouped.entrySet()) {
            String category = entry.getKey();
            List<Phase> phases = entry.getValue();

            // Category sub-header
            JLabel header = new JLabel(category);
            header.setFont(header.getFont().deriveFont(Font.BOLD, 10f));
            header.setForeground(UIManager.getColor("Label.disabledForeground"));
            header.setBorder(new EmptyBorder(6, 2, 2, 0));
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentPane.add(header);

            Color baseColor = CATEGORY_COLORS.getOrDefault(category, CATEGORY_COLORS.get("Custom"));

            for (Phase phase : phases) {
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);

                // Color swatch
                JPanel swatch = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        g.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), ALPHA));
                        g.fillRect(0, 0, getWidth(), getHeight());
                        g.setColor(baseColor);
                        g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                    }
                };
                swatch.setPreferredSize(new Dimension(12, 12));
                swatch.setOpaque(false);

                String label = phase.getName() != null ? phase.getName() : phase.getId();
                JCheckBox cb = new JCheckBox(label);
                cb.setFocusPainted(false);
                cb.setFont(cb.getFont().deriveFont(11f));
                if (phase.getDescription() != null) {
                    cb.setToolTipText(phase.getDescription());
                }
                cb.addActionListener(e -> {
                    if (!initializing) applyChanges();
                });
                checkBoxes.put(phase.getId(), cb);

                row.add(swatch);
                row.add(cb);
                contentPane.add(row);
            }
        }

        JScrollPane scrollPane = new JScrollPane(contentPane);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);

        setContentPane(scrollPane);
        pack();

        // Cap height
        GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc != null) {
            Rectangle screenBounds = gc.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            int maxHeight = screenBounds.height - insets.top - insets.bottom - 40;
            if (getHeight() > maxHeight) {
                setSize(getWidth() + scrollPane.getVerticalScrollBar().getPreferredSize().width, maxHeight);
            }
        }
    }

    private void syncFromConfig() {
        Set<String> selected = new HashSet<>(ChartConfig.getInstance().getPhaseOverlayIds());
        for (Map.Entry<String, JCheckBox> entry : checkBoxes.entrySet()) {
            entry.getValue().setSelected(selected.contains(entry.getKey()));
        }
    }

    private void applyChanges() {
        List<String> selected = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> entry : checkBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        ChartConfig.getInstance().setPhaseOverlayIds(selected);
        if (onChanged != null) {
            onChanged.run();
        }
    }

    /**
     * Get the overlay color for a phase category (with alpha for semi-transparency).
     */
    public static Color getPhaseColor(String category) {
        Color base = CATEGORY_COLORS.getOrDefault(
            category != null ? category : "Custom",
            CATEGORY_COLORS.get("Custom")
        );
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), ALPHA);
    }

    public static void showBelow(Component anchor, Runnable onChanged) {
        Window window = SwingUtilities.getWindowAncestor(anchor);
        PhaseSelectorPopup popup = new PhaseSelectorPopup(window, onChanged);
        Point loc = anchor.getLocationOnScreen();
        int x = loc.x;
        int y = loc.y + anchor.getHeight();

        // Clamp to screen
        GraphicsConfiguration gc = anchor.getGraphicsConfiguration();
        if (gc != null) {
            Rectangle screen = gc.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            int maxY = screen.y + screen.height - insets.bottom;
            if (y + popup.getHeight() > maxY) {
                y = maxY - popup.getHeight();
            }
            int maxX = screen.x + screen.width - insets.right;
            if (x + popup.getWidth() > maxX) {
                x = maxX - popup.getWidth();
            }
        }

        popup.setLocation(x, y);
        popup.setVisible(true);
    }
}
