package com.tradery.news.ui;

import com.tradery.news.ui.coin.EntityStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

/**
 * Dialog for configuring document sharing — identity, visibility, governance, and members.
 */
public class ShareDialog extends JDialog {

    private final String docId;
    private final Path docDir;
    private final EntityStore entityStore;
    private final SharingService sharingService;
    private final IntelLogPanel logPanel;

    // Identity
    private JTextField emailField;

    // Visibility
    private ButtonGroup visibilityGroup;
    private JRadioButton localRadio, privateRadio;

    // Governance
    private ButtonGroup governanceGroup;
    private JRadioButton openRadio, adminRadio, votingRadio;
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
                       EntityStore entityStore, SharingService sharingService,
                       IntelLogPanel logPanel) {
        super(owner, "Share Document", ModalityType.APPLICATION_MODAL);
        this.docId = docId;
        this.docDir = docDir;
        this.entityStore = entityStore;
        this.sharingService = sharingService;
        this.logPanel = logPanel;

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
        JLabel titleLabel = new JLabel("Share Document", SwingConstants.CENTER);
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

        JPanel emailPanel = new JPanel(new BorderLayout(8, 0));
        emailPanel.setOpaque(false);
        emailPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        emailPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        emailPanel.add(new JLabel("Email:"), BorderLayout.WEST);
        emailField = new JTextField();
        String savedEmail = IntelConfig.get().getUserEmail();
        if (savedEmail != null) emailField.setText(savedEmail);
        emailPanel.add(emailField, BorderLayout.CENTER);
        form.add(emailPanel);
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
        adminRadio = createRadio("Admin Approved", "Owner reviews incoming changes");
        votingRadio = createRadio("Voting", "Members vote on changes");
        governanceGroup.add(openRadio);
        governanceGroup.add(adminRadio);
        governanceGroup.add(votingRadio);

        governancePanel.add(openRadio);
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

        // Wire radio listeners
        localRadio.addActionListener(e -> updateSections());
        privateRadio.addActionListener(e -> updateSections());
        votingRadio.addActionListener(e -> quorumSpinner.setEnabled(true));
        openRadio.addActionListener(e -> quorumSpinner.setEnabled(false));
        adminRadio.addActionListener(e -> quorumSpinner.setEnabled(false));
    }

    private void loadCurrentState() {
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
        if ("ADMIN_APPROVED".equals(govType)) {
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
        String email = emailField.getText().trim().toLowerCase();
        boolean wantLocal = localRadio.isSelected();

        if (!wantLocal && (email.isEmpty() || !email.contains("@"))) {
            JOptionPane.showMessageDialog(this,
                "Please enter your email address to enable sharing.",
                "Email Required", JOptionPane.WARNING_MESSAGE);
            emailField.requestFocus();
            return;
        }

        // Save email to config
        if (!email.isEmpty()) {
            IntelConfig.get().setUserEmail(email);
            IntelConfig.get().save();
        }

        String visibility = wantLocal ? "LOCAL" : "PRIVATE";
        String govType = adminRadio.isSelected() ? "ADMIN_APPROVED"
                       : votingRadio.isSelected() ? "VOTING" : "OPEN";
        double quorum = ((Number) quorumSpinner.getValue()).doubleValue() / 100.0;

        SharingService.SharingState currentState = sharingService.getState(docId);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                if (wantLocal && currentState.isShared()) {
                    sharingService.disableSharing(docId);
                    return "Sharing disabled";
                } else if (!wantLocal && !currentState.isShared()) {
                    sharingService.enableSharing(docId, visibility, govType, quorum,
                        email, docDir, entityStore);
                    return "Sharing enabled (" + visibility + ")";
                } else if (!wantLocal) {
                    sharingService.updateSharing(docId, visibility, govType, quorum, email);
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
