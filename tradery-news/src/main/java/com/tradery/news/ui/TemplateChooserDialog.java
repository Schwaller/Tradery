package com.tradery.news.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Modal dialog for choosing a template and naming a new document.
 * Shows built-in + user templates in a vertical list with descriptions.
 */
public class TemplateChooserDialog extends JDialog {

    private DocumentTemplate selectedTemplate;
    private String documentName;
    private boolean confirmed;

    public TemplateChooserDialog(Window owner) {
        super(owner, "New Document", ModalityType.APPLICATION_MODAL);
        setSize(440, 400);
        setLocationRelativeTo(owner);
        setResizable(false);

        List<DocumentTemplate> templates = DocumentTemplate.all();

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(new EmptyBorder(16, 20, 12, 20));

        // Template list
        DefaultListModel<DocumentTemplate> listModel = new DefaultListModel<>();
        templates.forEach(listModel::addElement);

        JList<DocumentTemplate> templateList = new JList<>(listModel);
        templateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        templateList.setSelectedIndex(0);
        templateList.setCellRenderer(new TemplateCellRenderer());

        JScrollPane scroll = new JScrollPane(templateList);
        scroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        content.add(scroll, BorderLayout.CENTER);

        // Name field
        JPanel namePanel = new JPanel(new BorderLayout(8, 0));
        namePanel.add(new JLabel("Name:"), BorderLayout.WEST);
        JTextField nameField = new JTextField();
        namePanel.add(nameField, BorderLayout.CENTER);

        // Pre-fill name from selected template
        if (!templates.isEmpty()) {
            nameField.setText(templates.get(0).getName());
        }
        templateList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                DocumentTemplate t = templateList.getSelectedValue();
                if (t != null) nameField.setText(t.getName());
            }
        });

        content.add(namePanel, BorderLayout.SOUTH);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setBorder(new EmptyBorder(8, 0, 4, 0));

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        JButton createBtn = new JButton("Create");
        createBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a document name.",
                    "Name Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            DocumentTemplate t = templateList.getSelectedValue();
            if (t == null) {
                JOptionPane.showMessageDialog(this, "Please select a template.",
                    "Template Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            selectedTemplate = t;
            documentName = name;
            confirmed = true;
            dispose();
        });

        // Delete user template button
        JButton deleteBtn = new JButton("Delete Template");
        deleteBtn.setEnabled(false);
        deleteBtn.addActionListener(e -> {
            DocumentTemplate t = templateList.getSelectedValue();
            if (t == null || t.isBuiltIn()) return;
            int result = JOptionPane.showConfirmDialog(this,
                "Delete template '" + t.getName() + "'?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                try {
                    DocumentTemplate.deleteUserTemplate(t.getId());
                    listModel.removeElement(t);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Failed to delete: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        templateList.addListSelectionListener(e -> {
            DocumentTemplate t = templateList.getSelectedValue();
            deleteBtn.setEnabled(t != null && !t.isBuiltIn());
        });

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftButtons.add(deleteBtn);

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.add(leftButtons, BorderLayout.WEST);
        buttonPanel.add(cancelBtn);
        buttonPanel.add(createBtn);
        bottomBar.add(buttonPanel, BorderLayout.EAST);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(content, BorderLayout.CENTER);
        getContentPane().add(bottomBar, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(createBtn);
    }

    public boolean isConfirmed() { return confirmed; }
    public DocumentTemplate getSelectedTemplate() { return selectedTemplate; }
    public String getDocumentName() { return documentName; }

    /** Show dialog and return result. Returns null if cancelled. */
    public static TemplateChooserDialog.Result show(Window owner) {
        TemplateChooserDialog dialog = new TemplateChooserDialog(owner);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            return new Result(dialog.getSelectedTemplate(), dialog.getDocumentName());
        }
        return null;
    }

    public record Result(DocumentTemplate template, String name) {}

    private static class TemplateCellRenderer extends JPanel implements ListCellRenderer<DocumentTemplate> {
        private final JLabel nameLabel = new JLabel();
        private final JLabel descLabel = new JLabel();
        private final JLabel badgeLabel = new JLabel();

        TemplateCellRenderer() {
            setLayout(new BorderLayout(6, 2));
            setBorder(new EmptyBorder(6, 8, 6, 8));

            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 13f));

            descLabel.setFont(descLabel.getFont().deriveFont(11f));
            descLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

            badgeLabel.setFont(badgeLabel.getFont().deriveFont(10f));
            badgeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

            JPanel topRow = new JPanel(new BorderLayout());
            topRow.setOpaque(false);
            topRow.add(nameLabel, BorderLayout.WEST);
            topRow.add(badgeLabel, BorderLayout.EAST);

            add(topRow, BorderLayout.NORTH);
            add(descLabel, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends DocumentTemplate> list,
                DocumentTemplate value, int index, boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(value.getName());
            descLabel.setText(value.getDescription());
            badgeLabel.setText(value.isBuiltIn() ? "Built-in" : "Custom");

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                nameLabel.setForeground(list.getSelectionForeground());
                descLabel.setForeground(list.getSelectionForeground());
                badgeLabel.setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                nameLabel.setForeground(list.getForeground());
                descLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
                badgeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            }

            setOpaque(true);
            return this;
        }
    }
}
