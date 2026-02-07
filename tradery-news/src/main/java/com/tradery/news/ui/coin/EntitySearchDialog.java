package com.tradery.news.ui.coin;

import com.tradery.news.ui.IntelLogPanel;
import com.tradery.ui.controls.SegmentedToggle;
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
    private JScrollPane resultsScroll;
    private JButton addSelectedBtn;
    private JLabel statusLabel;

    private static final String PREF_X = "entitySearchDialog.x";
    private static final String PREF_Y = "entitySearchDialog.y";
    private static final String PREF_WIDTH = "entitySearchDialog.width";
    private static final String PREF_HEIGHT = "entitySearchDialog.height";

    public EntitySearchDialog(Frame owner, CoinEntity entity, EntityStore store) {
        super(owner, "Search Related Entities", false);
        this.sourceEntity = entity;
        this.store = store;
        this.processor = new EntitySearchProcessor();
        this.matcher = new EntityMatcher(store);

        setAlwaysOnTop(true);
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
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Top: Source entity info
        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH);

        // Center: Split pane with types on left, results on right
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(280);
        splitPane.setResizeWeight(0);

        // Left: Entity types with search buttons
        typesPanel = new JPanel();
        typesPanel.setLayout(new BoxLayout(typesPanel, BoxLayout.Y_AXIS));
        JScrollPane typesScroll = new JScrollPane(typesPanel);
        typesScroll.setBorder(BorderFactory.createTitledBorder("Search by Type"));
        splitPane.setLeftComponent(typesScroll);

        // Right: Results
        resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsScroll = new JScrollPane(resultsPanel);
        resultsScroll.setBorder(BorderFactory.createTitledBorder("Results"));
        splitPane.setRightComponent(resultsScroll);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        // Bottom: Status and action buttons
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // Populate types panel
        populateTypesPanel();
        showMessage("Click search buttons to find related entities.");
    }

    private JPanel createHeaderPanel() {
        Color secondaryText = UIManager.getColor("Label.disabledForeground");
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        JLabel sourceLabel = new JLabel("Source:");
        sourceLabel.setForeground(secondaryText);
        panel.add(sourceLabel);

        JLabel nameLabel = new JLabel(sourceEntity.name());
        nameLabel.setForeground(sourceEntity.type().color());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        panel.add(nameLabel);

        if (sourceEntity.symbol() != null) {
            JLabel symbolLabel = new JLabel("(" + sourceEntity.symbol() + ")");
            symbolLabel.setForeground(secondaryText);
            panel.add(symbolLabel);
        }

        JLabel typeLabel = new JLabel("[" + sourceEntity.type().name() + "]");
        typeLabel.setForeground(secondaryText);
        panel.add(typeLabel);

        return panel;
    }

    private void populateTypesPanel() {
        typesPanel.removeAll();

        List<CoinRelationship.Type> searchableTypes = CoinRelationship.Type.getSearchableTypes(sourceEntity.type());

        // Grid: 4 columns (label + 3 buttons), N rows
        JPanel gridPanel = new JPanel(new GridLayout(searchableTypes.size(), 4, 5, 5));
        gridPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        for (CoinRelationship.Type relType : searchableTypes) {
            addTypeRow(gridPanel, relType);
        }

        typesPanel.add(gridPanel);
        typesPanel.add(Box.createVerticalGlue());
        typesPanel.revalidate();
    }

    private void addTypeRow(JPanel gridPanel, CoinRelationship.Type relType) {
        // Type label
        String label = getShortLabel(relType, sourceEntity.type());
        JLabel typeLabel = new JLabel(label);
        typeLabel.setForeground(relType.color());
        typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD));
        gridPanel.add(typeLabel);

        Map<SearchLevel, JButton> buttons = new HashMap<>();
        Map<SearchLevel, Integer> counts = new HashMap<>();

        for (SearchLevel level : SearchLevel.values()) {
            ToolbarButton btn = new ToolbarButton(level.label);
            btn.setToolTipText(level.tooltip);

            btn.addActionListener(e -> performSearch(relType, level, btn));

            buttons.put(level, btn);
            counts.put(level, -1);
            gridPanel.add(btn);
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
        JPanel panel = new JPanel(new BorderLayout(10, 5));

        // Status
        statusLabel = new JLabel(" ");
        statusLabel.setForeground(secondaryText);
        panel.add(statusLabel, BorderLayout.WEST);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        JButton selectAllBtn = new JButton("Select All");
        selectAllBtn.addActionListener(e -> selectAll(true));
        buttonPanel.add(selectAllBtn);

        JButton selectNoneBtn = new JButton("Select None");
        selectNoneBtn.addActionListener(e -> selectAll(false));
        buttonPanel.add(selectNoneBtn);

        addSelectedBtn = new JButton("Add Selected");
        addSelectedBtn.setEnabled(false);
        addSelectedBtn.addActionListener(e -> addSelectedEntities());
        buttonPanel.add(addSelectedBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        buttonPanel.add(closeBtn);

        panel.add(buttonPanel, BorderLayout.EAST);
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

        List<CoinRelationship.Type> types = new ArrayList<>(typeStates.keySet());
        IntelLogPanel.logAI("Starting general investigation of all types for " + sourceEntity.name());

        // Search all types in parallel
        for (CoinRelationship.Type type : types) {
            activeInvestigations++;
            updateStatus();

            CompletableFuture.supplyAsync(() ->
                processor.searchRelated(sourceEntity, type, msg -> {})
            ).thenAccept(result -> SwingUtilities.invokeLater(() -> {
                activeInvestigations--;
                JButton btn = typeStates.get(type).buttons().get(SearchLevel.GENERAL);

                if (result.hasError()) {
                    btn.setText("General");
                    btn.setEnabled(true);
                    IntelLogPanel.logError("Search failed for " + type.name() + ": " + result.error());
                } else {
                    List<EntitySearchProcessor.DiscoveredEntity> entities = result.entities();
                    int count = entities.size();

                    btn.setText("General: " + count);
                    typeStates.get(type).resultCounts().put(SearchLevel.GENERAL, count);

                    for (EntitySearchProcessor.DiscoveredEntity entity : entities) {
                        String id = entity.generateId();
                        boolean exists = allResults.stream()
                            .anyMatch(e -> e.generateId().equals(id));
                        if (!exists) {
                            allResults.add(entity);
                        }
                    }

                    IntelLogPanel.logSuccess("Found " + count + " " + type.name() + " entities");
                }

                // Check if all general searches are done
                if (activeInvestigations == 0) {
                    generalSearchInProgress = false;
                    // Don't re-enable - they show counts now
                }

                displayResults();
                updateStatus();
            }));
        }
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

        Color headerBg = UIManager.getColor("TableHeader.background");

        // Group by relationship type
        Map<CoinRelationship.Type, List<EntitySearchProcessor.DiscoveredEntity>> grouped = new LinkedHashMap<>();
        for (EntitySearchProcessor.DiscoveredEntity entity : allResults) {
            grouped.computeIfAbsent(entity.relationshipType(), k -> new ArrayList<>()).add(entity);
        }

        for (Map.Entry<CoinRelationship.Type, List<EntitySearchProcessor.DiscoveredEntity>> entry : grouped.entrySet()) {
            // Group header
            JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            headerPanel.setBackground(headerBg);
            headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

            JLabel headerLabel = new JLabel(entry.getKey().name().replace("_", " ") +
                " (" + entry.getValue().size() + ")");
            headerLabel.setForeground(entry.getKey().color());
            headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 12f));
            headerPanel.add(headerLabel);

            resultsPanel.add(headerPanel);

            // Entities
            for (EntitySearchProcessor.DiscoveredEntity entity : entry.getValue()) {
                resultsPanel.add(createEntityPanel(entity));
            }

            resultsPanel.add(Box.createVerticalStrut(5));
        }

        // Spacer to consume extra vertical space
        resultsPanel.add(Box.createVerticalGlue());

        resultsPanel.revalidate();
        resultsPanel.repaint();
        addSelectedBtn.setEnabled(!allResults.isEmpty());
    }

    private JPanel createEntityPanel(EntitySearchProcessor.DiscoveredEntity entity) {
        Color borderColor = UIManager.getColor("Separator.foreground");
        Color mutedText = UIManager.getColor("Label.disabledForeground");
        Color textColor = UIManager.getColor("Label.foreground");
        Font baseFont = UIManager.getFont("Label.font");

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

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

        JLabel typeLabel = new JLabel("[" + entity.type().name() + "]");
        typeLabel.setForeground(mutedText);
        typeLabel.setFont(baseFont.deriveFont(baseFont.getSize() - 2f));
        topRow.add(typeLabel);

        JLabel confLabel = new JLabel(String.format("%.0f%%", entity.confidence() * 100));
        confLabel.setForeground(getConfidenceColor(entity.confidence()));
        confLabel.setFont(baseFont);
        topRow.add(confLabel);

        panel.add(topRow);

        // Reason/description
        if (!entity.reason().isEmpty()) {
            JPanel reasonPanel = new JPanel(new BorderLayout());
            reasonPanel.setBorder(BorderFactory.createEmptyBorder(2, 28, 0, 10));

            String html = "<html><body style='width: 350px;'>" +
                escapeHtml(entity.reason()) + "</body></html>";
            JLabel reasonLabel = new JLabel(html);
            reasonLabel.setForeground(textColor);
            reasonLabel.setFont(baseFont.deriveFont(baseFont.getSize() - 1f));
            reasonPanel.add(reasonLabel, BorderLayout.CENTER);
            panel.add(reasonPanel);
        }

        // Fuzzy match indicator
        List<EntityMatcher.MatchCandidate> matches = matcher.findMatches(entity);
        JPanel matchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        matchPanel.setBorder(BorderFactory.createEmptyBorder(0, 28, 0, 0));

        if (!matches.isEmpty()) {
            JLabel matchLabel = new JLabel("Link to:");
            matchLabel.setForeground(mutedText);
            matchPanel.add(matchLabel);

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

            matchPanel.add(matchToggle);
        } else {
            String generatedId = entity.generateId();
            if (store.entityExists(generatedId)) {
                checkbox.setSelected(false);
                checkbox.setEnabled(false);
                nameLabel.setForeground(mutedText);
                JLabel existsLabel = new JLabel("(exists)");
                existsLabel.setForeground(mutedText);
                matchPanel.add(existsLabel);
            } else {
                JLabel newLabel = new JLabel("(new)");
                newLabel.setForeground(new Color(80, 200, 120));
                matchPanel.add(newLabel);
                selectedMatches.put(entity, null);
            }
        }
        panel.add(matchPanel);

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
