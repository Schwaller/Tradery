package com.tradery.news.ui;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Launcher window for the Intelligence app.
 * Shows document list with create/open/delete. Manages open document windows.
 */
public class IntelLauncherFrame extends JFrame {

    private final IntelDocumentManager documentManager;
    private final Map<String, IntelDocumentFrame> openWindows = new HashMap<>();
    private DefaultListModel<IntelDocumentManager.DocMeta> listModel;
    private JList<IntelDocumentManager.DocMeta> documentList;
    private JButton openButton;
    private JButton chatBtn;
    private JButton userAvatarBtn;

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private static IntelLauncherFrame instance;

    public IntelLauncherFrame(IntelDocumentManager documentManager) {
        super("Intelligence");
        instance = this;
        this.documentManager = documentManager;

        initializeFrame();
        initializeComponents();
        layoutComponents();
        loadDocuments();
    }

    private void initializeFrame() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        IntelConfig config = IntelConfig.get();
        int w = config.getLauncherWidth();
        int h = config.getLauncherHeight();
        setSize(w > 0 ? w : 400, h > 0 ? h : 520);
        setMinimumSize(new Dimension(300, 400));

        int x = config.getLauncherX();
        int y = config.getLauncherY();
        if (x >= 0 && y >= 0) {
            setLocation(x, y);
        } else {
            setLocationRelativeTo(null);
        }

        // Integrated title bar
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) { saveLauncherState(); }
            @Override
            public void componentResized(ComponentEvent e) { saveLauncherState(); }
            @Override
            public void componentHidden(ComponentEvent e) { exitIfNoWindows(); }
        });

        // macOS menu bar (shared across all Intel windows)
        setJMenuBar(IntelMenuBar.create(this));
    }

    private void saveLauncherState() {
        if (!isVisible()) return;
        IntelConfig config = IntelConfig.get();
        config.setLauncherWidth(getWidth());
        config.setLauncherHeight(getHeight());
        config.setLauncherX(getX());
        config.setLauncherY(getY());
        config.save();
    }

    private void initializeComponents() {
        listModel = new DefaultListModel<>();
        documentList = new JList<>(listModel);
        documentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        documentList.setCellRenderer(new DocumentCellRenderer());
        documentList.addListSelectionListener(e -> updateButtonStates());
        documentList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openDocument();
            }
        });

        // Context menu
        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem openItem = new JMenuItem("Open");
        openItem.addActionListener(e -> openDocument());
        JMenuItem renameItem = new JMenuItem("Rename");
        renameItem.addActionListener(e -> renameDocument());
        JMenuItem saveTemplateItem = new JMenuItem("Save as Template...");
        saveTemplateItem.addActionListener(e -> saveAsTemplate());
        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(e -> deleteDocument());

        contextMenu.add(openItem);
        contextMenu.addSeparator();
        contextMenu.add(renameItem);
        contextMenu.add(saveTemplateItem);
        contextMenu.addSeparator();
        contextMenu.add(deleteItem);

        documentList.setComponentPopupMenu(contextMenu);
        documentList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = documentList.locationToIndex(e.getPoint());
                    if (index >= 0) documentList.setSelectedIndex(index);
                }
            }
        });
    }

    private void layoutComponents() {
        JPanel contentPane = new JPanel(new BorderLayout());
        setContentPane(contentPane);

        // Header bar
        JPanel headerWrapper = new JPanel(new BorderLayout());
        JPanel headerBar = new JPanel(new GridBagLayout());
        headerBar.setPreferredSize(new Dimension(0, 52));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;

        // Left spacer (balances the avatar on the right)
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        headerBar.add(Box.createHorizontalGlue(), gbc);

        // Center: Title
        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel titleLabel = new JLabel("Plaiiin \u2014 Intelligence", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        headerBar.add(titleLabel, gbc);

        // Right: User avatar
        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.EAST;
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setOpaque(false);
        rightPanel.setBorder(new EmptyBorder(6, 0, 0, 0));
        rightPanel.add(createUserAvatar());
        headerBar.add(rightPanel, gbc);

        headerWrapper.add(headerBar, BorderLayout.CENTER);
        headerWrapper.add(new JSeparator(), BorderLayout.SOUTH);

        // List
        JScrollPane scrollPane = new JScrollPane(documentList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.add(scrollPane, BorderLayout.CENTER);
        listWrapper.add(new JSeparator(), BorderLayout.SOUTH);

        // Buttons
        JPanel buttonPanel = new JPanel(new BorderLayout());
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));

        chatBtn = new JButton("Chat");
        chatBtn.addActionListener(e -> openChat());
        chatBtn.setVisible(IntelDocumentFrame.getSharingService() != null);
        leftButtons.add(chatBtn);

        JButton friendsBtn = new JButton("Friends");
        friendsBtn.addActionListener(e -> showFriends());
        friendsBtn.setVisible(IntelDocumentFrame.getSharingService() != null);
        leftButtons.add(friendsBtn);

        JButton settingsBtn = new JButton("Settings");
        settingsBtn.addActionListener(e -> showSettings());
        leftButtons.add(settingsBtn);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton newBtn = new JButton("New Document...");
        newBtn.addActionListener(e -> createDocument());
        openButton = new JButton("Open");
        openButton.addActionListener(e -> openDocument());
        openButton.setEnabled(false);
        rightButtons.add(newBtn);
        rightButtons.add(openButton);

        buttonPanel.add(leftButtons, BorderLayout.WEST);
        buttonPanel.add(rightButtons, BorderLayout.EAST);

        JPanel mainContent = new JPanel(new BorderLayout());
        mainContent.add(listWrapper, BorderLayout.CENTER);
        mainContent.add(buttonPanel, BorderLayout.SOUTH);

        contentPane.add(headerWrapper, BorderLayout.NORTH);
        contentPane.add(mainContent, BorderLayout.CENTER);
    }

    private void loadDocuments() {
        listModel.clear();
        List<IntelDocumentManager.DocMeta> docs = documentManager.listDocuments();
        for (IntelDocumentManager.DocMeta doc : docs) {
            listModel.addElement(doc);
        }
        updateButtonStates();
    }

    private void updateButtonStates() {
        openButton.setEnabled(documentList.getSelectedValue() != null);
    }

    private void createDocument() {
        TemplateChooserDialog.Result result = TemplateChooserDialog.show(this);
        if (result == null) return;

        try {
            IntelDocumentManager.DocMeta meta = documentManager.createDocument(
                result.name(), result.template());
            loadDocuments();

            // Select and open the new document
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).id.equals(meta.id)) {
                    documentList.setSelectedIndex(i);
                    break;
                }
            }
            openDocument();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Failed to create document: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openDocument() {
        IntelDocumentManager.DocMeta selected = documentList.getSelectedValue();
        if (selected == null) return;

        // Focus existing window if already open
        IntelDocumentFrame existing = openWindows.get(selected.id);
        if (existing != null) {
            existing.toFront();
            existing.requestFocus();
            return;
        }

        Path docDir = documentManager.getDocumentDir(selected.id);
        DocumentServices services = DocumentServices.load(docDir);
        if (services == null) {
            // Fallback: create default services
            List<DocumentTemplate> builtIn = DocumentTemplate.builtIn();
            DocumentTemplate defaultTemplate = builtIn.stream()
                .filter(t -> t.getId().equals(selected.templateId))
                .findFirst().orElse(builtIn.get(0));
            services = DocumentServices.fromTemplate(defaultTemplate);
            services.save(docDir);
        }

        IntelDocumentFrame frame = new IntelDocumentFrame(
            selected.id, selected.name, docDir, services, this::onWindowClosed);
        openWindows.put(selected.id, frame);
        frame.setVisible(true);

        // Save last opened doc
        IntelConfig config = IntelConfig.get();
        config.setLastOpenedDocId(selected.id);
        config.save();
    }

    private void renameDocument() {
        IntelDocumentManager.DocMeta selected = documentList.getSelectedValue();
        if (selected == null) return;

        String newName = JOptionPane.showInputDialog(this, "Enter new name:", selected.name);
        if (newName != null && !newName.trim().isEmpty() && !newName.equals(selected.name)) {
            try {
                documentManager.renameDocument(selected.id, newName.trim());
                loadDocuments();

                // Update window title if open
                IntelDocumentFrame frame = openWindows.get(selected.id);
                if (frame != null) {
                    frame.setTitle(newName.trim() + " \u2014 Intelligence");
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to rename: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveAsTemplate() {
        IntelDocumentManager.DocMeta selected = documentList.getSelectedValue();
        if (selected == null) return;

        Path docDir = documentManager.getDocumentDir(selected.id);
        DocumentServices services = DocumentServices.load(docDir);
        if (services == null) return;

        JPanel form = new JPanel(new GridLayout(2, 2, 5, 5));
        JTextField nameField = new JTextField(selected.name);
        JTextField descField = new JTextField();
        form.add(new JLabel("Template name:"));
        form.add(nameField);
        form.add(new JLabel("Description:"));
        form.add(descField);

        int result = JOptionPane.showConfirmDialog(this, form, "Save as Template",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String name = nameField.getText().trim();
        if (name.isEmpty()) return;

        String id = name.toLowerCase().replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "") + "-" + System.currentTimeMillis() % 10000;

        DocumentTemplate template = new DocumentTemplate(
            id, name, descField.getText().trim(),
            services.getPanels(), services.getEnabledSourceIds()
        );

        try {
            template.saveAsUserTemplate();
            JOptionPane.showMessageDialog(this,
                "Template '" + name + "' saved successfully.",
                "Template Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Failed to save template: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteDocument() {
        IntelDocumentManager.DocMeta selected = documentList.getSelectedValue();
        if (selected == null) return;

        int result = JOptionPane.showConfirmDialog(this,
            "Delete document '" + selected.name + "'?\n\nThis will delete all data in this document.",
            "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            // Close window if open
            IntelDocumentFrame frame = openWindows.remove(selected.id);
            if (frame != null) frame.dispose();

            try {
                documentManager.deleteDocument(selected.id);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to delete: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
            loadDocuments();
        }
    }

    private void onWindowClosed(String docId) {
        openWindows.remove(docId);
        exitIfNoWindows();
    }

    private void exitIfNoWindows() {
        if (openWindows.isEmpty() && !isVisible()) {
            dispose();
            System.exit(0);
        }
    }

    private void openChat() {
        SharingService ss = IntelDocumentFrame.getSharingService();
        ChatStore cs = IntelDocumentFrame.getChatStore();
        if (ss == null || cs == null) return;
        ChatFrame.open(ss, cs, this);
        ChatFrame.setOnUnreadChanged(this::updateChatBadge);
    }

    private void updateChatBadge() {
        if (chatBtn == null) return;
        int unread = ChatFrame.getUnreadCount();
        SwingUtilities.invokeLater(() ->
            chatBtn.setText(unread > 0 ? "Chat (" + unread + ")" : "Chat"));
    }

    private void showFriends() {
        SharingService ss = IntelDocumentFrame.getSharingService();
        ChatStore cs = IntelDocumentFrame.getChatStore();
        if (ss == null) return;
        FriendsDialog dialog = new FriendsDialog(this, ss, cs);
        dialog.setVisible(true);
    }

    private void showSettings() {
        IntelSettingsDialog dialog = new IntelSettingsDialog(this);
        dialog.setVisible(true);
    }

    private void performSignIn() {
        SharingService ss = IntelDocumentFrame.getSharingService();
        if (ss == null) return;
        userAvatarBtn.setEnabled(false);
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return ss.login();
            }

            @Override
            protected void done() {
                userAvatarBtn.setEnabled(true);
                try {
                    boolean success = get();
                    if (success) {
                        String email = ss.getAuthenticatedEmail();
                        if (email != null) {
                            IntelConfig.get().setUserEmail(email);
                            IntelConfig.get().save();
                        }
                        userAvatarBtn.setToolTipText(email);
                        userAvatarBtn.repaint();
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    @Override
    public void dispose() {
        for (IntelDocumentFrame frame : openWindows.values()) {
            frame.dispose();
        }
        openWindows.clear();
        super.dispose();
    }

    public static IntelLauncherFrame getInstance() { return instance; }

    public void bringToFront() {
        SwingUtilities.invokeLater(() -> {
            setVisible(true);
            toFront();
            requestFocus();
        });
    }

    /** Open a specific document by ID (for API/restore). */
    public void openDocumentById(String docId) {
        for (int i = 0; i < listModel.size(); i++) {
            if (listModel.get(i).id.equals(docId)) {
                documentList.setSelectedIndex(i);
                openDocument();
                return;
            }
        }
    }

    // ==================== USER AVATAR ====================

    private JButton createUserAvatar() {
        userAvatarBtn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int size = Math.min(getWidth(), getHeight()) - 2;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;

                String email = IntelConfig.get().getUserEmail();
                boolean loggedIn = email != null && !email.isBlank();

                g2.setColor(loggedIn
                    ? new Color(76, 148, 255)
                    : UIManager.getColor("Button.default.borderColor") != null
                        ? UIManager.getColor("Button.default.borderColor")
                        : Color.GRAY);
                g2.fillOval(x, y, size, size);

                g2.setColor(Color.WHITE);
                if (loggedIn) {
                    String initial = email.substring(0, 1).toUpperCase();
                    g2.setFont(new Font("SansSerif", Font.BOLD, size / 2));
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = x + (size - fm.stringWidth(initial)) / 2;
                    int ty = y + (size - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(initial, tx, ty);
                } else {
                    // Clip to circle so body extends to bottom edge
                    Shape oldClip = g2.getClip();
                    g2.setClip(new java.awt.geom.Ellipse2D.Float(x, y, size, size));
                    int cx = x + size / 2;
                    int headR = size / 5;
                    g2.fillOval(cx - headR, y + size / 4, headR * 2, headR * 2);
                    int bodyW = size * 3 / 5;
                    g2.fillOval(cx - bodyW / 2, y + size * 11 / 20, bodyW, size);
                    g2.setClip(oldClip);
                }
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() { return new Dimension(32, 32); }
            @Override
            public Dimension getMinimumSize() { return getPreferredSize(); }
            @Override
            public Dimension getMaximumSize() { return getPreferredSize(); }
        };

        userAvatarBtn.setContentAreaFilled(false);
        userAvatarBtn.setBorderPainted(false);
        userAvatarBtn.setFocusPainted(false);
        userAvatarBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        String email = IntelConfig.get().getUserEmail();
        userAvatarBtn.setToolTipText(email != null && !email.isBlank()
            ? email : "Not logged in \u2014 click to set identity");

        userAvatarBtn.addActionListener(e -> {
            String avatarEmail = IntelConfig.get().getUserEmail();
            if (avatarEmail == null || avatarEmail.isBlank()) {
                JPopupMenu signInMenu = new JPopupMenu();
                JMenuItem signInItem = new JMenuItem("Sign In\u2026");
                signInItem.addActionListener(ev -> performSignIn());
                signInMenu.add(signInItem);
                signInMenu.show(userAvatarBtn, 0, userAvatarBtn.getHeight());
                return;
            }
            JPopupMenu menu = new JPopupMenu();
            JMenuItem emailItem = new JMenuItem(avatarEmail);
            emailItem.setEnabled(false);
            menu.add(emailItem);
            menu.addSeparator();
            JMenuItem friendsItem = new JMenuItem("Friends");
            friendsItem.addActionListener(ev -> showFriends());
            menu.add(friendsItem);
            JMenuItem chatItem = new JMenuItem("Chat\u2026");
            chatItem.addActionListener(ev -> openChat());
            menu.add(chatItem);
            menu.addSeparator();
            JMenuItem logoutItem = new JMenuItem("Sign Out");
            logoutItem.addActionListener(ev -> {
                SharingService ss = IntelDocumentFrame.getSharingService();
                if (ss != null) ss.logout();
                IntelConfig.get().setUserEmail(null);
                IntelConfig.get().save();
                userAvatarBtn.setToolTipText("Not logged in \u2014 click to set identity");
                userAvatarBtn.repaint();
            });
            menu.add(logoutItem);
            menu.show(userAvatarBtn, 0, userAvatarBtn.getHeight());
        });

        return userAvatarBtn;
    }

    // ==================== Cell Renderer ====================

    private class DocumentCellRenderer extends JPanel implements ListCellRenderer<IntelDocumentManager.DocMeta> {
        private final JLabel nameLabel = new JLabel();
        private final JLabel detailsLabel = new JLabel();

        DocumentCellRenderer() {
            setLayout(new BorderLayout(4, 0));
            setBorder(new EmptyBorder(8, 8, 8, 8));

            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 13f));

            detailsLabel.setFont(detailsLabel.getFont().deriveFont(11f));
            detailsLabel.setForeground(Color.GRAY);

            add(nameLabel, BorderLayout.NORTH);
            add(detailsLabel, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends IntelDocumentManager.DocMeta> list,
                IntelDocumentManager.DocMeta value, int index,
                boolean isSelected, boolean cellHasFocus) {

            nameLabel.setText(value.name);

            String templateBadge = value.templateId != null ? value.templateId : "unknown";
            String created = DATE_FORMAT.format(Instant.ofEpochMilli(value.createdAt));
            detailsLabel.setText(templateBadge + " \u2022 Created: " + created);

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                nameLabel.setForeground(list.getSelectionForeground());
                detailsLabel.setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                nameLabel.setForeground(list.getForeground());
                detailsLabel.setForeground(Color.GRAY);
            }

            setOpaque(true);
            return this;
        }
    }
}
