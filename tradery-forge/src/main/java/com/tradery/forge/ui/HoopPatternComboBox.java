package com.tradery.forge.ui;

import com.tradery.forge.ApplicationContext;
import com.tradery.forge.io.HoopPatternStore;
import com.tradery.core.model.HoopPattern;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Simple combo box for selecting a single hoop pattern.
 * Used in exit zones for exit hoop pattern selection.
 */
public class HoopPatternComboBox extends JPanel {

    private JComboBox<PatternItem> combo;
    private Runnable onChange;

    public HoopPatternComboBox() {
        setLayout(new BorderLayout());
        setOpaque(false);

        combo = new JComboBox<>();
        combo.setRenderer(new PatternRenderer());
        combo.addActionListener(e -> {
            if (onChange != null) {
                onChange.run();
            }
        });

        refreshPatterns();
        add(combo, BorderLayout.CENTER);
    }

    public void refreshPatterns() {
        PatternItem selected = (PatternItem) combo.getSelectedItem();
        String selectedId = selected != null ? selected.id : null;

        combo.removeAllItems();
        combo.addItem(new PatternItem(null, "(None)")); // None option

        HoopPatternStore store = ApplicationContext.getInstance().getHoopPatternStore();
        List<HoopPattern> patterns = store.loadAll();
        patterns.sort((a, b) -> {
            String n1 = a.getName() != null ? a.getName() : "";
            String n2 = b.getName() != null ? b.getName() : "";
            return n1.compareToIgnoreCase(n2);
        });

        for (HoopPattern pattern : patterns) {
            combo.addItem(new PatternItem(pattern.getId(), pattern.getName()));
        }

        // Restore selection
        if (selectedId != null) {
            setSelectedPatternId(selectedId);
        }
    }

    public void setSelectedPatternId(String patternId) {
        if (patternId == null) {
            combo.setSelectedIndex(0); // None
            return;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            PatternItem item = combo.getItemAt(i);
            if (patternId.equals(item.id)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        combo.setSelectedIndex(0); // None if not found
    }

    public String getSelectedPatternId() {
        PatternItem item = (PatternItem) combo.getSelectedItem();
        return item != null ? item.id : null;
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    private record PatternItem(String id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static class PatternRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof PatternItem item) {
                setText(item.name != null ? item.name : "(None)");
                if (item.id == null) {
                    setForeground(Color.GRAY);
                }
            }
            return this;
        }
    }
}
