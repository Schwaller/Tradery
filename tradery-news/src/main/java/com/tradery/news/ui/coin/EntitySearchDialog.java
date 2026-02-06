package com.tradery.news.ui.coin;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Dialog for searching and selecting related entities using AI.
 */
public class EntitySearchDialog extends JDialog {

    private final CoinEntity sourceEntity;
    private final EntityStore store;
    private final EntitySearchProcessor processor;

    private JComboBox<RelationshipOption> relationshipCombo;
    private JPanel resultsPanel;
    private JScrollPane resultsScroll;
    private JButton searchBtn;
    private JButton addSelectedBtn;
    private JProgressBar progressBar;
    private JLabel statusLabel;

    private final Map<EntitySearchProcessor.DiscoveredEntity, JCheckBox> checkboxMap = new LinkedHashMap<>();
    private List<EntitySearchProcessor.DiscoveredEntity> lastResults = new ArrayList<>();

    public EntitySearchDialog(Frame owner, CoinEntity entity, EntityStore store) {
        super(owner, "Search Related Entities", true);
        this.sourceEntity = entity;
        this.store = store;
        this.processor = new EntitySearchProcessor();

        setSize(600, 500);
        setLocationRelativeTo(owner);
        initUI();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(new Color(38, 40, 44));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Top: Entity info and relationship selector
        JPanel topPanel = createTopPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Center: Results panel
        resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setBackground(new Color(35, 37, 41));

        resultsScroll = new JScrollPane(resultsPanel);
        resultsScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 62, 66)));
        resultsScroll.getViewport().setBackground(new Color(35, 37, 41));
        mainPanel.add(resultsScroll, BorderLayout.CENTER);

        // Bottom: Status and buttons
        JPanel bottomPanel = createBottomPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // Initial message
        showMessage("Click 'Search' to find related entities using AI.");
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(38, 40, 44));

        // Entity info
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        infoPanel.setBackground(new Color(38, 40, 44));

        JLabel entityLabel = new JLabel("Source: ");
        entityLabel.setForeground(new Color(150, 150, 160));
        infoPanel.add(entityLabel);

        JLabel nameLabel = new JLabel(sourceEntity.name());
        nameLabel.setForeground(sourceEntity.type().color());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        infoPanel.add(nameLabel);

        if (sourceEntity.symbol() != null) {
            JLabel symbolLabel = new JLabel("(" + sourceEntity.symbol() + ")");
            symbolLabel.setForeground(new Color(120, 120, 130));
            infoPanel.add(symbolLabel);
        }

        JLabel typeLabel = new JLabel("[" + sourceEntity.type().name() + "]");
        typeLabel.setForeground(new Color(100, 100, 110));
        infoPanel.add(typeLabel);

        panel.add(infoPanel, BorderLayout.NORTH);

        // Relationship selector
        JPanel selectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        selectorPanel.setBackground(new Color(38, 40, 44));

        JLabel searchLabel = new JLabel("Search for:");
        searchLabel.setForeground(new Color(180, 180, 190));
        selectorPanel.add(searchLabel);

        relationshipCombo = new JComboBox<>();
        relationshipCombo.setBackground(new Color(60, 62, 66));
        relationshipCombo.setForeground(new Color(200, 200, 210));
        populateRelationshipOptions();
        selectorPanel.add(relationshipCombo);

        searchBtn = new JButton("Search");
        searchBtn.addActionListener(e -> performSearch());
        selectorPanel.add(searchBtn);

        panel.add(selectorPanel, BorderLayout.CENTER);

        return panel;
    }

    private void populateRelationshipOptions() {
        // Add "All related" option first
        relationshipCombo.addItem(new RelationshipOption(null, "All related entities"));

        // Add specific relationship types based on entity type
        List<CoinRelationship.Type> searchableTypes = CoinRelationship.Type.getSearchableTypes(sourceEntity.type());
        for (CoinRelationship.Type type : searchableTypes) {
            String label = getRelationshipLabel(type, sourceEntity.type());
            relationshipCombo.addItem(new RelationshipOption(type, label));
        }
    }

    private String getRelationshipLabel(CoinRelationship.Type relType, CoinEntity.Type entityType) {
        return switch (relType) {
            case ETF_TRACKS -> "ETFs tracking this";
            case ETP_TRACKS -> "ETPs tracking this";
            case INVESTED_IN -> entityType == CoinEntity.Type.VC ? "Investments" : "Investors (VCs)";
            case L2_OF -> entityType == CoinEntity.Type.L2 ? "Parent L1 chain" : "Layer 2 networks";
            case ECOSYSTEM -> "Ecosystem projects";
            case PARTNER -> "Partners";
            case FORK_OF -> "Forks";
            case FOUNDED_BY -> "Founders";
            case BRIDGE -> "Bridges";
            case COMPETITOR -> "Competitors";
        };
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBackground(new Color(38, 40, 44));

        // Progress bar
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(0, 3));
        panel.add(progressBar, BorderLayout.NORTH);

        // Status and buttons
        JPanel bottomRow = new JPanel(new BorderLayout(10, 0));
        bottomRow.setBackground(new Color(38, 40, 44));

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(new Color(140, 140, 150));
        bottomRow.add(statusLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(new Color(38, 40, 44));

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

        bottomRow.add(buttonPanel, BorderLayout.EAST);
        panel.add(bottomRow, BorderLayout.SOUTH);

        return panel;
    }

    private void performSearch() {
        if (!processor.isAvailable()) {
            showMessage("Claude CLI is not available. Please install it first.");
            return;
        }

        searchBtn.setEnabled(false);
        progressBar.setVisible(true);
        statusLabel.setText("Searching...");
        resultsPanel.removeAll();
        checkboxMap.clear();

        RelationshipOption selected = (RelationshipOption) relationshipCombo.getSelectedItem();
        CoinRelationship.Type relType = selected != null ? selected.type() : null;

        CompletableFuture.supplyAsync(() -> processor.searchRelated(sourceEntity, relType))
            .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                progressBar.setVisible(false);
                searchBtn.setEnabled(true);

                if (result.hasError()) {
                    showMessage("Search failed: " + result.error());
                    return;
                }

                lastResults = result.entities();
                if (lastResults.isEmpty()) {
                    showMessage("No related entities found.");
                    return;
                }

                displayResults(lastResults);
                statusLabel.setText(lastResults.size() + " entities found");
            }));
    }

    private void displayResults(List<EntitySearchProcessor.DiscoveredEntity> entities) {
        resultsPanel.removeAll();
        checkboxMap.clear();

        // Group by relationship type
        Map<CoinRelationship.Type, List<EntitySearchProcessor.DiscoveredEntity>> grouped = new LinkedHashMap<>();
        for (EntitySearchProcessor.DiscoveredEntity entity : entities) {
            grouped.computeIfAbsent(entity.relationshipType(), k -> new ArrayList<>()).add(entity);
        }

        for (Map.Entry<CoinRelationship.Type, List<EntitySearchProcessor.DiscoveredEntity>> entry : grouped.entrySet()) {
            // Group header
            JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            headerPanel.setBackground(new Color(45, 47, 51));
            headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

            JLabel headerLabel = new JLabel(entry.getKey().name().replace("_", " "));
            headerLabel.setForeground(entry.getKey().color());
            headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 12f));
            headerPanel.add(headerLabel);

            resultsPanel.add(headerPanel);

            // Entities in group
            for (EntitySearchProcessor.DiscoveredEntity entity : entry.getValue()) {
                JPanel entityPanel = createEntityPanel(entity);
                resultsPanel.add(entityPanel);
            }

            resultsPanel.add(Box.createVerticalStrut(5));
        }

        resultsPanel.revalidate();
        resultsPanel.repaint();
        addSelectedBtn.setEnabled(!entities.isEmpty());
    }

    private JPanel createEntityPanel(EntitySearchProcessor.DiscoveredEntity entity) {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBackground(new Color(35, 37, 41));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 52, 56)),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

        // Left: Checkbox
        JCheckBox checkbox = new JCheckBox();
        checkbox.setBackground(new Color(35, 37, 41));
        checkbox.setSelected(true);  // Default to selected
        checkboxMap.put(entity, checkbox);
        panel.add(checkbox, BorderLayout.WEST);

        // Center: Entity info
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(new Color(35, 37, 41));

        // Name and symbol
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        namePanel.setBackground(new Color(35, 37, 41));

        JLabel nameLabel = new JLabel(entity.name());
        nameLabel.setForeground(entity.type().color());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        namePanel.add(nameLabel);

        if (entity.symbol() != null) {
            JLabel symbolLabel = new JLabel("(" + entity.symbol() + ")");
            symbolLabel.setForeground(new Color(140, 140, 150));
            namePanel.add(symbolLabel);
        }

        JLabel typeLabel = new JLabel("[" + entity.type().name() + "]");
        typeLabel.setForeground(new Color(100, 100, 110));
        typeLabel.setFont(typeLabel.getFont().deriveFont(10f));
        namePanel.add(typeLabel);

        infoPanel.add(namePanel);

        // Reason
        if (!entity.reason().isEmpty()) {
            JLabel reasonLabel = new JLabel(entity.reason());
            reasonLabel.setForeground(new Color(130, 130, 140));
            reasonLabel.setFont(reasonLabel.getFont().deriveFont(11f));
            infoPanel.add(reasonLabel);
        }

        panel.add(infoPanel, BorderLayout.CENTER);

        // Right: Confidence
        JLabel confidenceLabel = new JLabel(String.format("%.0f%%", entity.confidence() * 100));
        confidenceLabel.setForeground(getConfidenceColor(entity.confidence()));
        confidenceLabel.setFont(confidenceLabel.getFont().deriveFont(11f));
        panel.add(confidenceLabel, BorderLayout.EAST);

        // Check if entity already exists
        String generatedId = entity.generateId();
        if (store.entityExists(generatedId)) {
            checkbox.setSelected(false);
            checkbox.setEnabled(false);
            nameLabel.setForeground(new Color(100, 100, 110));
            JLabel existsLabel = new JLabel(" (exists)");
            existsLabel.setForeground(new Color(100, 100, 110));
            existsLabel.setFont(existsLabel.getFont().deriveFont(10f));
            namePanel.add(existsLabel);
        }

        return panel;
    }

    private Color getConfidenceColor(double confidence) {
        if (confidence >= 0.9) return new Color(80, 200, 120);
        if (confidence >= 0.75) return new Color(180, 200, 80);
        return new Color(200, 150, 80);
    }

    private void showMessage(String message) {
        resultsPanel.removeAll();
        JLabel label = new JLabel(message);
        label.setForeground(new Color(150, 150, 160));
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
    }

    private void addSelectedEntities() {
        List<EntitySearchProcessor.DiscoveredEntity> selected = new ArrayList<>();
        for (Map.Entry<EntitySearchProcessor.DiscoveredEntity, JCheckBox> entry : checkboxMap.entrySet()) {
            if (entry.getValue().isSelected() && entry.getValue().isEnabled()) {
                selected.add(entry.getKey());
            }
        }

        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No entities selected.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int added = 0;
        int relationships = 0;

        for (EntitySearchProcessor.DiscoveredEntity discovered : selected) {
            String entityId = discovered.generateId();

            // Create and save entity if it doesn't exist
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

            // Create relationship
            CoinRelationship relationship = createRelationship(
                sourceEntity.id(),
                entityId,
                discovered.relationshipType(),
                discovered.reason()
            );

            if (!store.relationshipExists(relationship.fromId(), relationship.toId(), relationship.type())) {
                store.saveRelationship(relationship, "ai-discovery");
                relationships++;
            }
        }

        JOptionPane.showMessageDialog(this,
            "Added " + added + " entities and " + relationships + " relationships.",
            "Success",
            JOptionPane.INFORMATION_MESSAGE);

        // Refresh results to show entities as existing
        if (!lastResults.isEmpty()) {
            displayResults(lastResults);
        }
    }

    private CoinRelationship createRelationship(String sourceId, String targetId,
                                                 CoinRelationship.Type relType, String note) {
        // Determine direction based on relationship type and entity types
        return switch (relType) {
            case ETF_TRACKS, ETP_TRACKS ->
                // ETF tracks COIN: ETF -> COIN
                new CoinRelationship(targetId, sourceId, relType, note);
            case INVESTED_IN ->
                // VC invested in COIN: VC -> COIN
                sourceEntity.type() == CoinEntity.Type.VC
                    ? new CoinRelationship(sourceId, targetId, relType, note)
                    : new CoinRelationship(targetId, sourceId, relType, note);
            case L2_OF ->
                // L2 built on L1: L2 -> L1
                sourceEntity.type() == CoinEntity.Type.L2
                    ? new CoinRelationship(sourceId, targetId, relType, note)
                    : new CoinRelationship(targetId, sourceId, relType, note);
            default ->
                // Default: source -> target
                new CoinRelationship(sourceId, targetId, relType, note);
        };
    }

    /**
     * Option for relationship type combo box.
     */
    private record RelationshipOption(CoinRelationship.Type type, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
