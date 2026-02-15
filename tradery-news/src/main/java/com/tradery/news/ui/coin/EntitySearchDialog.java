package com.tradery.news.ui.coin;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemInfo;
import com.tradery.ai.AiConfig;
import com.tradery.ai.AiProfile;
import com.tradery.ai.pipeline.schema.SchemaSuggestion;
import com.tradery.news.ui.IntelLogPanel;
import com.tradery.ui.controls.BorderlessScrollPane;
import com.tradery.ui.controls.SegmentedToggle;
import com.tradery.ui.controls.ThinSplitPane;
import com.tradery.ui.controls.ToolbarButton;
import com.tradery.ui.controls.ToolbarComboBox;

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
 *
 * All type resolution is schema-driven via SchemaRegistry.
 */
public class EntitySearchDialog extends JDialog {

    private final CoinEntity sourceEntity;
    private final EntityStore store;
    private final SchemaRegistry schemaRegistry;
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

    private final Map<SchemaType, TypeSearchState> typeStates = new LinkedHashMap<>();
    private boolean generalSearchInProgress = false;
    private int activeInvestigations = 0;

    // All discovered entities from all searches
    private final List<EntitySearchProcessor.DiscoveredEntity> allResults = new ArrayList<>();
    private final Map<EntitySearchProcessor.DiscoveredEntity, CoinEntity> selectedMatches = new HashMap<>();
    private final Map<EntitySearchProcessor.DiscoveredEntity, JCheckBox> checkboxMap = new LinkedHashMap<>();

    // Schema suggestions (types the AI wants but don't exist yet)
    private final Map<String, SchemaSuggestion> pendingSuggestions = new LinkedHashMap<>();

    // UI components
    private JPanel typesPanel;
    private JPanel resultsPanel;
    private BorderlessScrollPane resultsScroll;
    private JButton addSelectedBtn;
    private JLabel statusLabel;
    private JProgressBar statusSpinner;
    private ToolbarComboBox<String> aiProfileCombo;
    private List<AiProfile> aiProfileList = new ArrayList<>();

    // Track default-AI investigations so we can cancel on profile switch
    private final List<CompletableFuture<?>> defaultAiFutures = new ArrayList<>();
    private int defaultAiInvestigations = 0;

    private static final String PREF_X = "entitySearchDialog.x";
    private static final String PREF_Y = "entitySearchDialog.y";
    private static final String PREF_WIDTH = "entitySearchDialog.width";
    private static final String PREF_HEIGHT = "entitySearchDialog.height";

    public EntitySearchDialog(Frame owner, CoinEntity entity, EntityStore store, SchemaRegistry schemaRegistry) {
        super((Frame) null, "Search Related — " + entity.name(), false);
        this.sourceEntity = entity;
        this.store = store;
        this.schemaRegistry = schemaRegistry;
        this.processor = new EntitySearchProcessor(schemaRegistry);
        this.matcher = new EntityMatcher(store, schemaRegistry);

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
        headerBar.setPreferredSize(new Dimension(0, barHeight));
        headerBar.setMinimumSize(new Dimension(0, barHeight));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 0, 0);

        // [AI switch]
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftPanel.setOpaque(false);
        if (SystemInfo.isMacOS) {
            JPanel buttonsPlaceholder = new JPanel();
            buttonsPlaceholder.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER, "mac");
            buttonsPlaceholder.setOpaque(false);
            leftPanel.add(buttonsPlaceholder);
        }
        populateAiCombo();
        aiProfileCombo.addActionListener(e -> onAiProfileChanged());
        leftPanel.add(aiProfileCombo);
        headerBar.add(leftPanel, gbc);

        // flexible space
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        headerBar.add(Box.createGlue(), gbc);

        // [Search Related: Entity Name]
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        String entityName = sourceEntity.name();
        JLabel titleLabel = new JLabel("Search Related: " + entityName);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(UIManager.getColor("Label.foreground"));
        headerBar.add(titleLabel, gbc);

        // flexible space
        gbc.gridx = 3;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        headerBar.add(Box.createGlue(), gbc);

        // [Select All] [Select None] 24px [Apply]
        gbc.gridx = 4;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setOpaque(false);

        JButton selectAllBtn = new ToolbarButton("Select All");
        selectAllBtn.addActionListener(e -> selectAll(true));
        rightPanel.add(selectAllBtn);

        JButton selectNoneBtn = new ToolbarButton("Select None");
        selectNoneBtn.addActionListener(e -> selectAll(false));
        rightPanel.add(selectNoneBtn);

        rightPanel.add(Box.createHorizontalStrut(24));

        JButton cancelBtn = new ToolbarButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        rightPanel.add(cancelBtn);

        addSelectedBtn = new ToolbarButton("Apply");
        addSelectedBtn.setEnabled(false);
        addSelectedBtn.addActionListener(e -> addSelectedEntities());
        rightPanel.add(addSelectedBtn);

        headerBar.add(rightPanel, gbc);

        JPanel headerWrapper = new JPanel(new BorderLayout());
        headerWrapper.add(headerBar, BorderLayout.CENTER);
        headerWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        return headerWrapper;
    }

    private void populateTypesPanel() {
        typesPanel.removeAll();

        String sourceTypeId = sourceEntity.type().name().toLowerCase();
        List<SchemaType> searchableTypes = schemaRegistry.getRelationshipTypesFor(sourceTypeId);

        // Grid: 4 columns (label + 3 buttons), N rows
        JPanel gridPanel = new JPanel(new GridBagLayout());
        gridPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 24));
        gridPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        int row = 0;
        for (SchemaType relSchema : searchableTypes) {
            addTypeRow(gridPanel, relSchema, row++);
        }

        // Wrap grid in a panel that doesn't expand vertically
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(gridPanel, BorderLayout.NORTH);
        wrapperPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        typesPanel.add(wrapperPanel);
        typesPanel.revalidate();
    }

    private void addTypeRow(JPanel gridPanel, SchemaType relSchema, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = row;
        gbc.insets = new Insets(9, 3, 9, 3);
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;

        // Type label from schema metadata
        String sourceTypeId = sourceEntity.type().name().toLowerCase();
        String label = relSchema.pluralLabelFor(sourceTypeId);
        JLabel typeLabel = new JLabel(label);
        typeLabel.setForeground(relSchema.color());
        typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD));
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gridPanel.add(typeLabel, gbc);

        // Use schema ID directly (no enum resolution needed)
        String relTypeId = relSchema.id();

        Map<SearchLevel, JButton> buttons = new HashMap<>();
        Map<SearchLevel, Integer> counts = new HashMap<>();

        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int col = 1;
        for (SearchLevel level : SearchLevel.values()) {
            String btnLabel = level == SearchLevel.DEEP ? "More ..." : level.label;
            ToolbarButton btn = new ToolbarButton(btnLabel);
            btn.setToolTipText(level.tooltip);

            if (level == SearchLevel.DEEP) {
                btn.addActionListener(e -> showDeepProfileMenu(relTypeId, btn));
            } else {
                btn.addActionListener(e -> performSearch(relTypeId, level, btn));
            }

            buttons.put(level, btn);
            counts.put(level, -1);
            gbc.gridx = col++;
            gridPanel.add(btn, gbc);
        }

        typeStates.put(relSchema, new TypeSearchState(buttons, counts));
    }

    private TypeSearchState findTypeSearchState(String relTypeId) {
        if (relTypeId == null) return null;
        for (Map.Entry<SchemaType, TypeSearchState> entry : typeStates.entrySet()) {
            if (entry.getKey().id().equals(relTypeId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private JPanel createBottomPanel() {
        Color secondaryText = UIManager.getColor("Label.disabledForeground");
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JSeparator(), BorderLayout.NORTH);

        JPanel innerPanel = new JPanel(new BorderLayout());
        innerPanel.setBorder(BorderFactory.createEmptyBorder(6, 15, 6, 15));

        statusSpinner = new JProgressBar();
        statusSpinner.setIndeterminate(true);
        statusSpinner.setPreferredSize(new Dimension(16, 16));
        statusSpinner.setVisible(false);

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(secondaryText);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        statusPanel.setOpaque(false);
        statusPanel.add(statusSpinner);
        statusPanel.add(statusLabel);
        innerPanel.add(statusPanel, BorderLayout.CENTER);

        panel.add(innerPanel, BorderLayout.CENTER);
        return panel;
    }

    private void performSearch(String relTypeId, SearchLevel level, JButton button) {
        if (!processor.isAvailable()) {
            JOptionPane.showMessageDialog(this,
                "AI is not available. Please configure an AI profile in Settings.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // GENERAL searches all types at once
        if (level == SearchLevel.GENERAL) {
            performGeneralSearchAll();
            return;
        }

        // SPECIFIC searches just this type
        performSingleSearch(relTypeId, level, button);
    }

    private void showDeepProfileMenu(String relTypeId, JButton button) {
        java.util.List<AiProfile> profiles = AiConfig.get().getProfiles();
        if (profiles.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No AI profiles configured. Please add one in Settings.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JPopupMenu popup = new JPopupMenu();
        String defaultId = AiConfig.get().getDefaultProfileId();

        for (AiProfile profile : profiles) {
            String star = profile.getId() != null && profile.getId().equals(defaultId) ? "\u2605 " : "";
            String desc = profile.getDescription() != null && !profile.getDescription().isEmpty()
                ? "<br><font color='gray' size='-2'>" + profile.getDescription() + "</font>" : "";
            JMenuItem item = new JMenuItem("<html><b>" + star + profile.getName() + "</b>"
                + desc + "</html>");
            item.addActionListener(e -> performDeepSearch(relTypeId, profile, button));
            popup.add(item);
        }

        popup.show(button, 0, button.getHeight());
    }

    private void performDeepSearch(String relTypeId, AiProfile profile, JButton button) {
        String originalText = button.getText();
        button.setEnabled(false);
        button.setText("...");

        activeInvestigations++;
        updateStatus();
        IntelLogPanel.logAI("Deep search for " + relTypeId + " related to " + sourceEntity.name()
            + " using " + profile.getName());

        CompletableFuture.supplyAsync(() ->
            processor.searchRelatedDeep(sourceEntity, relTypeId, profile, msg -> {})
        ).thenAccept(result -> SwingUtilities.invokeLater(() -> {
            activeInvestigations--;
            button.setEnabled(true);

            if (result.hasError()) {
                button.setText(originalText);
                IntelLogPanel.logError("Search failed: " + result.error());
                updateStatus();
                return;
            }

            java.util.List<EntitySearchProcessor.DiscoveredEntity> entities = result.entities();
            int count = entities.size();

            button.setText("More: " + count);
            TypeSearchState tss = findTypeSearchState(relTypeId);
            if (tss != null) tss.resultCounts().put(SearchLevel.DEEP, count);

            for (EntitySearchProcessor.DiscoveredEntity entity : entities) {
                String id = entity.generateId();
                boolean exists = allResults.stream()
                    .anyMatch(e -> e.generateId().equals(id));
                if (!exists) {
                    allResults.add(entity);
                }
            }

            collectSuggestions(result);
            displayResults();
            updateStatus();

            IntelLogPanel.logSuccess("Found " + count + " " + relTypeId + " entities (deep)");
        }));
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

        // Single search for all types at once (relTypeId = null)
        defaultAiInvestigations++;
        CompletableFuture<?> future = CompletableFuture.supplyAsync(() ->
            processor.searchRelated(sourceEntity, null, msg -> {})
        ).thenAccept(result -> SwingUtilities.invokeLater(() -> {
            defaultAiInvestigations = Math.max(0, defaultAiInvestigations - 1);
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

                // Count results per relationship type (using schema string IDs)
                Map<String, Integer> countsByRelType = new HashMap<>();
                for (EntitySearchProcessor.DiscoveredEntity entity : entities) {
                    countsByRelType.merge(entity.relationshipTypeId(), 1, Integer::sum);

                    String id = entity.generateId();
                    boolean exists = allResults.stream()
                        .anyMatch(e -> e.generateId().equals(id));
                    if (!exists) {
                        allResults.add(entity);
                    }
                }

                // Collect schema suggestions
                collectSuggestions(result);

                // Update buttons with counts per type
                for (Map.Entry<SchemaType, TypeSearchState> entry : typeStates.entrySet()) {
                    SchemaType schemaType = entry.getKey();
                    JButton btn = entry.getValue().buttons().get(SearchLevel.GENERAL);
                    int count = countsByRelType.getOrDefault(schemaType.id(), 0);
                    btn.setText("General: " + count);
                    entry.getValue().resultCounts().put(SearchLevel.GENERAL, count);
                }

                IntelLogPanel.logSuccess("Found " + entities.size() + " related entities");
            }

            displayResults();
            updateStatus();
        }));
        defaultAiFutures.add(future);
    }

    private void performSingleSearch(String relTypeId, SearchLevel level, JButton button) {
        String originalText = button.getText();
        button.setEnabled(false);
        button.setText("...");

        activeInvestigations++;
        defaultAiInvestigations++;
        updateStatus();
        IntelLogPanel.logAI("Searching for " + relTypeId + " related to " + sourceEntity.name());

        CompletableFuture<?> future = CompletableFuture.supplyAsync(() ->
            processor.searchRelated(sourceEntity, relTypeId, msg -> {})
        ).thenAccept(result -> SwingUtilities.invokeLater(() -> {
            activeInvestigations--;
            defaultAiInvestigations = Math.max(0, defaultAiInvestigations - 1);
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
            TypeSearchState tss = findTypeSearchState(relTypeId);
            if (tss != null) tss.resultCounts().put(level, count);

            for (EntitySearchProcessor.DiscoveredEntity entity : entities) {
                String id = entity.generateId();
                boolean exists = allResults.stream()
                    .anyMatch(e -> e.generateId().equals(id));
                if (!exists) {
                    allResults.add(entity);
                }
            }

            collectSuggestions(result);
            displayResults();
            updateStatus();

            IntelLogPanel.logSuccess("Found " + count + " " + relTypeId + " entities");
        }));
        defaultAiFutures.add(future);
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

        // Group by entity type ID (schema-driven, not enum-based)
        Map<String, List<EntitySearchProcessor.DiscoveredEntity>> grouped = new LinkedHashMap<>();
        for (EntitySearchProcessor.DiscoveredEntity entity : allResults) {
            grouped.computeIfAbsent(entity.typeId(), k -> new ArrayList<>()).add(entity);
        }

        // Content panel that won't expand
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Show schema suggestion banner if there are pending suggestions
        if (!pendingSuggestions.isEmpty()) {
            contentPanel.add(createSuggestionBanner());
            contentPanel.add(Box.createVerticalStrut(4));
        }

        Color sepColor = UIManager.getColor("Separator.foreground");
        boolean firstGroup = true;

        for (Map.Entry<String, List<EntitySearchProcessor.DiscoveredEntity>> entry : grouped.entrySet()) {
            String typeId = entry.getKey();

            // Full-width separator between groups (skip for first group)
            if (!firstGroup) {
                JSeparator groupSep = new JSeparator();
                groupSep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
                groupSep.setAlignmentX(Component.LEFT_ALIGNMENT);
                contentPanel.add(groupSep);
            }

            // Resolve display name and color from schema
            SchemaType schemaType = schemaRegistry.getType(typeId);
            String typeName = schemaType != null ? schemaType.name() : typeId;
            Color typeColor = schemaType != null ? schemaType.color() : Color.GRAY;

            // Group header
            JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
            headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel headerLabel = new JLabel(typeName +
                " (" + entry.getValue().size() + ")");
            headerLabel.setForeground(typeColor);
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

        // Resolve entity type color from schema
        Color entityColor = entity.resolveColor(schemaRegistry);
        JLabel nameLabel = new JLabel(entity.name());
        nameLabel.setForeground(entityColor);
        nameLabel.setFont(baseFont.deriveFont(Font.BOLD));
        topRow.add(nameLabel);

        if (entity.symbol() != null) {
            JLabel symbolLabel = new JLabel("(" + entity.symbol() + ")");
            symbolLabel.setForeground(mutedText);
            symbolLabel.setFont(baseFont);
            topRow.add(symbolLabel);
        }

        // Show relationship type — resolve from schema
        String sourceTypeId = sourceEntity.type().name().toLowerCase();
        SchemaType relSchema = schemaRegistry.getType(entity.relationshipTypeId());
        String relLabel = relSchema != null ? relSchema.pluralLabelFor(sourceTypeId) : entity.relationshipTypeId();
        Color relColor = relSchema != null ? relSchema.color() : mutedText;
        JLabel relTypeLabel = new JLabel("[" + relLabel + "]");
        relTypeLabel.setForeground(relColor);
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

    private void populateAiCombo() {
        aiProfileList = AiConfig.get().getProfiles();
        String defaultId = AiConfig.get().getDefaultProfileId();
        String[] names = aiProfileList.stream().map(AiProfile::getName).toArray(String[]::new);
        aiProfileCombo = new ToolbarComboBox<>(names);
        for (int i = 0; i < aiProfileList.size(); i++) {
            AiProfile p = aiProfileList.get(i);
            if (p.getId() != null && p.getId().equals(defaultId)) {
                aiProfileCombo.setSelectedIndex(i);
                break;
            }
        }
    }

    private void onAiProfileChanged() {
        int idx = aiProfileCombo.getSelectedIndex();
        if (idx < 0 || idx >= aiProfileList.size()) return;
        AiProfile chosen = aiProfileList.get(idx);
        String currentDefault = AiConfig.get().getDefaultProfileId();
        if (chosen.getId().equals(currentDefault)) return;

        // If default-based investigations are running, offer to cancel
        if (defaultAiInvestigations > 0) {
            int result = JOptionPane.showConfirmDialog(this,
                defaultAiInvestigations + " investigation" + (defaultAiInvestigations > 1 ? "s" : "")
                    + " running with the previous AI.\nCancel and retry with " + chosen.getName() + "?",
                "Switch AI", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                cancelDefaultAiFutures();
                AiConfig.get().setDefaultProfileId(chosen.getId());
                AiConfig.get().save();
                retryAllSearches();
                return;
            }
        }

        AiConfig.get().setDefaultProfileId(chosen.getId());
        AiConfig.get().save();
    }

    private void cancelDefaultAiFutures() {
        for (CompletableFuture<?> f : defaultAiFutures) {
            f.cancel(true);
        }
        defaultAiFutures.clear();
        activeInvestigations -= defaultAiInvestigations;
        defaultAiInvestigations = 0;

        // Reset button states
        for (TypeSearchState state : typeStates.values()) {
            for (Map.Entry<SearchLevel, JButton> entry : state.buttons().entrySet()) {
                if (entry.getKey() != SearchLevel.DEEP) {
                    entry.getValue().setEnabled(true);
                    entry.getValue().setText(entry.getKey().label);
                }
            }
            // Clear result counts for non-deep
            state.resultCounts().remove(SearchLevel.GENERAL);
            state.resultCounts().remove(SearchLevel.SPECIFIC);
        }
        generalSearchInProgress = false;
        updateStatus();
    }

    private void retryAllSearches() {
        // Re-run general search for all types
        performGeneralSearchAll();
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

        statusSpinner.setVisible(activeInvestigations > 0);
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

    /**
     * Collect schema suggestions from a search result, merging with existing ones.
     */
    private void collectSuggestions(EntitySearchProcessor.SearchResult result) {
        for (SchemaSuggestion suggestion : result.schemaSuggestions()) {
            // Skip if this type already exists in the schema (maybe added since last search)
            if (schemaRegistry.getType(suggestion.typeId()) != null) continue;
            // Merge: keep the one with the higher entity count
            pendingSuggestions.merge(suggestion.typeId(), suggestion, (old, neu) ->
                neu.entityCount() > old.entityCount() ? neu : old);
        }
    }

    /**
     * Create a suggestion banner panel for display above results.
     */
    private JPanel createSuggestionBanner() {
        Color bannerBg = new Color(60, 55, 45);
        Color bannerText = new Color(220, 190, 120);

        JPanel banner = new JPanel(new BorderLayout(8, 0));
        banner.setBackground(bannerBg);
        banner.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        // Left: info text
        JPanel textPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        textPanel.setOpaque(false);

        JLabel icon = new JLabel("\u2728"); // sparkle
        icon.setForeground(bannerText);
        textPanel.add(icon);

        StringBuilder sb = new StringBuilder("AI suggests new types: ");
        boolean first = true;
        for (SchemaSuggestion s : pendingSuggestions.values()) {
            if (!first) sb.append(", ");
            sb.append(s.suggestedName()).append(" (").append(s.entityCount()).append(")");
            first = false;
        }
        JLabel label = new JLabel(sb.toString());
        label.setForeground(bannerText);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        textPanel.add(label);

        banner.add(textPanel, BorderLayout.CENTER);

        // Right: "Add to ERD" button
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnPanel.setOpaque(false);

        ToolbarButton addBtn = new ToolbarButton("Add to ERD");
        addBtn.setToolTipText("Add suggested types to the entity schema");
        addBtn.addActionListener(e -> addSuggestedTypes());
        btnPanel.add(addBtn);

        banner.add(btnPanel, BorderLayout.EAST);

        return banner;
    }

    /**
     * Add all pending suggested types to the schema registry.
     */
    private void addSuggestedTypes() {
        int added = 0;
        for (SchemaSuggestion suggestion : pendingSuggestions.values()) {
            // Double-check it doesn't exist yet
            if (schemaRegistry.getType(suggestion.typeId()) != null) continue;

            // Pick a distinct color based on hash
            float hue = (suggestion.typeId().hashCode() & 0x7fffffff) % 360 / 360f;
            Color color = Color.getHSBColor(hue, 0.35f, 0.78f);

            SchemaType newType = new SchemaType(
                suggestion.typeId(), suggestion.suggestedName(), color, SchemaType.KIND_ENTITY);
            newType.setDisplayOrder(schemaRegistry.entityTypes().size());
            newType.addAttribute(new SchemaAttribute("name", SchemaAttribute.TEXT, true, 0));
            newType.addAttribute(new SchemaAttribute("symbol", SchemaAttribute.TEXT, false, 1));

            schemaRegistry.save(newType);
            for (SchemaAttribute attr : newType.attributes()) {
                schemaRegistry.addAttribute(newType.id(), attr);
            }

            IntelLogPanel.logData("Added schema type: " + suggestion.suggestedName()
                + " (" + suggestion.entityCount() + " discovered entities)");
            added++;
        }

        if (added > 0) {
            pendingSuggestions.clear();
            IntelLogPanel.logSuccess("Added " + added + " new entity types to schema");

            // Clear old results and re-search so entities get correct types
            allResults.clear();
            selectedMatches.clear();
            checkboxMap.clear();
            // Refresh left panel (may have new relationship types)
            populateTypesPanel();
            // Re-run general search with the new schema
            performGeneralSearchAll();
        }
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
                    // Resolve CoinEntity.Type from schema — fall back to COIN for unknown types
                    CoinEntity.Type coinType = discovered.resolveCoinEntityType();
                    if (coinType == null) coinType = CoinEntity.Type.COIN;

                    CoinEntity newEntity = new CoinEntity(
                        entityId,
                        discovered.name(),
                        discovered.symbol(),
                        coinType
                    );
                    store.saveEntity(newEntity, "ai-discovery");
                    added++;
                }
            }

            // Use string typeId directly — fall back to "partner" for null
            String relTypeId = discovered.relationshipTypeId();
            if (relTypeId == null || relTypeId.isEmpty()) relTypeId = "partner";

            CoinRelationship relationship = createRelationship(
                sourceEntity.id(), entityId, relTypeId, discovered.reason()
            );

            if (!store.relationshipExists(relationship.fromId(), relationship.toId(), relationship.typeId())) {
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
        dispose();
    }

    private CoinRelationship createRelationship(String sourceId, String targetId,
                                                 String relTypeId, String note) {
        SchemaType relSchema = schemaRegistry.getType(relTypeId);
        if (relSchema != null) {
            String sourceTypeId = sourceEntity.type().name().toLowerCase();
            return relSchema.createDirected(sourceId, sourceTypeId, targetId, relTypeId, note);
        }
        // Fallback if schema not found
        return new CoinRelationship(sourceId, targetId, relTypeId, note);
    }
}
