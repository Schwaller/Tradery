package com.tradery.forge.ui;

import com.tradery.core.model.Phase;
import com.tradery.forge.ui.charts.ChartConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Modal dialog for selecting which phases to display as overlays on the price chart.
 * Phases are grouped by category with colored swatches.
 */
public class PhaseOverlayPickerDialog extends JDialog {

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
    private boolean confirmed = false;

    public PhaseOverlayPickerDialog(Window owner, List<Phase> allPhases, List<String> selectedIds) {
        super(owner, "Phase Overlays", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Group phases by category
        Map<String, List<Phase>> grouped = new LinkedHashMap<>();
        for (Phase phase : allPhases) {
            String cat = phase.getCategory() != null ? phase.getCategory() : "Custom";
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(phase);
        }

        // Build checkbox list
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        Set<String> selected = new HashSet<>(selectedIds != null ? selectedIds : List.of());

        for (Map.Entry<String, List<Phase>> entry : grouped.entrySet()) {
            String category = entry.getKey();
            List<Phase> phases = entry.getValue();

            // Category header
            JLabel header = new JLabel(category);
            header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
            header.setBorder(new EmptyBorder(8, 0, 4, 0));
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            listPanel.add(header);

            for (Phase phase : phases) {
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

                // Color swatch
                Color baseColor = CATEGORY_COLORS.getOrDefault(category, CATEGORY_COLORS.get("Custom"));
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
                swatch.setPreferredSize(new Dimension(16, 16));
                swatch.setOpaque(false);

                JCheckBox cb = new JCheckBox(phase.getName() != null ? phase.getName() : phase.getId());
                cb.setSelected(selected.contains(phase.getId()));
                checkBoxes.put(phase.getId(), cb);

                row.add(swatch);
                row.add(cb);
                listPanel.add(row);
            }
        }

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setPreferredSize(new Dimension(400, 400));
        scroll.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));
        content.add(scroll, BorderLayout.CENTER);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> { confirmed = true; dispose(); });
        buttons.add(cancelBtn);
        buttons.add(okBtn);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public List<String> getSelectedPhaseIds() {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> entry : checkBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                result.add(entry.getKey());
            }
        }
        return result;
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
}
