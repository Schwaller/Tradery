package com.tradery.news.ui.coin;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Popup menu shown when dragging between two entity type boxes.
 * Lists existing relationship types that connect these types,
 * plus an option to create a new one.
 */
public class ConnectionTypePopup extends JPopupMenu {

    public ConnectionTypePopup(SchemaRegistry registry, SchemaType from, SchemaType to,
                                Consumer<SchemaType> onCreated) {
        // Header
        JLabel header = new JLabel("  Connect " + from.name() + " → " + to.name());
        header.setForeground(new Color(150, 150, 160));
        header.setFont(header.getFont().deriveFont(Font.ITALIC, 11f));
        header.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(header);
        addSeparator();

        // Existing relationship types between these entity types
        List<SchemaType> existing = registry.getRelationshipTypesBetween(from.id(), to.id());
        if (!existing.isEmpty()) {
            for (SchemaType rel : existing) {
                JMenuItem item = new JMenuItem(rel.name() + (rel.label() != null ? " (" + rel.label() + ")" : ""));
                item.setForeground(rel.color());
                item.addActionListener(e -> {
                    // Relationship type already exists - nothing to create
                    // Could be used to highlight or focus the existing type
                    if (onCreated != null) onCreated.accept(rel);
                });
                add(item);
            }
            addSeparator();
        }

        // New relationship type option
        JMenuItem newItem = new JMenuItem("New Relationship Type...");
        newItem.setFont(newItem.getFont().deriveFont(Font.BOLD));
        newItem.addActionListener(e -> createNewRelationshipType(registry, from, to, onCreated));
        add(newItem);
    }

    private void createNewRelationshipType(SchemaRegistry registry, SchemaType from, SchemaType to,
                                            Consumer<SchemaType> onCreated) {
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        JTextField nameField = new JTextField();
        JTextField labelField = new JTextField();

        panel.add(new JLabel("Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Label (verb):"));
        panel.add(labelField);

        int result = JOptionPane.showConfirmDialog(getInvoker(), panel, "New Relationship Type",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            if (name.isEmpty()) return;

            String id = name.toLowerCase().replaceAll("\\s+", "_");
            SchemaType type = new SchemaType(id, name,
                new Color(150 + (int)(Math.random() * 80), 150 + (int)(Math.random() * 80), 150 + (int)(Math.random() * 80)),
                SchemaType.KIND_RELATIONSHIP);
            type.setLabel(labelField.getText().trim());
            type.setFromTypeId(from.id());
            type.setToTypeId(to.id());
            type.setDisplayOrder(registry.relationshipTypes().size());

            // Position between the two entity types
            type.setErdX((from.erdX() + to.erdX()) / 2.0);
            type.setErdY((from.erdY() + to.erdY()) / 2.0 + 120);

            registry.save(type);
            if (onCreated != null) onCreated.accept(type);
        }
    }
}
