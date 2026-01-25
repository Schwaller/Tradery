package com.tradery.forge.ui.controls;

import com.tradery.forge.ui.BadgePanel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A modern badge-style panel for displaying a list of selected items with add/remove functionality.
 *
 * Layout when empty:
 *   ┌─────────────────────────┐
 *   │ All Phases          [+] │
 *   └─────────────────────────┘
 *
 * Layout with selections:
 *   ┌─────────────────────────┐
 *   │ Phases              [+] │
 *   ├─────────────────────────┤
 *   │ + Uptrend           [−] │
 *   ├─────────────────────────┤
 *   │ − Weekend           [−] │
 *   └─────────────────────────┘
 */
public class BadgeListPanel extends JPanel {

    private final String label;
    private final Set<String> requiredIds = new LinkedHashSet<>();
    private final Set<String> excludedIds = new LinkedHashSet<>();
    private Runnable onChange;
    private Consumer<JPopupMenu> popupBuilder;
    private Supplier<String> nameResolver;

    private final JPanel contentPanel;
    private java.beans.PropertyChangeListener lafListener;

    public BadgeListPanel(String label) {
        this.label = label;

        setLayout(new BorderLayout());
        setOpaque(false);

        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);

        add(contentPanel, BorderLayout.CENTER);

        rebuildUI();
    }

    /**
     * Get the separator/border color based on current theme.
     */
    private Color getSeparatorColor() {
        Color borderColor = UIManager.getColor("Component.borderColor");
        return borderColor != null ? borderColor : new Color(60, 60, 65);
    }

    /**
     * Get the color for required items (positive/include).
     */
    private Color getRequiredColor() {
        return BadgePanel.isDarkTheme()
            ? new Color(100, 180, 100)   // Brighter green for dark themes
            : new Color(50, 120, 50);    // Darker green for light themes
    }

    /**
     * Get the color for excluded items (negative/exclude).
     */
    private Color getExcludedColor() {
        return BadgePanel.isDarkTheme()
            ? new Color(220, 100, 100)   // Brighter red for dark themes
            : new Color(160, 60, 60);    // Darker red for light themes
    }

    @Override
    public void addNotify() {
        super.addNotify();
        // Listen for LAF changes to update colors
        if (lafListener == null) {
            lafListener = evt -> {
                if ("lookAndFeel".equals(evt.getPropertyName())) {
                    SwingUtilities.invokeLater(this::rebuildUI);
                }
            };
            UIManager.addPropertyChangeListener(lafListener);
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (lafListener != null) {
            UIManager.removePropertyChangeListener(lafListener);
            lafListener = null;
        }
    }

    /**
     * Set a callback to build the add popup menu.
     * The consumer receives a JPopupMenu to populate.
     */
    public void setPopupBuilder(Consumer<JPopupMenu> popupBuilder) {
        this.popupBuilder = popupBuilder;
    }

    /**
     * Set a function to resolve item IDs to display names.
     */
    public void setNameResolver(Supplier<String> nameResolver) {
        this.nameResolver = nameResolver;
    }

    /**
     * Set a name resolver that takes an ID and returns a display name.
     */
    public void setNameResolver(java.util.function.Function<String, String> resolver) {
        this.nameResolver = null; // Clear simple resolver
        this.idToNameResolver = resolver;
    }

    private java.util.function.Function<String, String> idToNameResolver;

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public void setItems(List<String> required, List<String> excluded) {
        requiredIds.clear();
        excludedIds.clear();
        if (required != null) requiredIds.addAll(required);
        if (excluded != null) excludedIds.addAll(excluded);
        rebuildUI();
    }

    public List<String> getRequiredIds() {
        return new ArrayList<>(requiredIds);
    }

    public List<String> getExcludedIds() {
        return new ArrayList<>(excludedIds);
    }

    public void addRequired(String id) {
        if (!requiredIds.contains(id) && !excludedIds.contains(id)) {
            requiredIds.add(id);
            rebuildUI();
            fireChange();
        }
    }

    public void addExcluded(String id) {
        if (!requiredIds.contains(id) && !excludedIds.contains(id)) {
            excludedIds.add(id);
            rebuildUI();
            fireChange();
        }
    }

    public void clearAll() {
        if (!requiredIds.isEmpty() || !excludedIds.isEmpty()) {
            requiredIds.clear();
            excludedIds.clear();
            rebuildUI();
            fireChange();
        }
    }

    public boolean hasSelections() {
        return !requiredIds.isEmpty() || !excludedIds.isEmpty();
    }

    public boolean contains(String id) {
        return requiredIds.contains(id) || excludedIds.contains(id);
    }

    private void rebuildUI() {
        contentPanel.removeAll();

        boolean hasItems = !requiredIds.isEmpty() || !excludedIds.isEmpty();

        // Header row
        JPanel headerRow = createRow(
            hasItems ? label : "All " + label,
            null,
            true,
            false,
            this::showAddPopup
        );
        contentPanel.add(headerRow);

        // Item rows
        for (String id : requiredIds) {
            contentPanel.add(createSeparator());
            String name = resolveName(id);
            JPanel row = createRow(name, id, false, true, () -> removeItem(id, true));
            contentPanel.add(row);
        }

        for (String id : excludedIds) {
            contentPanel.add(createSeparator());
            String name = resolveName(id);
            JPanel row = createRow(name, id, false, false, () -> removeItem(id, false));
            contentPanel.add(row);
        }

        revalidate();
        repaint();
    }

    private String resolveName(String id) {
        if (idToNameResolver != null) {
            String name = idToNameResolver.apply(id);
            return name != null ? name : id;
        }
        return id;
    }

    private JPanel createRow(String text, String id, boolean isHeader, boolean isRequired, Runnable buttonAction) {
        JPanel row = new JPanel(new BorderLayout(4, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Color sepColor = getSeparatorColor();
                // Draw top border for first row, handled by separator for others
                if (isHeader) {
                    g.setColor(sepColor);
                    // Top border
                    g.drawLine(0, 0, getWidth(), 0);
                    // Left border
                    g.drawLine(0, 0, 0, getHeight() - 1);
                    // Right border
                    g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight() - 1);
                    // Bottom border if no items
                    if (!hasSelections()) {
                        g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                    }
                } else {
                    // Left and right borders for item rows
                    g.setColor(sepColor);
                    g.drawLine(0, 0, 0, getHeight() - 1);
                    g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight() - 1);
                }
            }
        };
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));

        // Label
        JLabel label = new JLabel();
        label.setFont(label.getFont().deriveFont(11f));

        if (isHeader) {
            label.setText(text);
            label.setForeground(UIManager.getColor("Label.foreground"));
        } else {
            String prefix = isRequired ? "+" : "−";
            label.setText(prefix + "  " + text);
            label.setForeground(isRequired ? getRequiredColor() : getExcludedColor());
        }

        row.add(label, BorderLayout.CENTER);

        // Button
        JButton button = new JButton(isHeader ? "+" : "−");
        button.setFont(button.getFont().deriveFont(Font.BOLD, 10f));
        button.setMargin(new Insets(0, 5, 0, 5));
        button.setFocusPainted(false);
        button.addActionListener(e -> buttonAction.run());

        if (isHeader) {
            button.setToolTipText("Add " + this.label.toLowerCase());
        } else {
            button.setToolTipText("Remove");
        }

        row.add(button, BorderLayout.EAST);

        return row;
    }

    private JPanel createSeparator() {
        JPanel sep = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(getSeparatorColor());
                // Horizontal line
                g.drawLine(0, 0, getWidth(), 0);
                // Left border continuation
                g.drawLine(0, 0, 0, getHeight());
                // Right border continuation
                g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(super.getPreferredSize().width, 1);
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, 1);
            }
        };
        sep.setOpaque(false);
        return sep;
    }

    private JPanel createBottomBorder() {
        JPanel bottom = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(getSeparatorColor());
                g.drawLine(0, 0, getWidth(), 0);
                // Close left border
                g.drawLine(0, 0, 0, 0);
                // Close right border
                g.drawLine(getWidth() - 1, 0, getWidth() - 1, 0);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(super.getPreferredSize().width, 1);
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, 1);
            }
        };
        bottom.setOpaque(false);
        return bottom;
    }

    private void showAddPopup() {
        if (popupBuilder == null) return;

        JPopupMenu popup = new JPopupMenu();
        popupBuilder.accept(popup);

        // Find the header row (first component)
        if (contentPanel.getComponentCount() > 0) {
            Component header = contentPanel.getComponent(0);
            popup.show(header, 0, header.getHeight());
        }
    }

    private void removeItem(String id, boolean fromRequired) {
        if (fromRequired) {
            requiredIds.remove(id);
        } else {
            excludedIds.remove(id);
        }
        rebuildUI();
        fireChange();
    }

    private void fireChange() {
        if (onChange != null) {
            onChange.run();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);

        // Draw bottom border after all children
        if (hasSelections()) {
            g.setColor(getSeparatorColor());
            int bottom = contentPanel.getHeight() - 1;
            g.drawLine(0, bottom, getWidth(), bottom);
        }
    }
}
