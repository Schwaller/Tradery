package com.tradery.symbols.ui;

import com.tradery.symbols.model.SymbolEntry;
import com.tradery.symbols.service.SymbolService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Modal dialog that wraps SymbolChooserPanel.
 * Returns the selected SymbolEntry, or null if cancelled.
 */
public class SymbolChooserDialog extends JDialog {

    private SymbolEntry result;

    private SymbolChooserDialog(Component parent, String title, SymbolService service) {
        super(SwingUtilities.getWindowAncestor(parent), title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        SymbolChooserPanel panel = new SymbolChooserPanel(service);
        panel.setSelectionCallback(entry -> {
            result = entry;
            dispose();
        });

        // Cancel on Escape
        getRootPane().registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Bottom buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton selectBtn = new JButton("Select");
        selectBtn.addActionListener(e -> {
            SymbolEntry selected = panel.getSelectedEntry();
            if (selected != null) {
                result = selected;
                dispose();
            }
        });
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        buttonPanel.add(selectBtn);
        buttonPanel.add(cancelBtn);

        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        setSize(700, 500);
        setLocationRelativeTo(parent);
    }

    /**
     * Show the symbol chooser dialog. Returns the selected entry, or null if cancelled.
     */
    public static SymbolEntry showDialog(Component parent, String title, SymbolService service) {
        SymbolChooserDialog dialog = new SymbolChooserDialog(parent, title, service);
        dialog.setVisible(true);
        return dialog.result;
    }
}
