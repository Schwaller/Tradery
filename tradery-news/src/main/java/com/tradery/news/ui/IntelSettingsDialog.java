package com.tradery.news.ui;

import com.tradery.ai.AiClient;
import com.tradery.ai.AiConfig;
import com.tradery.ai.AiDetector;
import com.tradery.ai.AiSetupDialog;
import com.tradery.ai.AiDetector.DetectedProvider;
import com.tradery.ai.AiProfile;
import com.tradery.ai.AiProvider;
import com.tradery.news.fetch.RssFetcher;
import com.tradery.news.ui.coin.CoinEntity;
import com.tradery.news.ui.coin.EntityStore;
import com.tradery.news.ui.coin.SchemaRegistry;
import com.tradery.news.ui.coin.SchemaType;
import com.tradery.ui.settings.SettingsDialog;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Settings dialog for the Intelligence app.
 * Extends the shared base for header, Appearance section, and button bar.
 * Adds News Sources, AI Provider, and ERD Rendering sections.
 */
public class IntelSettingsDialog extends SettingsDialog {

    public IntelSettingsDialog(Window owner) {
        super(owner);
    }

    private EntityStore getEntityStore() {
        if (getOwner() instanceof IntelDocumentFrame frame) return frame.getEntityStore();
        if (getOwner() instanceof IntelFrame frame) return frame.getEntityStore();
        return null;
    }

    private SchemaRegistry getSchemaRegistry() {
        if (getOwner() instanceof IntelDocumentFrame frame) return frame.getSchemaRegistry();
        if (getOwner() instanceof IntelFrame frame) return frame.getSchemaRegistry();
        return null;
    }

    private DocumentServices getDocumentServices() {
        if (getOwner() instanceof IntelDocumentFrame frame) return frame.getDocumentServices();
        return null;
    }

    private Path getDocDir() {
        if (getOwner() instanceof IntelDocumentFrame frame) return frame.getDocDir();
        return null;
    }

    @Override
    protected List<SectionEntry> addSections() {
        return List.of(
            new SectionEntry("Panels", createPanelsContent()),
            new SectionEntry("News Sources", createNewsSourcesContent()),
            new SectionEntry("AI Profiles", createAiProfilesContent()),
            new SectionEntry("ERD Rendering", createErdContent())
        );
    }

    // --- Panels ---

    private JPanel createPanelsContent() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        IntelConfig config = IntelConfig.get();

        DefaultListModel<PanelConfig> listModel = new DefaultListModel<>();
        JList<PanelConfig> panelList = new JList<>(listModel);
        panelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        Runnable loadPanels = () -> {
            listModel.clear();
            for (PanelConfig p : config.getPanels()) {
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
        JButton addBtn = new JButton("Add...");
        JButton editBtn = new JButton("Edit...");
        JButton removeBtn = new JButton("Remove");
        JButton moveUpBtn = new JButton("Move Up");
        JButton moveDownBtn = new JButton("Move Down");

        addBtn.addActionListener(e -> {
            PanelConfig newPanel = showPanelEditor(null);
            if (newPanel != null) {
                config.addPanel(newPanel);
                config.save();
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
                config.save();
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
            if (config.getPanels().size() <= 1) {
                JOptionPane.showMessageDialog(this, "Cannot remove the last panel.", "Cannot Remove", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int result = JOptionPane.showConfirmDialog(this,
                "Remove panel '" + selected.getName() + "'?",
                "Confirm Remove", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                config.removePanel(selected.getId());
                config.save();
                loadPanels.run();
                notifyPanelsChanged();
            }
        });

        moveUpBtn.addActionListener(e -> {
            int idx = panelList.getSelectedIndex();
            if (idx <= 0) return;
            List<PanelConfig> panels = config.getPanels();
            Collections.swap(panels, idx, idx - 1);
            config.save();
            loadPanels.run();
            panelList.setSelectedIndex(idx - 1);
            notifyPanelsChanged();
        });

        moveDownBtn.addActionListener(e -> {
            int idx = panelList.getSelectedIndex();
            List<PanelConfig> panels = config.getPanels();
            if (idx < 0 || idx >= panels.size() - 1) return;
            Collections.swap(panels, idx, idx + 1);
            config.save();
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
        labelGbc.anchor = GridBagConstraints.WEST; // restore

        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        fieldGbc.fill = GridBagConstraints.BOTH;
        fieldGbc.weighty = 1.0;

        // Initialize bands list from existing config or defaults
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
            if (newBand != null) {
                bandsModel.addElement(newBand);
            }
        });
        bandEditBtn.addActionListener(ev -> {
            int idx = bandsList.getSelectedIndex();
            if (idx < 0) return;
            BandConfig edited = showBandEditor(dialog, bandsModel.get(idx));
            if (edited != null) {
                bandsModel.set(idx, edited);
            }
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

        // Build entity type checkboxes from schema registry
        JPanel typesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        typesPanel.setOpaque(false);
        Map<String, JCheckBox> typeCheckboxes = new LinkedHashMap<>();
        Set<String> existingFilter = existing != null ? existing.getEntityTypeFilter() : null;
        SchemaRegistry registry = getSchemaRegistry();
        if (registry != null) {
            for (SchemaType st : registry.entityTypes()) {
                JCheckBox cb = new JCheckBox(st.name());
                cb.setSelected(existingFilter == null || existingFilter.contains(st.id()));
                typeCheckboxes.put(st.id(), cb);
                typesPanel.add(cb);
            }
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
        if (registry != null) {
            for (SchemaType rt : registry.relationshipTypes()) {
                JCheckBox cb = new JCheckBox(rt.label() != null ? rt.label() : rt.name());
                cb.setSelected(existingRelFilter == null || existingRelFilter.contains(rt.id()));
                relTypeCheckboxes.put(rt.id(), cb);
                relTypesPanel.add(cb);
            }
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

                // Entity source filter
                int sourceIdx = sourceCombo.getSelectedIndex();
                if (sourceIdx == 1) pc.setEntitySourceFilter(Set.of("coingecko"));
                else if (sourceIdx == 2) pc.setEntitySourceFilter(Set.of("manual"));

                // Relationship type filter (null = all)
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

        // Filter (populated from SchemaRegistry entity types + "articles")
        lc.gridx = 0; lc.gridy = r;
        form.add(new JLabel("Filter:"), lc);
        fc.gridx = 1; fc.gridy = r++;
        JComboBox<String> filterCombo = new JComboBox<>();
        filterCombo.addItem("articles");
        // Built-in article field extractors (always available)
        Set<String> addedFilters = new HashSet<>();
        for (String builtIn : List.of("topic", "coin", "category", "tag")) {
            filterCombo.addItem(builtIn);
            addedFilters.add(builtIn);
        }
        // Schema entity types (may overlap with built-ins)
        SchemaRegistry registry = getSchemaRegistry();
        if (registry != null) {
            for (SchemaType st : registry.entityTypes()) {
                if (!addedFilters.contains(st.id())) {
                    filterCombo.addItem(st.id());
                }
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

            // Update yField options based on filter
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

        // Pre-select existing yField
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
        } else if (getOwner() instanceof IntelFrame frame) {
            frame.rebuildPanels();
        }
    }

    // --- News Sources ---

    private JPanel createNewsSourcesContent() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));

        IntelConfig config0 = IntelConfig.get();
        DefaultListModel<RssFetcher> listModel = new DefaultListModel<>();
        JList<RssFetcher> sourceList = new JList<>(listModel);
        sourceList.setFixedCellHeight(-1);
        sourceList.setCellRenderer(new FeedCellRenderer(config0));
        sourceList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int index = sourceList.locationToIndex(e.getPoint());
                if (index < 0) return;
                Rectangle bounds = sourceList.getCellBounds(index, index);
                if (bounds == null) return;
                // Toggle if click is within checkbox area (first ~30px)
                if (e.getX() - bounds.x < 30) {
                    RssFetcher fetcher = listModel.get(index);
                    String id = fetcher.getSourceId();
                    config0.setFeedDisabled(id, !config0.isFeedDisabled(id));
                    config0.save();
                    sourceList.repaint();
                }
            }
        });

        Runnable loadSources = () -> {
            listModel.clear();
            for (RssFetcher fetcher : RssFetcher.defaultSources()) {
                listModel.addElement(fetcher);
            }
            if (getEntityStore() != null) {
                for (CoinEntity entity : getEntityStore().loadEntitiesBySource("manual")) {
                    if (entity.type() == CoinEntity.Type.NEWS_SOURCE && entity.symbol() != null) {
                        listModel.addElement(new RssFetcher(
                            entity.id().replace("rss-", ""),
                            entity.name(),
                            entity.symbol()
                        ));
                    }
                }
            }
        };
        loadSources.run();

        JScrollPane scroll = new JScrollPane(sourceList);
        scroll.setPreferredSize(new Dimension(0, 180));
        scroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        panel.add(scroll, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton addBtn = new JButton("Add Feed...");
        JButton removeBtn = new JButton("Remove");
        JButton resetBtn = new JButton("Reset to Defaults");

        addBtn.addActionListener(e -> {
            JPanel form = new JPanel(new GridLayout(2, 2, 5, 5));
            JTextField nameField = new JTextField();
            JTextField urlField = new JTextField();
            form.add(new JLabel("Name:"));
            form.add(nameField);
            form.add(new JLabel("RSS URL:"));
            form.add(urlField);

            int result = JOptionPane.showConfirmDialog(this, form, "Add RSS Feed",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION && getEntityStore() != null) {
                String name = nameField.getText().trim();
                String url = urlField.getText().trim();
                if (name.isEmpty() || url.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "All fields are required.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                // Check for duplicate URL
                for (int i = 0; i < listModel.getSize(); i++) {
                    if (listModel.get(i).getFeedUrl().equalsIgnoreCase(url)) {
                        JOptionPane.showMessageDialog(this, "A feed with this URL already exists.", "Duplicate", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                }
                String id = hashUrl(url);
                getEntityStore().saveEntity(new CoinEntity("rss-" + id, name, url, CoinEntity.Type.NEWS_SOURCE), "manual");
                loadSources.run();
            }
        });

        removeBtn.addActionListener(e -> {
            RssFetcher selected = sourceList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Select a feed to remove.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String entityId = "rss-" + selected.getSourceId();
            if (getEntityStore() == null || !getEntityStore().entityExists(entityId)) {
                JOptionPane.showMessageDialog(this, "Built-in feeds cannot be removed.", "Cannot Remove", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int result = JOptionPane.showConfirmDialog(this,
                "Remove RSS feed '" + selected.getSourceName() + "'?",
                "Confirm Remove", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                getEntityStore().deleteEntity(entityId);
                loadSources.run();
            }
        });

        resetBtn.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(this,
                "Reset RSS feeds to factory defaults?\nThis will remove any custom feeds.",
                "Reset to Defaults", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION && getEntityStore() != null) {
                for (CoinEntity entity : getEntityStore().loadEntitiesBySource("manual")) {
                    if (entity.type() == CoinEntity.Type.NEWS_SOURCE) {
                        getEntityStore().deleteEntity(entity.id());
                    }
                }
                loadSources.run();
            }
        });

        buttonPanel.add(addBtn);
        buttonPanel.add(removeBtn);
        buttonPanel.add(resetBtn);

        // Update interval
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 8));
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);

        JPanel intervalPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        intervalPanel.add(new JLabel("Update interval:"));

        String[] intervals = {"Manual only", "2 minutes", "3 minutes", "5 minutes", "15 minutes", "30 minutes", "1 hour"};
        int[] intervalValues = {0, 2, 3, 5, 15, 30, 60};
        JComboBox<String> intervalCombo = new JComboBox<>(intervals);

        IntelConfig config = IntelConfig.get();
        int currentInterval = config.getFetchIntervalMinutes();
        for (int i = 0; i < intervalValues.length; i++) {
            if (intervalValues[i] == currentInterval) {
                intervalCombo.setSelectedIndex(i);
                break;
            }
        }
        intervalCombo.addActionListener(e -> {
            int idx = intervalCombo.getSelectedIndex();
            if (idx >= 0 && idx < intervalValues.length) {
                config.setFetchIntervalMinutes(intervalValues[idx]);
                config.save();
                if (getOwner() instanceof IntelDocumentFrame frame) {
                    frame.updateAutoFetchTimer();
                } else if (getOwner() instanceof IntelFrame frame) {
                    frame.updateAutoFetchTimer();
                }
            }
        });
        intervalPanel.add(intervalCombo);
        bottomPanel.add(intervalPanel, BorderLayout.SOUTH);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    // --- AI Profiles ---

    private JPanel createAiProfilesContent() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        AiConfig aiConfig = AiConfig.get();

        DefaultListModel<AiProfile> listModel = new DefaultListModel<>();
        JList<AiProfile> profileList = new JList<>(listModel);
        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        Runnable loadProfiles = () -> {
            listModel.clear();
            for (AiProfile p : aiConfig.getProfiles()) {
                listModel.addElement(p);
            }
        };
        loadProfiles.run();

        profileList.setCellRenderer(new ProfileCellRenderer(aiConfig));

        JScrollPane scroll = new JScrollPane(profileList);
        scroll.setPreferredSize(new Dimension(0, 140));
        scroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        panel.add(scroll, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton addBtn = new JButton("Add...");
        JButton editBtn = new JButton("Edit...");
        JButton removeBtn = new JButton("Remove");
        JButton setDefaultBtn = new JButton("Set Default");

        addBtn.addActionListener(e -> {
            AiProfile newProfile = showProfileEditor(null);
            if (newProfile != null) {
                aiConfig.addProfile(newProfile);
                if (aiConfig.getProfiles().size() == 1) {
                    aiConfig.setDefaultProfileId(newProfile.getId());
                }
                aiConfig.save();
                loadProfiles.run();
            }
        });

        editBtn.addActionListener(e -> {
            AiProfile selected = profileList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Select a profile to edit.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            AiProfile edited = showProfileEditor(selected);
            if (edited != null) {
                // Update in-place
                selected.setName(edited.getName());
                selected.setDescription(edited.getDescription());
                selected.setProvider(edited.getProvider());
                selected.setPath(edited.getPath());
                selected.setArgs(edited.getArgs());
                selected.setCommand(edited.getCommand());
                selected.setApiKey(edited.getApiKey());
                selected.setModel(edited.getModel());
                selected.setTimeoutSeconds(edited.getTimeoutSeconds());
                aiConfig.save();
                loadProfiles.run();
            }
        });

        removeBtn.addActionListener(e -> {
            AiProfile selected = profileList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Select a profile to remove.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (aiConfig.getProfiles().size() <= 1) {
                JOptionPane.showMessageDialog(this, "Cannot remove the last profile.", "Cannot Remove", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int result = JOptionPane.showConfirmDialog(this,
                "Remove profile '" + selected.getName() + "'?",
                "Confirm Remove", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                aiConfig.removeProfile(selected.getId());
                aiConfig.save();
                loadProfiles.run();
            }
        });

        setDefaultBtn.addActionListener(e -> {
            AiProfile selected = profileList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Select a profile to set as default.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            aiConfig.setDefaultProfileId(selected.getId());
            aiConfig.save();
            loadProfiles.run();
        });

        JButton autoDetectBtn = new JButton("Auto-Detect...");
        autoDetectBtn.addActionListener(e -> {
            autoDetectBtn.setEnabled(false);
            autoDetectBtn.setText("Detecting...");
            new SwingWorker<List<DetectedProvider>, Void>() {
                @Override
                protected List<DetectedProvider> doInBackground() {
                    return AiDetector.detectAll();
                }

                @Override
                protected void done() {
                    autoDetectBtn.setEnabled(true);
                    autoDetectBtn.setText("Auto-Detect...");
                    try {
                        List<DetectedProvider> allProviders = get();
                        // Filter out providers that already have a matching profile
                        List<DetectedProvider> newProviders = new ArrayList<>();
                        for (DetectedProvider dp : allProviders) {
                            if (!dp.detected() || dp.requiresSetup()) continue;
                            boolean exists = false;
                            for (AiProfile existing : aiConfig.getProfiles()) {
                                if (existing.getProvider() == dp.provider()) {
                                    if (dp.provider() == AiProvider.CUSTOM) {
                                        // Match by command for custom providers (e.g. Ollama models)
                                        if (dp.command() != null && dp.command().equals(existing.getCommand())) {
                                            exists = true;
                                            break;
                                        }
                                    } else if (dp.provider() == AiProvider.CLAUDE || dp.provider() == AiProvider.CODEX) {
                                        // Match by args for providers with multiple model tiers
                                        if (dp.args() != null && dp.args().equals(existing.getArgs())) {
                                            exists = true;
                                            break;
                                        }
                                    } else {
                                        exists = true;
                                        break;
                                    }
                                }
                            }
                            if (!exists) newProviders.add(dp);
                        }

                        if (newProviders.isEmpty()) {
                            JOptionPane.showMessageDialog(IntelSettingsDialog.this,
                                "No new AI providers found.",
                                "Auto-Detect", JOptionPane.INFORMATION_MESSAGE);
                            return;
                        }

                        showAutoDetectResults(newProviders, aiConfig, loadProfiles);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(IntelSettingsDialog.this,
                            "Detection failed: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        JButton resetBtn = new JButton("Reset...");
        resetBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                "This will remove all AI profiles and re-run the initial setup.\nContinue?",
                "Reset AI Profiles", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
            aiConfig.getProfiles().clear();
            aiConfig.setDefaultProfileId(null);
            aiConfig.save();
            loadProfiles.run();
            AiSetupDialog.showSetup(this);
            loadProfiles.run();
        });

        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(removeBtn);
        buttonPanel.add(setDefaultBtn);
        buttonPanel.add(autoDetectBtn);
        buttonPanel.add(resetBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void showAutoDetectResults(List<DetectedProvider> newProviders, AiConfig aiConfig, Runnable loadProfiles) {
        JDialog dialog = new JDialog(this, "New Providers Detected", true);
        dialog.setSize(420, 300);
        dialog.setLocationRelativeTo(this);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));

        content.add(new JLabel("Select providers to add as profiles:"), BorderLayout.NORTH);

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        List<JCheckBox> checkboxes = new ArrayList<>();

        for (DetectedProvider dp : newProviders) {
            JCheckBox cb = new JCheckBox(dp.name() + " — " + dp.description());
            cb.setSelected(true);
            checkboxes.add(cb);
            listPanel.add(cb);
            listPanel.add(Box.createVerticalStrut(4));
        }

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        content.add(scroll, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        buttonPanel.add(cancelBtn);

        JButton addBtn = new JButton("Add Selected");
        addBtn.addActionListener(e -> {
            boolean hadNoDefault = aiConfig.getDefaultProfileId() == null;
            String firstAddedId = null;
            for (int i = 0; i < checkboxes.size(); i++) {
                if (!checkboxes.get(i).isSelected()) continue;
                DetectedProvider dp = newProviders.get(i);
                AiProfile profile = new AiProfile();
                String id = generateProfileId(dp.name());
                profile.setId(id);
                profile.setName(dp.name().replaceAll("\\s+v[\\d.]+$", "").trim());
                profile.setProvider(dp.provider());
                if (dp.path() != null) profile.setPath(dp.path());
                if (dp.args() != null) profile.setArgs(dp.args());
                if (dp.command() != null) profile.setCommand(dp.command());
                aiConfig.addProfile(profile);
                if (firstAddedId == null) firstAddedId = id;
            }
            if (hadNoDefault && firstAddedId != null) {
                aiConfig.setDefaultProfileId(firstAddedId);
            }
            aiConfig.save();
            loadProfiles.run();
            dialog.dispose();
        });
        buttonPanel.add(addBtn);
        content.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.setVisible(true);
    }

    private AiProfile showProfileEditor(AiProfile existing) {
        JDialog dialog = new JDialog(this, existing != null ? "Edit Profile" : "Add Profile", true);
        dialog.setSize(480, 420);
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

        // Description
        labelGbc.gridx = 0; labelGbc.gridy = row;
        formPanel.add(new JLabel("Description:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JTextField descField = new JTextField(existing != null && existing.getDescription() != null ? existing.getDescription() : "");
        formPanel.add(descField, fieldGbc);

        // Provider
        labelGbc.gridx = 0; labelGbc.gridy = row;
        formPanel.add(new JLabel("Provider:"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JComboBox<AiProvider> providerCombo = new JComboBox<>(AiProvider.values());
        providerCombo.setSelectedItem(existing != null ? existing.getProvider() : AiProvider.CLAUDE);
        formPanel.add(providerCombo, fieldGbc);

        // CLI path
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel pathLabel = new JLabel("CLI path:");
        formPanel.add(pathLabel, labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JTextField pathField = new JTextField(existing != null ? existing.getPath() : "claude");
        formPanel.add(pathField, fieldGbc);

        // CLI args
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel argsLabel = new JLabel("CLI args:");
        formPanel.add(argsLabel, labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JTextField argsField = new JTextField(existing != null ? existing.getArgs() : "--print --output-format text --model haiku");
        formPanel.add(argsField, fieldGbc);

        // Custom command
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel commandLabel = new JLabel("Command:");
        formPanel.add(commandLabel, labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JTextField commandField = new JTextField(existing != null ? existing.getCommand() : "");
        formPanel.add(commandField, fieldGbc);

        // Gemini API key
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel apiKeyLabel = new JLabel("API Key:");
        formPanel.add(apiKeyLabel, labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JPasswordField apiKeyField = new JPasswordField(existing != null ? existing.getApiKey() : "");
        formPanel.add(apiKeyField, fieldGbc);

        // Gemini model
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel modelLabel = new JLabel("Model:");
        formPanel.add(modelLabel, labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JComboBox<String> modelCombo = new JComboBox<>(new String[]{
            "gemini-2.5-flash-lite", "gemini-2.5-flash", "gemini-2.0-flash"
        });
        modelCombo.setEditable(true);
        modelCombo.setSelectedItem(existing != null ? existing.getModel() : "gemini-2.5-flash-lite");
        formPanel.add(modelCombo, fieldGbc);

        // Gemini help
        labelGbc.gridx = 0; labelGbc.gridy = row;
        formPanel.add(new JLabel(), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JLabel geminiHelp = new JLabel("<html><small>Free API key from aistudio.google.com</small></html>");
        geminiHelp.setForeground(UIManager.getColor("Label.disabledForeground"));
        formPanel.add(geminiHelp, fieldGbc);

        // Timeout
        labelGbc.gridx = 0; labelGbc.gridy = row;
        formPanel.add(new JLabel("Timeout (sec):"), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JSpinner timeoutSpinner = new JSpinner(new SpinnerNumberModel(
            existing != null ? existing.getTimeoutSeconds() : 60, 10, 300, 10));
        formPanel.add(timeoutSpinner, fieldGbc);

        // Test log area
        labelGbc.gridx = 0; labelGbc.gridy = row;
        formPanel.add(new JLabel(), labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;
        JTextArea testLogArea = new JTextArea(3, 40);
        testLogArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        testLogArea.setEditable(false);
        testLogArea.setLineWrap(true);
        testLogArea.setVisible(false);
        JScrollPane testLogScroll = new JScrollPane(testLogArea);
        testLogScroll.setBorder(UIManager.getBorder("ScrollPane.border"));
        testLogScroll.setVisible(false);
        formPanel.add(testLogScroll, fieldGbc);

        // Visibility updater
        Runnable updateVisibility = () -> {
            AiProvider selected = (AiProvider) providerCombo.getSelectedItem();
            boolean isCli = selected == AiProvider.CLAUDE || selected == AiProvider.CODEX;
            pathLabel.setVisible(isCli);
            pathField.setVisible(isCli);
            argsLabel.setVisible(isCli);
            argsField.setVisible(isCli);
            commandLabel.setVisible(selected == AiProvider.CUSTOM);
            commandField.setVisible(selected == AiProvider.CUSTOM);
            apiKeyLabel.setVisible(selected == AiProvider.GEMINI);
            apiKeyField.setVisible(selected == AiProvider.GEMINI);
            modelLabel.setVisible(selected == AiProvider.GEMINI);
            modelCombo.setVisible(selected == AiProvider.GEMINI);
            geminiHelp.setVisible(selected == AiProvider.GEMINI);
            formPanel.revalidate();
        };
        providerCombo.addActionListener(e -> updateVisibility.run());
        updateVisibility.run();

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(8, 16, 12, 16));
        JButton testBtn = new JButton("Test Connection");
        JButton cancelBtn = new JButton("Cancel");
        JButton saveBtn = new JButton("Save");

        AiProfile[] result = {null};

        testBtn.addActionListener(e -> {
            testLogArea.setVisible(true);
            testLogScroll.setVisible(true);
            testLogArea.setText("");
            formPanel.revalidate();
            dialog.pack();

            AiProfile tempProfile = buildProfileFromForm(
                existing != null ? existing.getId() : null,
                nameField, descField, providerCombo, pathField, argsField,
                commandField, apiKeyField, modelCombo, timeoutSpinner);

            testBtn.setEnabled(false);
            new Thread(() -> {
                try {
                    Consumer<String> log = msg -> SwingUtilities.invokeLater(() -> {
                        testLogArea.append(msg + "\n");
                        testLogArea.setCaretPosition(testLogArea.getDocument().getLength());
                    });

                    log.accept("Testing " + tempProfile.getProvider() + " profile...");
                    AiClient.TestResult testResult = AiClient.getInstance().testConnection(tempProfile);
                    if (testResult.version() != null) {
                        log.accept("Version: " + testResult.version());
                    }
                    log.accept(testResult.success() ? "Connection working!" : "Failed: " + testResult.message());
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> testLogArea.append("Error: " + ex.getMessage() + "\n"));
                } finally {
                    SwingUtilities.invokeLater(() -> testBtn.setEnabled(true));
                }
            }).start();
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        saveBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Name is required.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            result[0] = buildProfileFromForm(
                existing != null ? existing.getId() : generateProfileId(name),
                nameField, descField, providerCombo, pathField, argsField,
                commandField, apiKeyField, modelCombo, timeoutSpinner);
            dialog.dispose();
        });

        buttonPanel.add(testBtn);
        buttonPanel.add(cancelBtn);
        buttonPanel.add(saveBtn);

        dialog.setLayout(new BorderLayout());
        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setVisible(true);

        return result[0];
    }

    private AiProfile buildProfileFromForm(String id, JTextField nameField, JTextField descField,
                                            JComboBox<AiProvider> providerCombo,
                                            JTextField pathField, JTextField argsField,
                                            JTextField commandField, JPasswordField apiKeyField,
                                            JComboBox<String> modelCombo, JSpinner timeoutSpinner) {
        AiProfile profile = new AiProfile();
        profile.setId(id);
        profile.setName(nameField.getText().trim());
        String desc = descField.getText().trim();
        profile.setDescription(desc.isEmpty() ? null : desc);
        profile.setProvider((AiProvider) providerCombo.getSelectedItem());
        profile.setPath(pathField.getText().trim());
        profile.setArgs(argsField.getText().trim());
        profile.setCommand(commandField.getText().trim());
        profile.setApiKey(new String(apiKeyField.getPassword()).trim());
        profile.setModel((String) modelCombo.getSelectedItem());
        profile.setTimeoutSeconds((Integer) timeoutSpinner.getValue());
        return profile;
    }

    private String generateProfileId(String name) {
        return name.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    }

    // --- ERD Rendering ---

    private JPanel createErdContent() {
        JPanel panel = new JPanel(new GridBagLayout());
        IntelConfig config = IntelConfig.get();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 0, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;

        JCheckBox flowModeCheck = new JCheckBox("Flow mode (relationship connections widen through boxes)");
        flowModeCheck.setSelected(config.isErdFlowMode());
        flowModeCheck.addActionListener(e -> {
            config.setErdFlowMode(flowModeCheck.isSelected());
            config.save();
        });
        panel.add(flowModeCheck, gbc);

        return panel;
    }

    private static String hashUrl(String url) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(url.hashCode());
        }
    }

    // --- Reusable cell renderers (rubber-stamp pattern) ---

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

    private static class FeedCellRenderer extends JPanel implements ListCellRenderer<RssFetcher> {
        private final JCheckBox cb = new JCheckBox();
        private final JLabel nameLabel = new JLabel();
        private final JLabel urlLabel = new JLabel();
        private final IntelConfig config;

        FeedCellRenderer(IntelConfig config) {
            this.config = config;
            setLayout(new BorderLayout(6, 0));
            setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            cb.setOpaque(false);
            add(cb, BorderLayout.WEST);

            JPanel textPanel = new JPanel(new BorderLayout());
            textPanel.setOpaque(false);
            textPanel.add(nameLabel, BorderLayout.NORTH);
            urlLabel.setFont(urlLabel.getFont().deriveFont(urlLabel.getFont().getSize2D() - 1f));
            urlLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            textPanel.add(urlLabel, BorderLayout.SOUTH);
            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends RssFetcher> list, RssFetcher fetcher,
                                                       int index, boolean isSelected, boolean cellHasFocus) {
            boolean disabled = config.isFeedDisabled(fetcher.getSourceId());
            cb.setSelected(!disabled);
            nameLabel.setText(fetcher.getSourceName());
            nameLabel.setForeground(disabled
                ? UIManager.getColor("Label.disabledForeground")
                : (isSelected ? list.getSelectionForeground() : list.getForeground()));
            urlLabel.setText(fetcher.getFeedUrl());
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            return this;
        }
    }

    private static class ProfileCellRenderer extends JPanel implements ListCellRenderer<AiProfile> {
        private final JLabel nameLabel = new JLabel();
        private final JLabel descLabel = new JLabel();
        private final AiConfig aiConfig;

        ProfileCellRenderer(AiConfig aiConfig) {
            this.aiConfig = aiConfig;
            setLayout(new BorderLayout(4, 0));
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
            descLabel.setFont(descLabel.getFont().deriveFont(descLabel.getFont().getSize2D() - 1f));
            descLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

            add(nameLabel, BorderLayout.NORTH);
            add(descLabel, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends AiProfile> list, AiProfile profile,
                                                       int index, boolean isSelected, boolean cellHasFocus) {
            boolean isDefault = profile.getId() != null && profile.getId().equals(aiConfig.getDefaultProfileId());
            String name = (isDefault ? "\u2605 " : "") + profile.getName();
            String provider = " [" + profile.getProvider() + "]";
            nameLabel.setText(name + provider);
            descLabel.setVisible(profile.getDescription() != null && !profile.getDescription().isEmpty());
            descLabel.setText(profile.getDescription() != null ? profile.getDescription() : "");
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            nameLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            setOpaque(true);
            return this;
        }
    }
}
