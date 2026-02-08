package com.tradery.news.ui;

import com.tradery.news.ai.AiClient;
import com.tradery.news.ai.AiDetector;
import com.tradery.news.ai.AiDetector.DetectedProvider;
import com.tradery.news.ai.AiProfile;
import com.tradery.news.fetch.RssFetcher;
import com.tradery.news.ui.coin.CoinEntity;
import com.tradery.news.ui.coin.EntityStore;
import com.tradery.ui.settings.SettingsDialog;

import javax.swing.*;
import java.awt.*;
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
        return (getOwner() instanceof IntelFrame frame) ? frame.getEntityStore() : null;
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

        panelList.setCellRenderer(new ListCellRenderer<>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends PanelConfig> list, PanelConfig pc,
                                                           int index, boolean isSelected, boolean cellHasFocus) {
                JPanel cell = new JPanel(new BorderLayout(8, 0));
                cell.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                cell.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

                JLabel nameLabel = new JLabel(pc.getName());
                nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
                nameLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());

                String badge = pc.getType() == PanelConfig.PanelType.NEWS_MAP ? "News Map" : "Coin Graph";
                JLabel typeLabel = new JLabel(badge);
                typeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
                typeLabel.setFont(typeLabel.getFont().deriveFont(typeLabel.getFont().getSize2D() - 1f));

                JPanel textPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
                textPanel.setOpaque(false);
                textPanel.add(nameLabel);
                textPanel.add(typeLabel);
                cell.add(textPanel, BorderLayout.CENTER);

                return cell;
            }
        });

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
                selected.setEntityTypeFilter(edited.getEntityTypeFilter());
                selected.setEntitySourceFilter(edited.getEntitySourceFilter());
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
        dialog.setSize(420, 380);
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

        // --- Coin Graph settings ---
        labelGbc.gridx = 0; labelGbc.gridy = row;
        JLabel entityTypeLabel = new JLabel("Entity types:");
        formPanel.add(entityTypeLabel, labelGbc);
        fieldGbc.gridx = 1; fieldGbc.gridy = row++;

        // Build entity type checkboxes from schema registry
        String[] entityTypeNames = {"coin", "l2", "etf", "etp", "dat", "vc", "exchange", "foundation", "company"};
        JPanel typesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        typesPanel.setOpaque(false);
        Map<String, JCheckBox> typeCheckboxes = new LinkedHashMap<>();
        Set<String> existingFilter = existing != null ? existing.getEntityTypeFilter() : null;
        for (String typeName : entityTypeNames) {
            JCheckBox cb = new JCheckBox(typeName);
            cb.setSelected(existingFilter == null || existingFilter.contains(typeName));
            typeCheckboxes.put(typeName, cb);
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
            entityTypeLabel.setVisible(!isNewsMap);
            typesPanel.setVisible(!isNewsMap);
            sourceFilterLabel.setVisible(!isNewsMap);
            sourceCombo.setVisible(!isNewsMap);
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

    private String generatePanelId(String name) {
        return name.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "")
            + "-" + System.currentTimeMillis() % 10000;
    }

    private void notifyPanelsChanged() {
        if (getOwner() instanceof IntelFrame frame) {
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
        sourceList.setCellRenderer(new ListCellRenderer<>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends RssFetcher> list, RssFetcher fetcher,
                                                           int index, boolean isSelected, boolean cellHasFocus) {
                JPanel cell = new JPanel(new BorderLayout(6, 0));
                cell.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
                cell.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

                JCheckBox cb = new JCheckBox();
                cb.setSelected(!config0.isFeedDisabled(fetcher.getSourceId()));
                cb.setOpaque(false);
                cell.add(cb, BorderLayout.WEST);

                JPanel textPanel = new JPanel(new BorderLayout());
                textPanel.setOpaque(false);

                JLabel nameLabel = new JLabel(fetcher.getSourceName());
                boolean disabled = config0.isFeedDisabled(fetcher.getSourceId());
                nameLabel.setForeground(disabled
                    ? UIManager.getColor("Label.disabledForeground")
                    : (isSelected ? list.getSelectionForeground() : list.getForeground()));
                textPanel.add(nameLabel, BorderLayout.NORTH);

                JLabel urlLabel = new JLabel(fetcher.getFeedUrl());
                urlLabel.setFont(urlLabel.getFont().deriveFont(urlLabel.getFont().getSize2D() - 1f));
                urlLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
                textPanel.add(urlLabel, BorderLayout.SOUTH);

                cell.add(textPanel, BorderLayout.CENTER);

                return cell;
            }
        });
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
                if (getOwner() instanceof IntelFrame frame) {
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
        IntelConfig config = IntelConfig.get();

        DefaultListModel<AiProfile> listModel = new DefaultListModel<>();
        JList<AiProfile> profileList = new JList<>(listModel);
        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        Runnable loadProfiles = () -> {
            listModel.clear();
            for (AiProfile p : config.getAiProfiles()) {
                listModel.addElement(p);
            }
        };
        loadProfiles.run();

        profileList.setCellRenderer(new ListCellRenderer<>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends AiProfile> list, AiProfile profile,
                                                           int index, boolean isSelected, boolean cellHasFocus) {
                JPanel cell = new JPanel(new BorderLayout(8, 0));
                cell.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                cell.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

                boolean isDefault = profile.getId() != null && profile.getId().equals(config.getDefaultProfileId());

                JPanel textPanel = new JPanel(new BorderLayout());
                textPanel.setOpaque(false);

                String nameText = (isDefault ? "\u2605 " : "") + profile.getName();
                JLabel nameLabel = new JLabel(nameText);
                nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
                nameLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());

                JLabel providerLabel = new JLabel("[" + profile.getProvider() + "]");
                providerLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
                providerLabel.setFont(providerLabel.getFont().deriveFont(providerLabel.getFont().getSize2D() - 1f));

                JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
                topRow.setOpaque(false);
                topRow.add(nameLabel);
                topRow.add(providerLabel);
                textPanel.add(topRow, BorderLayout.NORTH);

                if (profile.getDescription() != null && !profile.getDescription().isEmpty()) {
                    JLabel descLabel = new JLabel(profile.getDescription());
                    descLabel.setFont(descLabel.getFont().deriveFont(descLabel.getFont().getSize2D() - 1f));
                    descLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
                    descLabel.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
                    textPanel.add(descLabel, BorderLayout.SOUTH);
                }

                cell.add(textPanel, BorderLayout.CENTER);
                return cell;
            }
        });

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
                config.addProfile(newProfile);
                if (config.getAiProfiles().size() == 1) {
                    config.setDefaultProfileId(newProfile.getId());
                }
                config.save();
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
                config.save();
                loadProfiles.run();
            }
        });

        removeBtn.addActionListener(e -> {
            AiProfile selected = profileList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Select a profile to remove.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (config.getAiProfiles().size() <= 1) {
                JOptionPane.showMessageDialog(this, "Cannot remove the last profile.", "Cannot Remove", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int result = JOptionPane.showConfirmDialog(this,
                "Remove profile '" + selected.getName() + "'?",
                "Confirm Remove", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                config.removeProfile(selected.getId());
                config.save();
                loadProfiles.run();
            }
        });

        setDefaultBtn.addActionListener(e -> {
            AiProfile selected = profileList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Select a profile to set as default.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            config.setDefaultProfileId(selected.getId());
            config.save();
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
                            for (AiProfile existing : config.getAiProfiles()) {
                                if (existing.getProvider() == dp.provider()) {
                                    if (dp.provider() == IntelConfig.AiProvider.CUSTOM) {
                                        if (dp.command() != null && dp.command().equals(existing.getCommand())) {
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

                        showAutoDetectResults(newProviders, config, loadProfiles);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(IntelSettingsDialog.this,
                            "Detection failed: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(removeBtn);
        buttonPanel.add(setDefaultBtn);
        buttonPanel.add(autoDetectBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void showAutoDetectResults(List<DetectedProvider> newProviders, IntelConfig config, Runnable loadProfiles) {
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
                config.addProfile(profile);
            }
            config.save();
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
        JComboBox<IntelConfig.AiProvider> providerCombo = new JComboBox<>(IntelConfig.AiProvider.values());
        providerCombo.setSelectedItem(existing != null ? existing.getProvider() : IntelConfig.AiProvider.CLAUDE);
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
            IntelConfig.AiProvider selected = (IntelConfig.AiProvider) providerCombo.getSelectedItem();
            boolean isCli = selected == IntelConfig.AiProvider.CLAUDE || selected == IntelConfig.AiProvider.CODEX;
            pathLabel.setVisible(isCli);
            pathField.setVisible(isCli);
            argsLabel.setVisible(isCli);
            argsField.setVisible(isCli);
            commandLabel.setVisible(selected == IntelConfig.AiProvider.CUSTOM);
            commandField.setVisible(selected == IntelConfig.AiProvider.CUSTOM);
            apiKeyLabel.setVisible(selected == IntelConfig.AiProvider.GEMINI);
            apiKeyField.setVisible(selected == IntelConfig.AiProvider.GEMINI);
            modelLabel.setVisible(selected == IntelConfig.AiProvider.GEMINI);
            modelCombo.setVisible(selected == IntelConfig.AiProvider.GEMINI);
            geminiHelp.setVisible(selected == IntelConfig.AiProvider.GEMINI);
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
                                            JComboBox<IntelConfig.AiProvider> providerCombo,
                                            JTextField pathField, JTextField argsField,
                                            JTextField commandField, JPasswordField apiKeyField,
                                            JComboBox<String> modelCombo, JSpinner timeoutSpinner) {
        AiProfile profile = new AiProfile();
        profile.setId(id);
        profile.setName(nameField.getText().trim());
        String desc = descField.getText().trim();
        profile.setDescription(desc.isEmpty() ? null : desc);
        profile.setProvider((IntelConfig.AiProvider) providerCombo.getSelectedItem());
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
}
