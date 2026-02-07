package com.tradery.news.ui.coin;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemInfo;
import com.tradery.news.ui.IntelLogPanel;
import com.tradery.ui.controls.BorderlessScrollPane;
import com.tradery.ui.controls.SegmentedToggle;
import com.tradery.ui.controls.ThinSplitPane;
import com.tradery.ui.controls.ToolbarButton;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

/**
 * Dialog for searching and selecting related entities using AI.
 * Left panel shows entity types with search level buttons.
 * Right panel shows combined results from all searches.
 */
public class EntitySearchDialog extends JDialog {

    private final CoinEntity sourceEntity;
    private final EntityStore store;
    private final EntitySearchProcessor processor;
    private final EntityMatcher matcher;

    // Search levels
    private enum SearchLevel {
        GENERAL("General", "Quick AI search using known facts"),
        SPECIFIC("Specific", "Focused search with more detail"),
        DEEP("Deep", "Comprehensive search including web research");

        final String label;
        final String tooltip;

        SearchLevel(String label, String tooltip) {
            this.label = label;
            this.tooltip = tooltip;
        }
    }

    // Track search state per type and level
    private record TypeSearchState(
        Map<SearchLevel, JButton> buttons,
        Map<SearchLevel, Integer> resultCounts
    ) {}

    private final Map<CoinRelationship.Type, TypeSearchState> typeStates = new LinkedHashMap<>();
    private boolean generalSearchInProgress = false;
    private int activeInvestigations = 0;

    // All discovered entities from all searches
    private final List<EntitySearchProcessor.DiscoveredEntity> allResults = new ArrayList<>();
    private final Map<EntitySearchProcessor.DiscoveredEntity, CoinEntity> selectedMatches = new HashMap<>();
    private final Map<EntitySearchProcessor.DiscoveredEntity, JCheckBox> checkboxMap = new LinkedHashMap<>();

    // UI components
    private JPanel typesPanel;
    private JPanel resultsPanel;
    private BorderlessScrollPane resultsScroll;
    private JButton addSelectedBtn;
    private JLabel statusLabel;

    private static final String PREF_X = "entitySearchDialog.x";
    private static final String PREF_Y = "entitySearchDialog.y";
    private static final String PREF_WIDTH = "entitySearchDialog.width";
    private static final String PREF_HEIGHT = "entitySearchDialog.height";

    public EntitySearchDialog(Frame owner, CoinEntity entity, EntityStore store) {
        super(owner, "Search Related — " + entity.name(), false);
        this.sourceEntity = entity;
        this.store = store;
        this.processor = new EntitySearchProcessor();
        this.matcher = new EntityMatcher(store);

        // Transparent title bar (same style as IntelFrame)
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        restoreBounds(owner);
        initUI();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) { saveBounds(); }
            @Override
            public void componentMoved(ComponentEvent e) { saveBounds(); }
        });
    }

    private void restoreBounds(Frame owner) {
        Preferences prefs = Preferences.userNodeForPackage(EntitySearchDialog.class);
        int w = prefs.getInt(PREF_WIDTH, 900);
        int h = prefs.getInt(PREF_HEIGHT, 700);
        int x = prefs.getInt(PREF_X, -1);
        int y = prefs.getInt(PREF_Y, -1);

        setSize(w, h);
        if (x >= 0 && y >= 0) {
            setLocation(x, y);
        } else {
            setLocationRelativeTo(owner);
        }
    }

    private void saveBounds() {
        Preferences prefs = Preferences.userNodeForPackage(EntitySearchDialog.class);
        prefs.putInt(PREF_X, getX());
        prefs.putInt(PREF_Y, getY());
        prefs.putInt(PREF_WIDTH, getWidth());
        prefs.putInt(PREF_HEIGHT, getHeight());
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Header bar (same style as IntelFrame)
        mainPanel.add(createHeaderBar(), BorderLayout.NORTH);

        // Content area (no vertical padding so split spans full height)
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(new EmptyBorder(0, 15, 0, 0));

        // Center: Split pane with types on left, results on right
        ThinSplitPane splitPane = new ThinSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(420);
        splitPane.setResizeWeight(0);

        Color mutedText = UIManager.getColor("Label.disabledForeground");

        // Left: Entity types with search buttons
        JPanel leftPanel = new JPanel(new BorderLayout());
        JLabel typesTitle = new JLabel("Search by Type");
        typesTitle.setForeground(mutedText);
        typesTitle.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 0));
        leftPanel.add(typesTitle, BorderLayout.NORTH);

        typesPanel = new JPanel();
        typesPanel.setLayout(new BoxLayout(typesPanel, BoxLayout.Y_AXIS));
        BorderlessScrollPane typesScroll = new BorderlessScrollPane(typesPanel);
        leftPanel.add(typesScroll, BorderLayout.CENTER);
        splitPane.setLeftComponent(leftPanel);

        // Right: Results (scroll pane directly, no wrapper)
        resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsScroll = new BorderlessScrollPane(resultsPanel);
        splitPane.setRightComponent(resultsScroll);

        contentPanel.add(splitPane, BorderLayout.CENTER);

        mainPanel.add(contentPanel, BorderLayout.CENTER);

        // Bottom: Status bar (outside content area so separator spans full width)
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // Populate types panel
        populateTypesPanel();
        showMessage("Click search buttons to find related entities.");
    }

    private JPanel createHeaderBar() {
        int barHeight = 52;

        JPanel headerBar = new JPanel(new GridBagLayout());
        headerBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")));
        headerBar.setPreferredSize(new Dimension(0, barHeight));
        headerBar.setMinimumSize(new Dimension(0, barHeight));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;

        // Left: traffic light placeholder + source entity info
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.WEST;
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setOpaque(false);
        JPanel leftContent = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftContent.setOpaque(false);

        if (SystemInfo.isMacOS) {
            JPanel buttonsPlaceholder = new JPanel();
            buttonsPlaceholder.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER, "mac");
            buttonsPlaceholder.setOpaque(false);
            leftContent.add(buttonsPlaceholder);
        }

        JLabel nameLabel = new JLabel(sourceEntity.name());
        nameLabel.setForeground(sourceEntity.type().color());
        nameLabel.setFont(ToolbarButton.TOOLBAR_FONT.deriveFont(Font.BOLD));
        leftContent.add(nameLabel);

        if (sourceEntity.symbol() != null) {
            JLabel symbolLabel = new JLabel("(" + sourceEntity.symbol() + ")");
            symbolLabel.setFont(ToolbarButton.TOOLBAR_FONT);
            leftContent.add(symbolLabel);
        }

        JLabel typeLabel = new JLabel("[" + sourceEntity.type().name() + "]");
        typeLabel.setFont(ToolbarButton.TOOLBAR_FONT);
        leftContent.add(typeLabel);

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.fill = GridBagConstraints.HORIZONTAL;
        lc.weightx = 1.0;
        leftPanel.add(leftContent, lc);
        headerBar.add(leftPanel, gbc);

        // Center: Title
        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        JLabel titleLabel = new JLabel("Search Related");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        headerBar.add(titleLabel, gbc);

        // Right: action buttons
        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.EAST;
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);
        JPanel rightContent = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightContent.setOpaque(false);

        JButton selectAllBtn = new ToolbarButton("Select All");
        selectAllBtn.addActionListener(e -> selectAll(true));
        rightContent.add(selectAllBtn);

        JButton selectNoneBtn = new ToolbarButton("Select None");
        selectNoneBtn.addActionListener(e -> selectAll(false));
        rightContent.add(selectNoneBtn);

        addSelectedBtn = new ToolbarButton("Add Selected");
        addSelectedBtn.setEnabled(false);
        addSelectedBtn.addActionListener(e -> addSelectedEntities());
        rightContent.add(addSelectedBtn);

        GridBagConstraints rc = new GridBagConstraints();
        rc.anchor = GridBagConstraints.EAST;
        rc.fill = GridBagConstraints.HORIZONTAL;
        rc.weightx = 1.0;
        rightPanel.add(rightContent, rc);
        headerBar.add(rightPanel, gbc);

        return headerBar;
    }

    private void populateTypesPanel() {
        typesPanel.removeAll();

        List<CoinRelationship.Type> searchableTypes = CoinRelationship.Type.getSearchableTypes(sourceEntity.type());

        // Grid: 4 columns (label + 3 buttons), N rows
        JPanel gridPanel = new JPanel(new GridBagLayout());
        gridPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 24));
        gridPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        int row = 0;
        for (CoinRelationship.Type relType : searchableTypes) {
            addTypeRow(gridPanel, relType, row++);
        }

        // Wrap grid in a panel that doesn't expand vertically
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(gridPanel, BorderLayout.NORTH);
        wrapperPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        typesPanel.add(wrapperPanel);
        typesPanel.revalidate();
    }

    private void addTypeRow(JPanel gridPanel, CoinRelationship.Type relType, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = row;
        gbc.insets = new Insets(9, 3, 9, 3);
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;

        // Type label
        String label = getShortLabel(relType, sourceEntity.type());
        JLabel typeLabel = new JLabel(label);
        typeLabel.setForeground(relType.color());
        typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD));
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gridPanel.add(typeLabel, gbc);

        Map<SearchLevel, JButton> buttons = new HashMap<>();
        Map<SearchLevel, Integer> counts = new HashMap<>();

        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int col = 1;
        for (SearchLevel level : SearchLevel.values()) {
            ToolbarButton btn = new ToolbarButton(level.label);
            btn.setToolTipText(level.tooltip);

            btn.addActionListener(e -> performSearch(relType, level, btn));

            buttons.put(level, btn);
            counts.put(level, -1);
            gbc.gridx = col++;
            gridPanel.add(btn, gbc);
        }

        typeStates.put(relType, new TypeSearchState(buttons, counts));
    }

    private String getShortLabel(CoinRelationship.Type relType, CoinEntity.Type entityType) {
        return switch (relType) {
            case ETF_TRACKS -> "ETFs";
            case ETP_TRACKS -> "ETPs";
            case INVESTED_IN -> entityType == CoinEntity.Type.VC ? "Investments" : "VCs";
            case L2_OF -> entityType == CoinEntity.Type.L2 ? "L1" : "L2s";
            case ECOSYSTEM -> "Ecosystem";
            case PARTNER -> "Partners";
            case FORK_OF -> "Forks";
            case FOUNDED_BY -> "Founders";
            case BRIDGE -> "Bridges";
            case COMPETITOR -> "Competitors";
        };
    }

    private JPanel createBottomPanel() {
        Color secondaryText = UIManager.getColor("Label.disabledForeground");
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(6, 15, 6, 15)
        ));

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(secondaryText);
        panel.add(statusLabel, BorderLayout.CENTER);

        return panel;
    }

    private void performSearch(CoinRelationship.Type relType, SearchLevel level, JButton button) {
        if (!processor.isAvailable()) {
            JOptionPane.showMessageDialog(this,
                "Claude CLI is not available. Please install it first.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // GENERAL searches all types at once
        if (level == SearchLevel.GENERAL) {
            performGeneralSearchAll();
            return;
        }

        // SPECIFIC and DEEP search just this type
        performSingleSearch(relType, level, button);
    }

    private void performGeneralSearchAll() {
        generalSearchInProgress = true;
        setAllButtonsEnabled(SearchLevel.GENERAL, false);

        // Mark all General buttons as searching
        for (TypeSearchState state : typeStates.values()) {
            JButton btn = state.buttons().get(SearchLevel.GENERAL);
            btn.setText("...");
        }

        activeInvestigations = 1;
        updateStatus();
        IntelLogPanel.logAI("Starting general investigation for " + sourceEntity.name());

        // Single search for all types at once (relType = null)
        CompletableFuture.supplyAsync(() ->
            processor.searchRelated(sourceEntity, null, msg -> {})
        ).thenAccept(result -> SwingUtilities.invokeLater(() -> {
            activeInvestigations = 0;
            generalSearchInProgress = false;

            if (result.hasError()) {
                // Reset all buttons on error
                for (TypeSearchState state : typeStates.values()) {
                    JButton btn = state.buttons().get(SearchLevel.GENERAL);
                    btn.setText("General");
                    btn.setEnabled(true);
                }
                IntelLogPanel.logError("Search failed: " + result.error());
            } else {
                List<EntitySearchProcessor.DiscoveredEntity> entities = result.entities();

                // Count results per relationship type
                Map<CoinRelationship.Type, Integer> countsByType = new HashMap<>();
                for (EntitySearchProcessor.DiscoveredEntity entity : entities) {
                    countsByType.merge(entity.relationshipType(), 1, Integer::sum);

                    String id = entity.generateId();
                    boolean exists = allResults.stream()
                        .anyMatch(e -> e.generateId().equals(id));
                    if (!exists) {
                        allResults.add(entity);
                    }
                }

                // Update buttons with counts per type
                for (Map.Entry<CoinRelationship.Type, TypeSearchState> entry : typeStates.entrySet()) {
                    CoinRelationship.Type type = entry.getKey();
                    JButton btn = entry.getValue().buttons().get(SearchLevel.GENERAL);
                    int count = countsByType.getOrDefault(type, 0);
                    btn.setText("General: " + count);
                    entry.getValue().resultCounts().put(SearchLevel.GENERAL, count);
                }

                IntelLogPanel.logSuccess("Found " + entities.size() + " related entities");
            }

            displayResults();
            updateStatus();
        }));
    }

    private void performSingleSearch(CoinRelationship.Type relType, SearchLevel level, JButton button) {
        String originalText = button.getText();
        button.setEnabled(false);
        button.setText("...");

        activeInvestigations++;
        updateStatus();
        IntelLogPanel.logAI("Searching for " + relType.name() + " related to " + sourceEntity.name());

        CompletableFuture.supplyAsync(() ->
            processor.searchRelated(sourceEntity, relType, msg -> {})
        ).thenAccept(result -> SwingUtilities.invokeLater(() -> {
            activeInvestigations--;
            button.setEnabled(true);

            if (result.hasError()) {
                button.setText(originalText);
                IntelLogPanel.logError("Search failed: " + result.error());
                updateStatus();
                return;
            }

            List<EntitySearchProcessor.DiscoveredEntity> entities = result.entities();
            int count = entities.size();

            button.setText(originalText + ": " + count);
            typeStates.get(relType).resultCounts().put(level, count);

            for (EntitySearchProcessor.DiscoveredEntity entity : entities) {
                String id = entity.generateId();
                boolean exists = allResults.stream()
                    .anyMatch(e -> e.generateId().equals(id));
                if (!exists) {
                    allResults.add(entity);
                }
            }

            displayResults();
            updateStatus();

            IntelLogPanel.logSuccess("Found " + count + " " + relType.name() + " entities");
        }));
    }

    private void setAllButtonsEnabled(SearchLevel level, boolean enabled) {
        for (TypeSearchState state : typeStates.values()) {
            JButton btn = state.buttons().get(level);
            if (btn != null && !btn.getText().contains(":")) {
                // Only enable if not already completed (has count)
                btn.setEnabled(enabled);
            }
        }
    }

    private void displayResults() {
        resultsPanel.removeAll();
        checkboxMap.clear();

        if (allResults.isEmpty()) {
            showMessage("No results yet. Click search buttons on the left.");
            return;
        }

        // Group by entity type (not relationship type) for clearer organization
        Map<CoinEntity.Type, List<EntitySearchProcessor.DiscoveredEntity>> grouped = new LinkedHashMap<>();
        for (EntitySearchProcessor.DiscoveredEntity entity : allResults) {
            grouped.computeIfAbsent(entity.type(), k -> new ArrayList<>()).add(entity);
        }

        // Content panel that won't expand
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        Color sepColor = UIManager.getColor("Separator.foreground");
        boolean firstGroup = true;

        for (Map.Entry<CoinEntity.Type, List<EntitySearchProcessor.DiscoveredEntity>> entry : grouped.entrySet()) {
            // Group header with full-width top separator (skip for first group)
            JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
            headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (!firstGroup) {
                headerPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, sepColor));
            }

            JLabel headerLabel = new JLabel(entry.getKey().name() +
                " (" + entry.getValue().size() + ")");
            headerLabel.setForeground(entry.getKey().color());
            headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 12f));
            headerPanel.add(headerLabel);

            contentPanel.add(headerPanel);

            // Entities with indented separators between them
            boolean first = true;
            for (EntitySearchProcessor.DiscoveredEntity entity : entry.getValue()) {
                if (!first) {
                    JPanel sep = new JPanel(new BorderLayout());
                    sep.setAlignmentX(Component.LEFT_ALIGNMENT);
                    sep.setPreferredSize(new Dimension(0, 1));
                    sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                    sep.setBorder(BorderFactory.createEmptyBorder(0, 35, 0, 0));
                    JPanel line = new JPanel();
                    line.setBackground(sepColor);
                    sep.add(line, BorderLayout.CENTER);
                    contentPanel.add(sep);
                }
                contentPanel.add(createEntityPanel(entity));
                first = false;
            }
            firstGroup = false;
        }

        // Wrap in BorderLayout.NORTH to push content up
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(contentPanel, BorderLayout.NORTH);

        resultsPanel.add(wrapperPanel);

        resultsPanel.revalidate();
        resultsPanel.repaint();
        addSelectedBtn.setEnabled(!allResults.isEmpty());
    }

    private JPanel createEntityPanel(EntitySearchProcessor.DiscoveredEntity entity) {
        Color mutedText = UIManager.getColor("Label.disabledForeground");
        Color textColor = UIManager.getColor("Label.foreground");
        Font baseFont = UIManager.getFont("Label.font");

        // Main panel with BorderLayout: content on left, link section on right
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Left content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // Top row: [checkbox] [name (symbol) [TYPE]] [confidence]
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

        JCheckBox checkbox = new JCheckBox();
        checkbox.setSelected(true);
        checkboxMap.put(entity, checkbox);
        topRow.add(checkbox);

        JLabel nameLabel = new JLabel(entity.name());
        nameLabel.setForeground(entity.type().color());
        nameLabel.setFont(baseFont.deriveFont(Font.BOLD));
        topRow.add(nameLabel);

        if (entity.symbol() != null) {
            JLabel symbolLabel = new JLabel("(" + entity.symbol() + ")");
            symbolLabel.setForeground(mutedText);
            symbolLabel.setFont(baseFont);
            topRow.add(symbolLabel);
        }

        // Show relationship type (entity type is already in the group header)
        String relLabel = getShortLabel(entity.relationshipType(), sourceEntity.type());
        JLabel relTypeLabel = new JLabel("[" + relLabel + "]");
        relTypeLabel.setForeground(entity.relationshipType().color());
        relTypeLabel.setFont(baseFont.deriveFont(baseFont.getSize() - 2f));
        topRow.add(relTypeLabel);

        JLabel confLabel = new JLabel(String.format("%.0f%%", entity.confidence() * 100));
        confLabel.setForeground(getConfidenceColor(entity.confidence()));
        confLabel.setFont(baseFont);
        topRow.add(confLabel);

        contentPanel.add(topRow);

        // Reason/description
        if (!entity.reason().isEmpty()) {
            JPanel reasonPanel = new JPanel(new BorderLayout());
            reasonPanel.setBorder(BorderFactory.createEmptyBorder(2, 28, 0, 10));

            String html = "<html><body style='width: 300px;'>" +
                escapeHtml(entity.reason()) + "</body></html>";
            JLabel reasonLabel = new JLabel(html);
            reasonLabel.setForeground(textColor);
            reasonLabel.setFont(baseFont.deriveFont(baseFont.getSize() - 1f));
            reasonPanel.add(reasonLabel, BorderLayout.CENTER);
            contentPanel.add(reasonPanel);
        }

        panel.add(contentPanel, BorderLayout.CENTER);

        // Right side: Link section (vertically centered)
        JPanel linkPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        List<EntityMatcher.MatchCandidate> matches = matcher.findMatches(entity);

        if (!matches.isEmpty()) {
            JLabel matchLabel = new JLabel("Link to:");
            matchLabel.setForeground(mutedText);
            linkPanel.add(matchLabel);

            List<String> labels = new ArrayList<>();
            List<CoinEntity> matchEntities = new ArrayList<>();
            for (EntityMatcher.MatchCandidate m : matches) {
                String label = m.existing().symbol() != null
                    ? m.existing().symbol()
                    : truncate(m.existing().name(), 12);
                labels.add(String.format("%s %.0f%%", label, m.score() * 100));
                matchEntities.add(m.existing());
            }
            labels.add("New");
            matchEntities.add(null);

            SegmentedToggle matchToggle = new SegmentedToggle(labels.toArray(new String[0]));
            int defaultIndex = matches.get(0).score() >= 0.90 ? 0 : labels.size() - 1;
            matchToggle.setSelectedIndex(defaultIndex);
            selectedMatches.put(entity, matchEntities.get(defaultIndex));

            matchToggle.setOnSelectionChanged(index ->
                selectedMatches.put(entity, matchEntities.get(index)));

            linkPanel.add(matchToggle);
        } else {
            String generatedId = entity.generateId();
            if (store.entityExists(generatedId)) {
                checkbox.setSelected(false);
                checkbox.setEnabled(false);
                nameLabel.setForeground(mutedText);
                JLabel existsLabel = new JLabel("(exists)");
                existsLabel.setForeground(mutedText);
                linkPanel.add(existsLabel);
            } else {
                // Always show "Link to: New" for visual consistency
                JLabel matchLabel = new JLabel("Link to:");
                matchLabel.setForeground(mutedText);
                linkPanel.add(matchLabel);

                SegmentedToggle newToggle = new SegmentedToggle(new String[]{"New"});
                newToggle.setSelectedIndex(0);
                linkPanel.add(newToggle);
                selectedMatches.put(entity, null);
            }
        }

        // Wrap linkPanel in a vertically centered container
        JPanel linkWrapper = new JPanel(new GridBagLayout());
        linkWrapper.add(linkPanel);
        panel.add(linkWrapper, BorderLayout.EAST);

        return panel;
    }

    private void updateStatus() {
        StringBuilder sb = new StringBuilder();

        if (activeInvestigations > 0) {
            sb.append(activeInvestigations).append(" investigation");
            if (activeInvestigations > 1) sb.append("s");
            sb.append(" running");
        }

        if (!allResults.isEmpty()) {
            if (!sb.isEmpty()) sb.append(" | ");
            int total = allResults.size();
            long selected = checkboxMap.values().stream()
                .filter(cb -> cb.isSelected() && cb.isEnabled()).count();
            sb.append(total).append(" found, ").append(selected).append(" selected");
        }

        statusLabel.setText(sb.isEmpty() ? " " : sb.toString());
    }

    private void showMessage(String message) {
        resultsPanel.removeAll();
        JLabel label = new JLabel(message);
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(new EmptyBorder(50, 20, 50, 20));
        resultsPanel.add(label);
        resultsPanel.revalidate();
        resultsPanel.repaint();
    }

    private void selectAll(boolean selected) {
        for (JCheckBox checkbox : checkboxMap.values()) {
            if (checkbox.isEnabled()) {
                checkbox.setSelected(selected);
            }
        }
        updateStatus();
    }

    private Color getConfidenceColor(double confidence) {
        if (confidence >= 0.9) return new Color(80, 200, 120);
        if (confidence >= 0.75) return new Color(180, 200, 80);
        return new Color(200, 150, 80);
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max - 1) + "...";
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    private void addSelectedEntities() {
        List<EntitySearchProcessor.DiscoveredEntity> selected = new ArrayList<>();
        for (Map.Entry<EntitySearchProcessor.DiscoveredEntity, JCheckBox> entry : checkboxMap.entrySet()) {
            if (entry.getValue().isSelected() && entry.getValue().isEnabled()) {
                selected.add(entry.getKey());
            }
        }

        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No entities selected.",
                "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int added = 0, linked = 0, relationships = 0;

        for (EntitySearchProcessor.DiscoveredEntity discovered : selected) {
            CoinEntity matchedEntity = selectedMatches.get(discovered);
            String entityId;

            if (matchedEntity != null) {
                entityId = matchedEntity.id();
                linked++;
            } else {
                entityId = discovered.generateId();
                if (!store.entityExists(entityId)) {
                    CoinEntity newEntity = new CoinEntity(
                        entityId,
                        discovered.name(),
                        discovered.symbol(),
                        discovered.type()
                    );
                    store.saveEntity(newEntity, "ai-discovery");
                    added++;
                }
            }

            CoinRelationship relationship = createRelationship(
                sourceEntity.id(), entityId,
                discovered.relationshipType(), discovered.reason()
            );

            if (!store.relationshipExists(relationship.fromId(), relationship.toId(), relationship.type())) {
                store.saveRelationship(relationship, "ai-discovery");
                relationships++;
            }
        }

        StringBuilder msg = new StringBuilder();
        if (added > 0) msg.append("Created ").append(added).append(" new entities");
        if (linked > 0) {
            if (!msg.isEmpty()) msg.append(", ");
            msg.append("linked to ").append(linked).append(" existing");
        }
        if (relationships > 0) {
            if (!msg.isEmpty()) msg.append(", ");
            msg.append(relationships).append(" relationships added");
        }
        if (msg.isEmpty()) msg.append("No changes made");
        msg.append(".");

        IntelLogPanel.logSuccess(msg.toString());
        JOptionPane.showMessageDialog(this, msg.toString(), "Success",
            JOptionPane.INFORMATION_MESSAGE);

        displayResults();
    }

    private CoinRelationship createRelationship(String sourceId, String targetId,
                                                 CoinRelationship.Type relType, String note) {
        return switch (relType) {
            case ETF_TRACKS, ETP_TRACKS ->
                new CoinRelationship(targetId, sourceId, relType, note);
            case INVESTED_IN ->
                sourceEntity.type() == CoinEntity.Type.VC
                    ? new CoinRelationship(sourceId, targetId, relType, note)
                    : new CoinRelationship(targetId, sourceId, relType, note);
            case L2_OF ->
                sourceEntity.type() == CoinEntity.Type.L2
                    ? new CoinRelationship(sourceId, targetId, relType, note)
                    : new CoinRelationship(targetId, sourceId, relType, note);
            default ->
                new CoinRelationship(sourceId, targetId, relType, note);
        };
    }
}
