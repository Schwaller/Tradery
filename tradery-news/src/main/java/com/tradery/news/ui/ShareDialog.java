package com.tradery.news.ui;

import com.tradery.news.ui.coin.EntityStore;
import com.tradery.news.ui.coin.SchemaRegistry;
import com.tradery.news.ui.coin.SchemaType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * Dialog for configuring document sharing — identity, visibility, governance, and members.
 */
public class ShareDialog extends JDialog {

    private final String docId;
    private final Path docDir;
    private final EntityStore entityStore;
    private final SchemaRegistry schemaRegistry;
    private final SharingService sharingService;
    private final IntelLogPanel logPanel;
    private final DocumentServices services;

    // Identity
    private JPanel identityPanel;
    private JLabel emailDisplayLabel;
    private JButton signInBtn;
    private JButton signOutBtn;

    // Visibility
    private ButtonGroup visibilityGroup;
    private JRadioButton localRadio, privateRadio;

    // Governance
    private ButtonGroup governanceGroup;
    private JRadioButton openRadio, adminRadio, votingRadio, curatedRadio;
    private JSpinner quorumSpinner;
    private JPanel governancePanel;

    // Members
    private DefaultListModel<SharingService.Member> memberListModel;
    private JList<SharingService.Member> memberList;
    private JPanel membersPanel;

    // Status / Sync
    private JButton syncNowBtn;
    private JLabel statusLabel;

    public ShareDialog(JFrame owner, String docId, Path docDir,
                       EntityStore entityStore, SchemaRegistry schemaRegistry,
                       SharingService sharingService, IntelLogPanel logPanel) {
        super(owner, "Document Settings", ModalityType.APPLICATION_MODAL);
        this.docId = docId;
        this.docDir = docDir;
        this.entityStore = entityStore;
        this.schemaRegistry = schemaRegistry;
        this.sharingService = sharingService;
        this.logPanel = logPanel;
        this.services = (owner instanceof IntelDocumentFrame frame) ? frame.getDocumentServices() : null;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Integrated macOS title bar
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty("FlatLaf.macOS.windowButtonsSpacing", "large");

        initComponents();
        loadCurrentState();

        setSize(420, 600);
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        setContentPane(content);

        // Header
        JPanel headerWrapper = new JPanel(new BorderLayout());
        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setPreferredSize(new Dimension(0, 52));
        JLabel titleLabel = new JLabel("Document Settings", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        headerBar.add(titleLabel, BorderLayout.CENTER);
        headerWrapper.add(headerBar, BorderLayout.CENTER);
        headerWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        content.add(headerWrapper, BorderLayout.NORTH);

        // Main form
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(12, 20, 8, 20));

        // Panels section
        if (services != null) {
            JLabel panelsLabel = new JLabel("Panels");
            panelsLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
            panelsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(panelsLabel);
            form.add(Box.createVerticalStrut(6));
            form.add(createPanelsContent());
            form.add(Box.createVerticalStrut(12));
            form.add(createSeparator());
            form.add(Box.createVerticalStrut(12));
        }

        // Data Structure section — entity types + relation types side by side
        JPanel typesPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        typesPanel.setOpaque(false);
        typesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        typesPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

        // Entity types
        JPanel entityTypesCol = new JPanel(new BorderLayout(0, 4));
        entityTypesCol.setOpaque(false);
        JLabel entityTypesLabel = new JLabel("Entity Types");
        entityTypesLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        entityTypesCol.add(entityTypesLabel, BorderLayout.NORTH);

        DefaultListModel<String> entityTypeModel = new DefaultListModel<>();
        for (SchemaType t : schemaRegistry.entityTypes()) {
            entityTypeModel.addElement(t.name());
        }
        JList<String> entityTypeList = new JList<>(entityTypeModel);
        entityTypeList.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JScrollPane etScroll = new JScrollPane(entityTypeList);
        etScroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        entityTypesCol.add(etScroll, BorderLayout.CENTER);
        typesPanel.add(entityTypesCol);

        // Relation types
        JPanel relTypesCol = new JPanel(new BorderLayout(0, 4));
        relTypesCol.setOpaque(false);
        JLabel relTypesLabel = new JLabel("Relation Types");
        relTypesLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        relTypesCol.add(relTypesLabel, BorderLayout.NORTH);

        DefaultListModel<String> relTypeModel = new DefaultListModel<>();
        for (SchemaType t : schemaRegistry.relationshipTypes()) {
            relTypeModel.addElement(t.name());
        }
        JList<String> relTypeList = new JList<>(relTypeModel);
        relTypeList.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JScrollPane rtScroll = new JScrollPane(relTypeList);
        rtScroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        relTypesCol.add(rtScroll, BorderLayout.CENTER);
        typesPanel.add(relTypesCol);

        form.add(typesPanel);
        form.add(Box.createVerticalStrut(6));

        JButton editDataStructureBtn = new JButton("Edit Data Structure\u2026");
        editDataStructureBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        editDataStructureBtn.addActionListener(e -> {
            dispose();
            if (getOwner() instanceof IntelDocumentFrame docFrame) {
                docFrame.showDataStructureWindow();
            }
        });
        form.add(editDataStructureBtn);
        form.add(Box.createVerticalStrut(12));
        form.add(createSeparator());
        form.add(Box.createVerticalStrut(12));

        // Sharing sections — only shown when sharing service is available
        if (sharingService != null) {
            // Status
            statusLabel = new JLabel("Status: Local");
            statusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
            statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(statusLabel);
            form.add(Box.createVerticalStrut(12));
            form.add(createSeparator());
            form.add(Box.createVerticalStrut(12));

            // Your Identity section
            JLabel identityLabel = new JLabel("Your Identity");
            identityLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
            identityLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(identityLabel);
            form.add(Box.createVerticalStrut(6));

            identityPanel = new JPanel(new BorderLayout(8, 0));
            identityPanel.setOpaque(false);
            identityPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            identityPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

            emailDisplayLabel = new JLabel();
            emailDisplayLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

            signInBtn = new JButton("Sign In with Plaiiin");
            signInBtn.addActionListener(e -> onSignIn());

            signOutBtn = new JButton("Sign Out");
            signOutBtn.setFont(signOutBtn.getFont().deriveFont(11f));
            signOutBtn.addActionListener(e -> onSignOut());

            updateIdentityPanel();

            form.add(identityPanel);
            form.add(Box.createVerticalStrut(12));
            form.add(createSeparator());
            form.add(Box.createVerticalStrut(12));

            // Visibility section
            JLabel visLabel = new JLabel("Visibility");
            visLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
            visLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(visLabel);
            form.add(Box.createVerticalStrut(6));

            visibilityGroup = new ButtonGroup();
            localRadio = createRadio("Local", "Not shared — data stays on this machine");
            privateRadio = createRadio("Private", "Invite only — share with specific peers on your network");
            visibilityGroup.add(localRadio);
            visibilityGroup.add(privateRadio);
            form.add(localRadio);
            form.add(Box.createVerticalStrut(2));
            form.add(privateRadio);
            form.add(Box.createVerticalStrut(12));
            form.add(createSeparator());
            form.add(Box.createVerticalStrut(12));

            // Governance section
            JLabel govLabel = new JLabel("Governance");
            govLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
            govLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            governancePanel = new JPanel();
            governancePanel.setLayout(new BoxLayout(governancePanel, BoxLayout.Y_AXIS));
            governancePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            governancePanel.setOpaque(false);

            governanceGroup = new ButtonGroup();
            openRadio = createRadio("Open", "All members can edit freely");
            curatedRadio = createRadio("User Curated", "All data syncs \u2014 each user picks which entities to show");
            adminRadio = createRadio("Admin Approved", "Owner reviews incoming changes");
            votingRadio = createRadio("Voting", "Members vote on changes");
            governanceGroup.add(openRadio);
            governanceGroup.add(curatedRadio);
            governanceGroup.add(adminRadio);
            governanceGroup.add(votingRadio);

            governancePanel.add(openRadio);
            governancePanel.add(Box.createVerticalStrut(2));
            governancePanel.add(curatedRadio);
            governancePanel.add(Box.createVerticalStrut(2));
            governancePanel.add(adminRadio);
            governancePanel.add(Box.createVerticalStrut(2));
            governancePanel.add(votingRadio);

            JPanel quorumPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            quorumPanel.setOpaque(false);
            quorumPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            quorumPanel.add(Box.createHorizontalStrut(20));
            quorumPanel.add(new JLabel("Quorum:"));
            quorumSpinner = new JSpinner(new SpinnerNumberModel(51, 1, 100, 1));
            quorumSpinner.setPreferredSize(new Dimension(70, 24));
            quorumPanel.add(quorumSpinner);
            quorumPanel.add(new JLabel("%"));
            governancePanel.add(quorumPanel);

            form.add(govLabel);
            form.add(Box.createVerticalStrut(6));
            form.add(governancePanel);
            form.add(Box.createVerticalStrut(12));
            form.add(createSeparator());
            form.add(Box.createVerticalStrut(12));

            // Members section
            JLabel membersLabel = new JLabel("Members");
            membersLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
            membersLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(membersLabel);
            form.add(Box.createVerticalStrut(6));

            membersPanel = new JPanel(new BorderLayout(0, 4));
            membersPanel.setOpaque(false);
            membersPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            memberListModel = new DefaultListModel<>();
            memberList = new JList<>(memberListModel);
            memberList.setCellRenderer(new MemberCellRenderer());
            memberList.setVisibleRowCount(4);
            JScrollPane memberScroll = new JScrollPane(memberList);
            memberScroll.setPreferredSize(new Dimension(0, 80));
            memberScroll.setBorder(UIManager.getBorder("ScrollPane.border"));
            membersPanel.add(memberScroll, BorderLayout.CENTER);

            JPanel memberButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            memberButtons.setOpaque(false);
            JButton addMemberBtn = new JButton("Add...");
            addMemberBtn.addActionListener(e -> onAddMember());
            JButton addFromFriendsBtn = new JButton("Add from Friends...");
            addFromFriendsBtn.addActionListener(e -> onAddFromFriends());
            JButton removeMemberBtn = new JButton("Remove");
            removeMemberBtn.addActionListener(e -> onRemoveMember());
            memberButtons.add(addMemberBtn);
            memberButtons.add(addFromFriendsBtn);
            memberButtons.add(removeMemberBtn);
            membersPanel.add(memberButtons, BorderLayout.SOUTH);

            form.add(membersPanel);
            form.add(Box.createVerticalStrut(12));

            // Sync Now button
            syncNowBtn = new JButton("Sync Now");
            syncNowBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            syncNowBtn.addActionListener(e -> onSyncNow());
            syncNowBtn.setVisible(false);
            form.add(syncNowBtn);
        }

        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        content.add(scrollPane, BorderLayout.CENTER);

        // Button bar
        JPanel buttonBar = new JPanel(new BorderLayout());
        buttonBar.add(new JSeparator(), BorderLayout.NORTH);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setBorder(new EmptyBorder(10, 16, 10, 16));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        JButton applyBtn = new JButton("Apply");
        applyBtn.addActionListener(e -> onApply());
        buttonPanel.add(cancelBtn);
        buttonPanel.add(applyBtn);
        buttonBar.add(buttonPanel, BorderLayout.CENTER);
        content.add(buttonBar, BorderLayout.SOUTH);

        // Wire radio listeners (only when sharing sections exist)
        if (sharingService != null) {
            localRadio.addActionListener(e -> updateSections());
            privateRadio.addActionListener(e -> updateSections());
            votingRadio.addActionListener(e -> quorumSpinner.setEnabled(true));
            openRadio.addActionListener(e -> quorumSpinner.setEnabled(false));
            curatedRadio.addActionListener(e -> quorumSpinner.setEnabled(false));
            adminRadio.addActionListener(e -> quorumSpinner.setEnabled(false));
        }
    }

    private void loadCurrentState() {
        if (sharingService == null) return;
        SharingService.SharingState state = sharingService.getState(docId);

        if (state.isShared()) {
            statusLabel.setText("Status: Shared (" + state.connectedPeerCount() + " peers connected)");
            privateRadio.setSelected(true);
            syncNowBtn.setVisible(true);
        } else {
            statusLabel.setText("Status: Local");
            localRadio.setSelected(true);
            syncNowBtn.setVisible(false);
        }

        // Governance
        String govType = state.governanceType();
        if ("USER_CURATED".equals(govType)) {
            curatedRadio.setSelected(true);
        } else if ("ADMIN_APPROVED".equals(govType)) {
            adminRadio.setSelected(true);
        } else if ("VOTING".equals(govType)) {
            votingRadio.setSelected(true);
            quorumSpinner.setValue((int) (state.votingQuorum() * 100));
        } else {
            openRadio.setSelected(true);
        }

        // Members
        loadMembers();

        updateSections();
    }

    private void loadMembers() {
        memberListModel.clear();
        List<SharingService.Member> members = sharingService.getMembers(docId);
        for (SharingService.Member m : members) {
            memberListModel.addElement(m);
        }
    }

    private void updateSections() {
        boolean shared = privateRadio.isSelected();
        setComponentsEnabled(governancePanel, shared);
        quorumSpinner.setEnabled(shared && votingRadio.isSelected());
        setComponentsEnabled(membersPanel, shared);
    }

    private void setComponentsEnabled(Container container, boolean enabled) {
        for (Component c : container.getComponents()) {
            c.setEnabled(enabled);
            if (c instanceof Container child) {
                setComponentsEnabled(child, enabled);
            }
        }
    }

    private void onAddMember() {
        String email = JOptionPane.showInputDialog(this, "Enter member email address:", "Add Member",
            JOptionPane.PLAIN_MESSAGE);
        if (email == null || email.trim().isEmpty()) return;
        email = email.trim().toLowerCase();

        if (!email.contains("@")) {
            JOptionPane.showMessageDialog(this, "Please enter a valid email address.",
                "Invalid Email", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            sharingService.addMember(docId, email, "MEMBER");
            loadMembers();
            logPanel.success("Added member: " + email);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onAddFromFriends() {
        // Get friends not already in the member list
        java.util.Set<String> existingEmails = new java.util.HashSet<>();
        for (int i = 0; i < memberListModel.size(); i++) {
            existingEmails.add(memberListModel.get(i).email().toLowerCase());
        }

        java.util.List<FriendConfig> available = new java.util.ArrayList<>();
        for (FriendConfig f : IntelConfig.get().getFriends()) {
            if (!existingEmails.contains(f.getEmail().toLowerCase())) {
                available.add(f);
            }
        }

        if (available.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                available.isEmpty() && IntelConfig.get().getFriends().isEmpty()
                    ? "No friends yet. Add friends via the avatar menu."
                    : "All friends are already members.",
                "Add from Friends", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Show checkboxes
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        java.util.List<JCheckBox> checkBoxes = new java.util.ArrayList<>();
        for (FriendConfig f : available) {
            JCheckBox cb = new JCheckBox(f.label() + " (" + f.getEmail() + ")");
            cb.putClientProperty("email", f.getEmail());
            checkBoxes.add(cb);
            panel.add(cb);
        }

        int result = JOptionPane.showConfirmDialog(this, new JScrollPane(panel),
            "Select Friends to Add", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        for (JCheckBox cb : checkBoxes) {
            if (cb.isSelected()) {
                String email = (String) cb.getClientProperty("email");
                try {
                    sharingService.addMember(docId, email, "MEMBER");
                    logPanel.success("Added friend: " + email);
                } catch (Exception e) {
                    logPanel.error("Failed to add " + email + ": " + e.getMessage());
                }
            }
        }
        loadMembers();
    }

    private void onRemoveMember() {
        SharingService.Member selected = memberList.getSelectedValue();
        if (selected == null) return;

        if ("OWNER".equals(selected.role())) {
            JOptionPane.showMessageDialog(this, "Cannot remove the owner.",
                "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            sharingService.removeMember(docId, selected.email());
            loadMembers();
            logPanel.info("Removed member: " + selected.email());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onSyncNow() {
        syncNowBtn.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                sharingService.syncNow(docId);
                return null;
            }

            @Override
            protected void done() {
                syncNowBtn.setEnabled(true);
                logPanel.info("Sync requested");
            }
        }.execute();
    }

    private void onApply() {
        if (sharingService == null) {
            dispose();
            return;
        }
        boolean wantLocal = localRadio.isSelected();
        String email = sharingService.getAuthenticatedEmail();

        if (!wantLocal && (email == null || email.isBlank())) {
            JOptionPane.showMessageDialog(this,
                "Please sign in to enable sharing.",
                "Sign In Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String visibility = wantLocal ? "LOCAL" : "PRIVATE";
        String govType = curatedRadio.isSelected() ? "USER_CURATED"
                       : adminRadio.isSelected() ? "ADMIN_APPROVED"
                       : votingRadio.isSelected() ? "VOTING" : "OPEN";
        double quorum = ((Number) quorumSpinner.getValue()).doubleValue() / 100.0;

        SharingService.SharingState currentState = sharingService.getState(docId);
        String ownerEmail = email != null ? email : "";

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                if (wantLocal && currentState.isShared()) {
                    sharingService.disableSharing(docId);
                    return "Sharing disabled";
                } else if (!wantLocal && !currentState.isShared()) {
                    sharingService.enableSharing(docId, visibility, govType, quorum,
                        ownerEmail, docDir, entityStore);
                    return "Sharing enabled (" + visibility + ")";
                } else if (!wantLocal) {
                    sharingService.updateSharing(docId, visibility, govType, quorum, ownerEmail);
                    return "Sharing settings updated";
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    if (result != null) {
                        logPanel.success(result);
                    }
                    dispose();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    logPanel.error("Sharing failed: " + cause.getMessage());
                    JOptionPane.showMessageDialog(ShareDialog.this,
                        "Failed to update sharing: " + cause.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void updateIdentityPanel() {
        identityPanel.removeAll();
        String email = sharingService.getAuthenticatedEmail();
        if (email != null && !email.isBlank()) {
            emailDisplayLabel.setText(email);
            identityPanel.add(emailDisplayLabel, BorderLayout.CENTER);
            identityPanel.add(signOutBtn, BorderLayout.EAST);
        } else {
            identityPanel.add(signInBtn, BorderLayout.CENTER);
        }
        identityPanel.revalidate();
        identityPanel.repaint();
    }

    private void onSignIn() {
        signInBtn.setEnabled(false);
        signInBtn.setText("Signing in...");
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return sharingService.login();
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        logPanel.success("Signed in as " + sharingService.getAuthenticatedEmail());
                    } else {
                        logPanel.error("Sign in failed or was cancelled");
                    }
                } catch (Exception e) {
                    logPanel.error("Sign in failed: " + e.getMessage());
                }
                signInBtn.setEnabled(true);
                signInBtn.setText("Sign In with Plaiiin");
                updateIdentityPanel();
            }
        }.execute();
    }

    private void onSignOut() {
        sharingService.logout();
        updateIdentityPanel();
        logPanel.info("Signed out");
    }

    private JRadioButton createRadio(String label, String description) {
        JRadioButton radio = new JRadioButton(
            "<html><b>" + label + "</b><br><span style='color:gray;font-size:10px'>"
            + description + "</span></html>");
        radio.setAlignmentX(Component.LEFT_ALIGNMENT);
        radio.setOpaque(false);
        return radio;
    }

    private JSeparator createSeparator() {
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    // --- Panels ---

    private JPanel createPanelsContent() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        DefaultListModel<PanelConfig> listModel = new DefaultListModel<>();
        JList<PanelConfig> panelList = new JList<>(listModel);
        panelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        Runnable loadPanels = () -> {
            listModel.clear();
            for (PanelConfig p : services.getPanels()) {
                listModel.addElement(p);
            }
        };
        loadPanels.run();

        panelList.setCellRenderer(new PanelCellRenderer());

        JScrollPane scroll = new JScrollPane(panelList);
        scroll.setPreferredSize(new Dimension(0, 120));
        scroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        panel.add(scroll, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonPanel.setOpaque(false);
        JButton addBtn = new JButton("Add\u2026");
        JButton editBtn = new JButton("Edit\u2026");
        JButton removeBtn = new JButton("Remove");
        JButton moveUpBtn = new JButton("Move Up");
        JButton moveDownBtn = new JButton("Move Down");

        addBtn.addActionListener(e -> {
            PanelConfig newPanel = showPanelEditor(null);
            if (newPanel != null) {
                services.getPanels().add(newPanel);
                services.save(docDir);
                loadPanels.run();
                notifyPanelsChanged();
            }
        });

        editBtn.addActionListener(e -> {
            PanelConfig selected = panelList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Select a panel to edit.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            PanelConfig edited = showPanelEditor(selected);
            if (edited != null) {
                selected.setName(edited.getName());
                selected.setMaxArticles(edited.getMaxArticles());
                selected.setBands(edited.getBands());
                selected.setEntityTypeFilter(edited.getEntityTypeFilter());
                selected.setEntitySourceFilter(edited.getEntitySourceFilter());
                selected.setRelationshipTypeFilter(edited.getRelationshipTypeFilter());
                selected.setShowLabels(edited.isShowLabels());
                selected.setShowConnections(edited.isShowConnections());
                services.save(docDir);
                loadPanels.run();
                notifyPanelsChanged();
            }
        });

        removeBtn.addActionListener(e -> {
            PanelConfig selected = panelList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Select a panel to remove.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (services.getPanels().size() <= 1) {
                JOptionPane.showMessageDialog(this, "Cannot remove the last panel.", "Cannot Remove", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int result = JOptionPane.showConfirmDialog(this,
                "Remove panel '" + selected.getName() + "'?",
                "Confirm Remove", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                services.getPanels().removeIf(p -> p.getId().equals(selected.getId()));
                services.save(docDir);
                loadPanels.run();
                notifyPanelsChanged();
            }
        });

        moveUpBtn.addActionListener(e -> {
            int idx = panelList.getSelectedIndex();
            if (idx <= 0) return;
            List<PanelConfig> panels = services.getPanels();
            Collections.swap(panels, idx, idx - 1);
            services.save(docDir);
            loadPanels.run();
            panelList.setSelectedIndex(idx - 1);
            notifyPanelsChanged();
        });

        moveDownBtn.addActionListener(e -> {
            int idx = panelList.getSelectedIndex();
            List<PanelConfig> panels = services.getPanels();
            if (idx < 0 || idx >= panels.size() - 1) return;
            Collections.swap(panels, idx, idx + 1);
            services.save(docDir);
            loadPanels.run();
            panelList.setSelectedIndex(idx + 1);
            notifyPanelsChanged();
        });

        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(removeBtn);
        buttonPanel.add(moveUpBtn);
        buttonPanel.add(moveDownBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private PanelConfig showPanelEditor(PanelConfig existing) {
        JDialog dialog = new JDialog(this, existing != null ? "Edit Panel" : "Add Panel", true);
        dialog.setSize(420, 520);
        dialog.setLocationRelativeTo(this);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.anchor = GridBagConstraints.WEST;
        labelGbc.insets = new Insets(4, 0, 4, 8);

        GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.fill = GridBagConstraints.HORIZONTAL;
        fieldGbc.weightx = 1.0;
        fieldGbc.insets = new Insets(4, 0, 4, 0);

        int row = 0;

        // Name
        labelGbc.gridx = 0; labelGbc.gridy = row;
        formPanel.add(new JLabel("Name:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JTextField nameField = new JTextField(existing != null ? existing.getName() : "");
        formPanel.add(nameField, fieldGbc);

        // Type
        labelGbc.gridx = 0; labelGbc.gridy = row;
        formPanel.add(new JLabel("Type:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"News Map", "Coin Graph"});
        if (existing != null) {
            typeCombo.setSelectedIndex(existing.getType() == PanelConfig.PanelType.NEWS_MAP ? 0 : 1);
            typeCombo.setEnabled(false);
        }
        formPanel.add(typeCombo, fieldGbc);

        // --- News Map settings ---
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel maxArticlesLabel = new JLabel("Max articles:");
        formPanel.add(maxArticlesLabel, labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JComboBox<String> maxArticlesCombo = new JComboBox<>(new String[]{"100", "250", "500", "1000", "2000"});
        maxArticlesCombo.setSelectedItem(String.valueOf(existing != null ? existing.getMaxArticles() : 500));
        formPanel.add(maxArticlesCombo, fieldGbc);

        // --- Bands editor (NEWS_MAP only) ---
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel bandsLabel = new JLabel("Bands:");
        labelGbc.anchor = GridBagConstraints.NORTHWEST;
        formPanel.add(bandsLabel, labelGbc);
        labelGbc.anchor = GridBagConstraints.WEST;

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        fieldGbc.fill = GridBagConstraints.BOTH;
        fieldGbc.weighty = 1.0;

        List<BandConfig> editableBands = new ArrayList<>(
            existing != null && existing.getBands() != null ? existing.getBands() : BandConfig.defaultNewsBands()
        );
        DefaultListModel<BandConfig> bandsModel = new DefaultListModel<>();
        editableBands.forEach(bandsModel::addElement);

        JList<BandConfig> bandsList = new JList<>(bandsModel);
        bandsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bandsList.setVisibleRowCount(4);

        JPanel bandsPanel = new JPanel(new BorderLayout(4, 4));
        bandsPanel.setOpaque(false);

        JScrollPane bandsScroll = new JScrollPane(bandsList);
        bandsScroll.setPreferredSize(new Dimension(0, 90));
        bandsScroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        bandsPanel.add(bandsScroll, BorderLayout.CENTER);

        JPanel bandsButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bandsButtons.setOpaque(false);
        JButton bandAddBtn = new JButton("Add");
        JButton bandEditBtn = new JButton("Edit");
        JButton bandRemoveBtn = new JButton("Remove");
        JButton bandUpBtn = new JButton("\u25B2");
        JButton bandDownBtn = new JButton("\u25BC");
        bandUpBtn.setMargin(new Insets(1, 4, 1, 4));
        bandDownBtn.setMargin(new Insets(1, 4, 1, 4));

        bandAddBtn.addActionListener(ev -> {
            BandConfig newBand = showBandEditor(dialog, null);
            if (newBand != null) bandsModel.addElement(newBand);
        });
        bandEditBtn.addActionListener(ev -> {
            int idx = bandsList.getSelectedIndex();
            if (idx < 0) return;
            BandConfig edited = showBandEditor(dialog, bandsModel.get(idx));
            if (edited != null) bandsModel.set(idx, edited);
        });
        bandRemoveBtn.addActionListener(ev -> {
            int idx = bandsList.getSelectedIndex();
            if (idx >= 0) bandsModel.remove(idx);
        });
        bandUpBtn.addActionListener(ev -> {
            int idx = bandsList.getSelectedIndex();
            if (idx > 0) {
                BandConfig item = bandsModel.remove(idx);
                bandsModel.add(idx - 1, item);
                bandsList.setSelectedIndex(idx - 1);
            }
        });
        bandDownBtn.addActionListener(ev -> {
            int idx = bandsList.getSelectedIndex();
            if (idx >= 0 && idx < bandsModel.size() - 1) {
                BandConfig item = bandsModel.remove(idx);
                bandsModel.add(idx + 1, item);
                bandsList.setSelectedIndex(idx + 1);
            }
        });

        bandsButtons.add(bandAddBtn);
        bandsButtons.add(bandEditBtn);
        bandsButtons.add(bandRemoveBtn);
        bandsButtons.add(bandUpBtn);
        bandsButtons.add(bandDownBtn);
        bandsPanel.add(bandsButtons, BorderLayout.SOUTH);

        formPanel.add(bandsPanel, fieldGbc);
        fieldGbc.fill = GridBagConstraints.HORIZONTAL;
        fieldGbc.weighty = 0;

        // --- Coin Graph settings ---
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel entityTypeLabel = new JLabel("Entity types:");
        formPanel.add(entityTypeLabel, labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;

        JPanel typesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        typesPanel.setOpaque(false);
        Map<String, JCheckBox> typeCheckboxes = new LinkedHashMap<>();
        Set<String> existingFilter = existing != null ? existing.getEntityTypeFilter() : null;
        for (SchemaType st : schemaRegistry.entityTypes()) {
            JCheckBox cb = new JCheckBox(st.name());
            cb.setSelected(existingFilter == null || existingFilter.contains(st.id()));
            typeCheckboxes.put(st.id(), cb);
            typesPanel.add(cb);
        }
        formPanel.add(typesPanel, fieldGbc);

        // Entity source filter
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel sourceFilterLabel = new JLabel("Sources:");
        formPanel.add(sourceFilterLabel, labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JComboBox<String> sourceCombo = new JComboBox<>(new String[]{"All", "CoinGecko only", "Manual only"});
        if (existing != null && existing.getEntitySourceFilter() != null) {
            Set<String> sf = existing.getEntitySourceFilter();
            if (sf.contains("coingecko") && !sf.contains("manual")) sourceCombo.setSelectedIndex(1);
            else if (sf.contains("manual") && !sf.contains("coingecko")) sourceCombo.setSelectedIndex(2);
        }
        formPanel.add(sourceCombo, fieldGbc);

        // Relationship type filter
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel relTypeLabel = new JLabel("Relationships:");
        formPanel.add(relTypeLabel, labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;

        JPanel relTypesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        relTypesPanel.setOpaque(false);
        Map<String, JCheckBox> relTypeCheckboxes = new LinkedHashMap<>();
        Set<String> existingRelFilter = existing != null ? existing.getRelationshipTypeFilter() : null;
        for (SchemaType rt : schemaRegistry.relationshipTypes()) {
            JCheckBox cb = new JCheckBox(rt.label() != null ? rt.label() : rt.name());
            cb.setSelected(existingRelFilter == null || existingRelFilter.contains(rt.id()));
            relTypeCheckboxes.put(rt.id(), cb);
            relTypesPanel.add(cb);
        }
        formPanel.add(relTypesPanel, fieldGbc);

        // --- Shared display settings ---
        labelGbc.gridx = 0; labelGbc.gridy = row;
        formPanel.add(new JLabel(), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JCheckBox showLabelsCheck = new JCheckBox("Show labels");
        showLabelsCheck.setSelected(existing == null || existing.isShowLabels());
        formPanel.add(showLabelsCheck, fieldGbc);

        labelGbc.gridx = 0; labelGbc.gridy = row;
        formPanel.add(new JLabel(), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JCheckBox showConnectionsCheck = new JCheckBox("Show connections");
        showConnectionsCheck.setSelected(existing == null || existing.isShowConnections());
        formPanel.add(showConnectionsCheck, fieldGbc);

        // Visibility based on type
        Runnable updateVisibility = () -> {
            boolean isNewsMap = typeCombo.getSelectedIndex() == 0;
            maxArticlesLabel.setVisible(isNewsMap);
            maxArticlesCombo.setVisible(isNewsMap);
            bandsLabel.setVisible(isNewsMap);
            bandsPanel.setVisible(isNewsMap);
            entityTypeLabel.setVisible(!isNewsMap);
            typesPanel.setVisible(!isNewsMap);
            sourceFilterLabel.setVisible(!isNewsMap);
            sourceCombo.setVisible(!isNewsMap);
            relTypeLabel.setVisible(!isNewsMap);
            relTypesPanel.setVisible(!isNewsMap);
            formPanel.revalidate();
        };
        typeCombo.addActionListener(e -> updateVisibility.run());
        updateVisibility.run();

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(8, 16, 12, 16));
        JButton cancelBtn = new JButton("Cancel");
        JButton saveBtn = new JButton("Save");

        PanelConfig[] result = {null};

        cancelBtn.addActionListener(e -> dialog.dispose());

        saveBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Name is required.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }

            PanelConfig pc = new PanelConfig();
            pc.setId(existing != null ? existing.getId() : generatePanelId(name));
            pc.setName(name);
            pc.setType(typeCombo.getSelectedIndex() == 0 ? PanelConfig.PanelType.NEWS_MAP : PanelConfig.PanelType.COIN_GRAPH);
            pc.setMaxArticles(Integer.parseInt((String) maxArticlesCombo.getSelectedItem()));
            pc.setShowLabels(showLabelsCheck.isSelected());
            pc.setShowConnections(showConnectionsCheck.isSelected());

            // Bands (NEWS_MAP)
            if (pc.getType() == PanelConfig.PanelType.NEWS_MAP && bandsModel.size() > 0) {
                List<BandConfig> savedBands = new ArrayList<>();
                for (int i = 0; i < bandsModel.size(); i++) savedBands.add(bandsModel.get(i));
                pc.setBands(savedBands);
            }

            // Entity type filter (null = all)
            if (pc.getType() == PanelConfig.PanelType.COIN_GRAPH) {
                boolean allChecked = typeCheckboxes.values().stream().allMatch(JCheckBox::isSelected);
                if (!allChecked) {
                    Set<String> filter = new LinkedHashSet<>();
                    typeCheckboxes.forEach((typeName, cb) -> {
                        if (cb.isSelected()) filter.add(typeName);
                    });
                    pc.setEntityTypeFilter(filter);
                }

                int sourceIdx = sourceCombo.getSelectedIndex();
                if (sourceIdx == 1) pc.setEntitySourceFilter(Set.of("coingecko"));
                else if (sourceIdx == 2) pc.setEntitySourceFilter(Set.of("manual"));

                boolean allRelChecked = relTypeCheckboxes.values().stream().allMatch(JCheckBox::isSelected);
                if (!allRelChecked) {
                    Set<String> relFilter = new LinkedHashSet<>();
                    relTypeCheckboxes.forEach((typeName, cb) -> {
                        if (cb.isSelected()) relFilter.add(typeName);
                    });
                    pc.setRelationshipTypeFilter(relFilter);
                }
            }

            result[0] = pc;
            dialog.dispose();
        });

        buttonPanel.add(cancelBtn);
        buttonPanel.add(saveBtn);

        dialog.setLayout(new BorderLayout());
        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setVisible(true);

        return result[0];
    }

    private BandConfig showBandEditor(Dialog owner, BandConfig existing) {
        JDialog dialog = new JDialog(owner, existing != null ? "Edit Band" : "Add Band", true);
        dialog.setSize(350, 350);
        dialog.setLocationRelativeTo(owner);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(4, 0, 4, 8);

        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets = new Insets(4, 0, 4, 0);

        int r = 0;

        // Name
        lc.gridx = 0; lc.gridy = r;
        form.add(new JLabel("Name:"), lc);
        fc.gridx = 1; fc.gridy = r++;
        JTextField nameField = new JTextField(existing != null ? existing.getName() : "");
        form.add(nameField, fc);

        // Filter
        lc.gridx = 0; lc.gridy = r;
        form.add(new JLabel("Filter:"), lc);
        fc.gridx = 1; fc.gridy = r++;
        JComboBox<String> filterCombo = new JComboBox<>();
        filterCombo.addItem("articles");
        Set<String> addedFilters = new HashSet<>();
        for (String builtIn : List.of("topic", "coin", "category", "tag")) {
            filterCombo.addItem(builtIn);
            addedFilters.add(builtIn);
        }
        for (SchemaType st : schemaRegistry.entityTypes()) {
            if (!addedFilters.contains(st.id())) {
                filterCombo.addItem(st.id());
            }
        }
        if (existing != null) filterCombo.setSelectedItem(existing.getFilter());
        form.add(filterCombo, fc);

        // Layout mode
        lc.gridx = 0; lc.gridy = r;
        form.add(new JLabel("Layout:"), lc);
        fc.gridx = 1; fc.gridy = r++;
        JComboBox<BandConfig.LayoutMode> layoutCombo = new JComboBox<>(BandConfig.LayoutMode.values());
        if (existing != null) layoutCombo.setSelectedItem(existing.getLayoutMode());
        form.add(layoutCombo, fc);

        // Weight
        lc.gridx = 0; lc.gridy = r;
        form.add(new JLabel("Weight:"), lc);
        fc.gridx = 1; fc.gridy = r++;
        JSpinner weightSpinner = new JSpinner(new SpinnerNumberModel(
            existing != null ? (int) existing.getWeight() : 1, 1, 10, 1));
        form.add(weightSpinner, fc);

        // Visible
        lc.gridx = 0; lc.gridy = r;
        form.add(new JLabel(), lc);
        fc.gridx = 1; fc.gridy = r++;
        JCheckBox visibleCheck = new JCheckBox("Visible", existing == null || existing.isVisible());
        form.add(visibleCheck, fc);

        // Max rows (HORIZONTAL_ROWS only)
        lc.gridx = 0; lc.gridy = r;
        JLabel maxRowsLabel = new JLabel("Max rows:");
        form.add(maxRowsLabel, lc);
        fc.gridx = 1; fc.gridy = r++;
        JSpinner maxRowsSpinner = new JSpinner(new SpinnerNumberModel(
            existing != null ? existing.getMaxRows() : 3, 1, 10, 1));
        form.add(maxRowsSpinner, fc);

        // Y field (MAPPED_TO_FIELD only)
        lc.gridx = 0; lc.gridy = r;
        JLabel yFieldLabel = new JLabel("Y field:");
        form.add(yFieldLabel, lc);
        fc.gridx = 1; fc.gridy = r++;
        JComboBox<String> yFieldCombo = new JComboBox<>();
        form.add(yFieldCombo, fc);

        // Update conditional fields
        Runnable updateFields = () -> {
            BandConfig.LayoutMode mode = (BandConfig.LayoutMode) layoutCombo.getSelectedItem();
            maxRowsLabel.setVisible(mode == BandConfig.LayoutMode.HORIZONTAL_ROWS);
            maxRowsSpinner.setVisible(mode == BandConfig.LayoutMode.HORIZONTAL_ROWS);
            yFieldLabel.setVisible(mode == BandConfig.LayoutMode.MAPPED_TO_FIELD);
            yFieldCombo.setVisible(mode == BandConfig.LayoutMode.MAPPED_TO_FIELD);

            if (mode == BandConfig.LayoutMode.MAPPED_TO_FIELD) {
                String filter = (String) filterCombo.getSelectedItem();
                String prev = (String) yFieldCombo.getSelectedItem();
                yFieldCombo.removeAllItems();
                for (String f : BandConfig.yFieldsForFilter(filter)) {
                    yFieldCombo.addItem(f);
                }
                if (prev != null) yFieldCombo.setSelectedItem(prev);
                if (existing != null && existing.getYField() != null && yFieldCombo.getSelectedItem() == null) {
                    yFieldCombo.setSelectedItem(existing.getYField());
                }
            }
            form.revalidate();
        };
        layoutCombo.addActionListener(e -> updateFields.run());
        filterCombo.addActionListener(e -> updateFields.run());
        updateFields.run();

        if (existing != null && existing.getYField() != null) {
            yFieldCombo.setSelectedItem(existing.getYField());
        }

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 16, 12, 16));
        JButton cancelBtn = new JButton("Cancel");
        JButton saveBtn = new JButton("Save");

        BandConfig[] result = {null};

        cancelBtn.addActionListener(e -> dialog.dispose());
        saveBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Name is required.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            BandConfig bc = new BandConfig(name,
                (String) filterCombo.getSelectedItem(),
                (BandConfig.LayoutMode) layoutCombo.getSelectedItem(),
                (Integer) weightSpinner.getValue());
            bc.setVisible(visibleCheck.isSelected());
            bc.setMaxRows((Integer) maxRowsSpinner.getValue());
            if (layoutCombo.getSelectedItem() == BandConfig.LayoutMode.MAPPED_TO_FIELD) {
                bc.setYField((String) yFieldCombo.getSelectedItem());
            }
            result[0] = bc;
            dialog.dispose();
        });

        buttons.add(cancelBtn);
        buttons.add(saveBtn);

        dialog.setLayout(new BorderLayout());
        dialog.add(form, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setVisible(true);

        return result[0];
    }

    private String generatePanelId(String name) {
        return name.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "")
            + "-" + System.currentTimeMillis() % 10000;
    }

    private void notifyPanelsChanged() {
        if (getOwner() instanceof IntelDocumentFrame frame) {
            frame.rebuildPanels();
        }
    }

    private static class PanelCellRenderer extends JPanel implements ListCellRenderer<PanelConfig> {
        private final JLabel nameLabel = new JLabel();
        private final JLabel typeLabel = new JLabel();

        PanelCellRenderer() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 6, 0));
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
            typeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            typeLabel.setFont(typeLabel.getFont().deriveFont(typeLabel.getFont().getSize2D() - 1f));
            add(nameLabel);
            add(typeLabel);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends PanelConfig> list, PanelConfig pc,
                                                       int index, boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(pc.getName());
            typeLabel.setText(pc.getType() == PanelConfig.PanelType.NEWS_MAP ? "News Map" : "Coin Graph");
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            nameLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return this;
        }
    }

    private static class MemberCellRenderer extends JPanel implements ListCellRenderer<SharingService.Member> {
        private final JLabel emailLabel = new JLabel();
        private final JLabel roleLabel = new JLabel();

        MemberCellRenderer() {
            setLayout(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(3, 6, 3, 6));
            emailLabel.setFont(emailLabel.getFont().deriveFont(12f));
            roleLabel.setFont(roleLabel.getFont().deriveFont(10f));
            roleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            add(emailLabel, BorderLayout.CENTER);
            add(roleLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends SharingService.Member> list,
                SharingService.Member member, int index, boolean isSelected, boolean cellHasFocus) {
            emailLabel.setText(member.email());
            roleLabel.setText(member.role().toLowerCase());
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            emailLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            setOpaque(true);
            return this;
        }
    }
}
