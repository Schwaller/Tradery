package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.TraderyApp;
import com.tradery.data.CandleStore;
import com.tradery.data.DataConsumer;
import com.tradery.data.DataRequirement;
import com.tradery.data.DataRequirementsTracker;
import com.tradery.engine.HoopPatternEvaluator;
import com.tradery.io.HoopPatternStore;
import com.tradery.model.Candle;
import com.tradery.model.Hoop;
import com.tradery.model.HoopMatchResult;
import com.tradery.model.HoopPattern;
import com.tradery.model.PriceSmoothingType;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interactive editor frame for visually editing hoop patterns with real candle data.
 * Allows drag-to-resize hoop zones and shows pattern matches.
 */
public class InteractiveHoopEditorFrame extends JFrame {

    private static final String[] SYMBOLS = {
        "BTCUSDT", "ETHUSDT", "BNBUSDT", "XRPUSDT", "ADAUSDT",
        "SOLUSDT", "DOGEUSDT", "DOTUSDT", "MATICUSDT", "LTCUSDT"
    };

    private static final String[] TIMEFRAMES = {
        "1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w"
    };

    private static final String[] DURATIONS = {
        "7 days", "14 days", "30 days", "60 days", "90 days"
    };

    private HoopPattern pattern;
    private final HoopPatternStore patternStore;
    private final CandleStore candleStore;
    private List<Candle> candles = new ArrayList<>();

    // UI Components
    private HoopPatternChartPanel chartPanel;
    private JComboBox<String> symbolCombo;
    private JComboBox<String> timeframeCombo;
    private JComboBox<String> durationCombo;
    private JButton loadDataBtn;
    private JButton findMatchesBtn;
    private JToggleButton showMatchesToggle;
    private JComboBox<PriceSmoothingType> smoothingTypeCombo;
    private JSpinner smoothingPeriodSpinner;
    private JLabel smoothingPeriodLabel;
    private JSlider priceOpacitySlider;
    private JLabel priceOpacityLabel;
    private JLabel statusLabel;
    private JList<String> hoopList;
    private DefaultListModel<String> hoopListModel;

    // Pattern list (left panel)
    private JList<HoopPattern> patternList;
    private DefaultListModel<HoopPattern> patternListModel;
    private JButton newPatternBtn;
    private JButton deletePatternBtn;

    // Properties panel
    private JTextField nameField;
    private JSpinner minPercentSpinner;
    private JSpinner maxPercentSpinner;
    private JSpinner distanceSpinner;
    private JSpinner toleranceSpinner;
    private JComboBox<Hoop.AnchorMode> anchorModeCombo;

    private boolean suppressPropertyChanges = false;
    private Timer autoSaveTimer;
    private boolean ignoringFileChanges = false;

    public InteractiveHoopEditorFrame(HoopPatternStore patternStore) {
        super("Hoop Patterns - " + TraderyApp.APP_NAME);
        this.patternStore = patternStore;
        this.candleStore = ApplicationContext.getInstance().getCandleStore();

        initializeFrame();
        initializeComponents();
        layoutComponents();
        setupAutoSave();
        loadPatterns();
    }

    private void initializeFrame() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(800, 500));

        // Auto-load data when frame becomes visible
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                // Delay slightly to let UI render first
                SwingUtilities.invokeLater(() -> loadCandles());
            }
        });
    }

    private void initializeComponents() {
        // Pattern list (left panel)
        patternListModel = new DefaultListModel<>();
        patternList = new JList<>(patternListModel);
        patternList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        patternList.setCellRenderer(new PatternCellRenderer());
        patternList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onPatternSelected();
            }
        });

        newPatternBtn = new JButton("New");
        newPatternBtn.addActionListener(e -> createPattern());

        deletePatternBtn = new JButton("Delete");
        deletePatternBtn.addActionListener(e -> deletePattern());
        deletePatternBtn.setEnabled(false);

        // Toolbar components
        symbolCombo = new JComboBox<>(SYMBOLS);
        symbolCombo.setEditable(true);

        timeframeCombo = new JComboBox<>(TIMEFRAMES);
        timeframeCombo.setSelectedItem("1h");

        durationCombo = new JComboBox<>(DURATIONS);
        durationCombo.setSelectedItem("30 days");

        loadDataBtn = new JButton("Load Data");
        loadDataBtn.addActionListener(e -> loadCandles());

        findMatchesBtn = new JButton("Find Matches");
        findMatchesBtn.addActionListener(e -> findMatches());
        findMatchesBtn.setEnabled(false);

        showMatchesToggle = new JToggleButton("Show Matches", true);
        showMatchesToggle.addActionListener(e -> chartPanel.setShowMatches(showMatchesToggle.isSelected()));

        // Smoothing controls
        smoothingTypeCombo = new JComboBox<>(PriceSmoothingType.values());
        smoothingTypeCombo.addActionListener(e -> {
            updateSmoothingPeriodVisibility();
            onSmoothingChanged();
        });

        smoothingPeriodSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 100, 1));
        smoothingPeriodSpinner.setPreferredSize(new Dimension(50, smoothingPeriodSpinner.getPreferredSize().height));
        smoothingPeriodSpinner.addChangeListener(e -> onSmoothingChanged());

        smoothingPeriodLabel = new JLabel("Period:");

        // Price line opacity slider
        priceOpacitySlider = new JSlider(0, 100, 100);
        priceOpacitySlider.setPreferredSize(new Dimension(80, priceOpacitySlider.getPreferredSize().height));
        priceOpacitySlider.addChangeListener(e -> {
            int percent = priceOpacitySlider.getValue();
            chartPanel.setPriceLineOpacity(percent * 255 / 100);
        });
        priceOpacityLabel = new JLabel("Price:");

        statusLabel = new JLabel("Select a pattern to begin");

        // Chart panel
        chartPanel = new HoopPatternChartPanel();
        chartPanel.setOnPatternChanged(this::onPatternChanged);
        chartPanel.setOnSelectionChanged(this::onSelectionChanged);

        // Hoop list
        hoopListModel = new DefaultListModel<>();
        hoopList = new JList<>(hoopListModel);
        hoopList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hoopList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = hoopList.getSelectedIndex();
                chartPanel.setSelectedHoop(idx);
                updatePropertiesPanel();
            }
        });

        // Properties panel components
        nameField = new JTextField(10);
        minPercentSpinner = new JSpinner(new SpinnerNumberModel(-2.0, -50.0, 50.0, 0.5));
        maxPercentSpinner = new JSpinner(new SpinnerNumberModel(2.0, -50.0, 50.0, 0.5));
        distanceSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 100, 1));
        toleranceSpinner = new JSpinner(new SpinnerNumberModel(2, 0, 50, 1));
        anchorModeCombo = new JComboBox<>(Hoop.AnchorMode.values());

        // Wire property changes
        ChangeListener propChangeListener = e -> updateSelectedHoop();
        nameField.addActionListener(e -> updateSelectedHoop());
        minPercentSpinner.addChangeListener(propChangeListener);
        maxPercentSpinner.addChangeListener(propChangeListener);
        distanceSpinner.addChangeListener(propChangeListener);
        toleranceSpinner.addChangeListener(propChangeListener);
        anchorModeCombo.addActionListener(e -> updateSelectedHoop());

        updateHoopList();
        updateSmoothingPeriodVisibility();
    }

    private void layoutComponents() {
        JPanel contentPane = new JPanel(new BorderLayout(0, 0));
        setContentPane(contentPane);

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.add(new JLabel("Symbol:"));
        toolbar.add(symbolCombo);
        toolbar.add(new JLabel("Timeframe:"));
        toolbar.add(timeframeCombo);
        toolbar.add(new JLabel("Duration:"));
        toolbar.add(durationCombo);
        toolbar.add(Box.createHorizontalStrut(8));
        toolbar.add(loadDataBtn);
        toolbar.add(Box.createHorizontalStrut(16));
        toolbar.add(findMatchesBtn);
        toolbar.add(showMatchesToggle);
        toolbar.add(Box.createHorizontalStrut(16));
        toolbar.add(new JLabel("Smoothing:"));
        toolbar.add(smoothingTypeCombo);
        toolbar.add(smoothingPeriodLabel);
        toolbar.add(smoothingPeriodSpinner);
        toolbar.add(Box.createHorizontalStrut(8));
        toolbar.add(priceOpacityLabel);
        toolbar.add(priceOpacitySlider);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(statusLabel);

        contentPane.add(toolbar, BorderLayout.NORTH);

        // Left panel: Pattern list
        JPanel leftPanel = new JPanel(new BorderLayout(0, 4));
        leftPanel.setPreferredSize(new Dimension(180, 0));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));

        JLabel patternsLabel = new JLabel("Patterns");
        patternsLabel.setFont(patternsLabel.getFont().deriveFont(Font.BOLD));
        leftPanel.add(patternsLabel, BorderLayout.NORTH);

        JScrollPane patternScroll = new JScrollPane(patternList);
        leftPanel.add(patternScroll, BorderLayout.CENTER);

        JPanel patternButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        patternButtons.add(newPatternBtn);
        patternButtons.add(deletePatternBtn);
        leftPanel.add(patternButtons, BorderLayout.SOUTH);

        // Right panel: Hoop list + properties
        JPanel rightPanel = new JPanel(new BorderLayout(0, 4));
        rightPanel.setPreferredSize(new Dimension(180, 0));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 8));

        // Top: Hoops label + list + buttons
        JPanel hoopListPanel = new JPanel(new BorderLayout(0, 4));

        JLabel hoopsLabel = new JLabel("Hoops");
        hoopsLabel.setFont(hoopsLabel.getFont().deriveFont(Font.BOLD));
        hoopListPanel.add(hoopsLabel, BorderLayout.NORTH);

        JScrollPane listScroll = new JScrollPane(hoopList);
        hoopListPanel.add(listScroll, BorderLayout.CENTER);

        // Add/remove buttons
        JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton addHoopBtn = new JButton("+");
        addHoopBtn.setToolTipText("Add hoop");
        addHoopBtn.addActionListener(e -> addHoop());
        JButton removeHoopBtn = new JButton("−");
        removeHoopBtn.setToolTipText("Remove hoop");
        removeHoopBtn.addActionListener(e -> removeHoop());
        listButtons.add(addHoopBtn);
        listButtons.add(removeHoopBtn);
        hoopListPanel.add(listButtons, BorderLayout.SOUTH);

        // Properties panel (vertical layout)
        JPanel propsPanel = new JPanel(new GridBagLayout());
        propsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
            BorderFactory.createEmptyBorder(8, 0, 0, 0)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;

        // Name
        gbc.gridx = 0; gbc.gridy = 0;
        propsPanel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        propsPanel.add(nameField, gbc);

        // Min %
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        propsPanel.add(new JLabel("Min %:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        propsPanel.add(minPercentSpinner, gbc);

        // Max %
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        propsPanel.add(new JLabel("Max %:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        propsPanel.add(maxPercentSpinner, gbc);

        // Distance
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        propsPanel.add(new JLabel("Distance:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        propsPanel.add(distanceSpinner, gbc);

        // Tolerance
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        propsPanel.add(new JLabel("Tolerance:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        propsPanel.add(toleranceSpinner, gbc);

        // Anchor
        gbc.gridx = 0; gbc.gridy = 5; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        propsPanel.add(new JLabel("Anchor:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        propsPanel.add(anchorModeCombo, gbc);

        rightPanel.add(hoopListPanel, BorderLayout.CENTER);
        rightPanel.add(propsPanel, BorderLayout.SOUTH);

        // Center: Chart with right panel
        JPanel chartAndRight = new JPanel(new BorderLayout());
        chartAndRight.add(chartPanel, BorderLayout.CENTER);
        chartAndRight.add(rightPanel, BorderLayout.EAST);

        // Main split: left | center+right
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setLeftComponent(leftPanel);
        mainSplit.setRightComponent(chartAndRight);
        mainSplit.setDividerLocation(180);
        mainSplit.setOneTouchExpandable(true);

        contentPane.add(mainSplit, BorderLayout.CENTER);
    }

    private void loadPatternData() {
        if (pattern != null) {
            symbolCombo.setSelectedItem(pattern.getSymbol());
            timeframeCombo.setSelectedItem(pattern.getTimeframe());
        }
    }

    private void setupAutoSave() {
        autoSaveTimer = new Timer(500, e -> savePattern());
        autoSaveTimer.setRepeats(false);
    }

    private void scheduleAutoSave() {
        if (autoSaveTimer.isRunning()) {
            autoSaveTimer.restart();
        } else {
            autoSaveTimer.start();
        }
    }

    private void savePattern() {
        if (pattern != null) {
            pattern.setSymbol((String) symbolCombo.getSelectedItem());
            pattern.setTimeframe((String) timeframeCombo.getSelectedItem());
            patternStore.save(pattern);
        }
    }

    private void loadCandles() {
        String symbol = (String) symbolCombo.getSelectedItem();
        String timeframe = (String) timeframeCombo.getSelectedItem();
        String duration = (String) durationCombo.getSelectedItem();

        long endTime = System.currentTimeMillis();
        long startTime = endTime - parseDuration(duration);

        loadDataBtn.setEnabled(false);
        statusLabel.setText("Loading " + symbol + " " + timeframe + "...");

        // Register with preview tracker
        DataRequirementsTracker tracker = ApplicationContext.getInstance().getPreviewTracker();
        String dataType = "OHLC:" + timeframe;
        DataRequirement requirement = new DataRequirement(
            dataType,
            symbol,
            startTime,
            endTime,
            DataRequirement.Tier.TRADING,
            "hoop:" + (pattern != null ? pattern.getId() : "editor"),
            DataConsumer.HOOP_PREVIEW
        );
        tracker.addRequirement(requirement);
        tracker.updateStatus(dataType, DataRequirementsTracker.Status.FETCHING);

        SwingWorker<List<Candle>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Candle> doInBackground() throws Exception {
                return candleStore.getCandles(symbol, timeframe, startTime, endTime);
            }

            @Override
            protected void done() {
                try {
                    candles = get();
                    tracker.updateStatus(dataType, DataRequirementsTracker.Status.READY, candles.size(), candles.size());
                    chartPanel.setCandles(candles);

                    // Apply current smoothing settings to chart
                    PriceSmoothingType type = (PriceSmoothingType) smoothingTypeCombo.getSelectedItem();
                    int period = ((Number) smoothingPeriodSpinner.getValue()).intValue();
                    chartPanel.setSmoothing(type, period);

                    // Auto-set anchor to 20% into the data so hoops are visible
                    if (!candles.isEmpty()) {
                        int anchorBar = Math.max(0, candles.size() / 5);
                        chartPanel.setAnchorBar(anchorBar);
                        // Fit chart to show all data
                        chartPanel.fitAll();
                        statusLabel.setText("Loaded " + candles.size() + " candles. Right-click to set anchor.");
                    } else {
                        statusLabel.setText("No candles loaded.");
                    }
                    findMatchesBtn.setEnabled(true);
                } catch (Exception ex) {
                    tracker.updateStatus(dataType, DataRequirementsTracker.Status.ERROR, 0, 0, ex.getMessage());
                    statusLabel.setText("Error: " + ex.getMessage());
                } finally {
                    loadDataBtn.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private long parseDuration(String duration) {
        return switch (duration) {
            case "7 days" -> 7L * 24 * 60 * 60 * 1000;
            case "14 days" -> 14L * 24 * 60 * 60 * 1000;
            case "30 days" -> 30L * 24 * 60 * 60 * 1000;
            case "60 days" -> 60L * 24 * 60 * 60 * 1000;
            case "90 days" -> 90L * 24 * 60 * 60 * 1000;
            default -> 30L * 24 * 60 * 60 * 1000;
        };
    }

    private void findMatches() {
        if (pattern == null || candles.isEmpty()) return;

        statusLabel.setText("Finding matches...");

        SwingWorker<List<HoopMatchResult>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<HoopMatchResult> doInBackground() throws Exception {
                HoopPatternEvaluator evaluator = new HoopPatternEvaluator(candleStore);
                return evaluator.findPatternCompletions(pattern, candles);
            }

            @Override
            protected void done() {
                try {
                    List<HoopMatchResult> matches = get();
                    chartPanel.setMatches(matches);
                    statusLabel.setText("Found " + matches.size() + " pattern matches");
                } catch (Exception ex) {
                    statusLabel.setText("Error finding matches: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void updateHoopList() {
        hoopListModel.clear();
        if (pattern != null && pattern.getHoops() != null) {
            for (int i = 0; i < pattern.getHoops().size(); i++) {
                Hoop h = pattern.getHoops().get(i);
                hoopListModel.addElement((i + 1) + ". " + (h.name() != null ? h.name() : "hoop-" + (i + 1)));
            }
        }
    }

    private void updatePropertiesPanel() {
        int idx = chartPanel.getSelectedHoop();
        if (idx < 0 || pattern == null || idx >= pattern.getHoops().size()) {
            setPropertiesEnabled(false);
            return;
        }

        suppressPropertyChanges = true;
        try {
            Hoop hoop = pattern.getHoops().get(idx);
            nameField.setText(hoop.name() != null ? hoop.name() : "");
            minPercentSpinner.setValue(hoop.minPricePercent() != null ? hoop.minPricePercent() : -2.0);
            maxPercentSpinner.setValue(hoop.maxPricePercent() != null ? hoop.maxPricePercent() : 2.0);
            distanceSpinner.setValue(hoop.distance());
            toleranceSpinner.setValue(hoop.tolerance());
            anchorModeCombo.setSelectedItem(hoop.anchorMode());
            setPropertiesEnabled(true);
        } finally {
            suppressPropertyChanges = false;
        }
    }

    private void setPropertiesEnabled(boolean enabled) {
        nameField.setEnabled(enabled);
        minPercentSpinner.setEnabled(enabled);
        maxPercentSpinner.setEnabled(enabled);
        distanceSpinner.setEnabled(enabled);
        toleranceSpinner.setEnabled(enabled);
        anchorModeCombo.setEnabled(enabled);
    }

    private void updateSelectedHoop() {
        if (suppressPropertyChanges) return;

        int idx = chartPanel.getSelectedHoop();
        if (idx < 0 || pattern == null || idx >= pattern.getHoops().size()) return;

        Hoop newHoop = new Hoop(
            nameField.getText().trim(),
            ((Number) minPercentSpinner.getValue()).doubleValue(),
            ((Number) maxPercentSpinner.getValue()).doubleValue(),
            ((Number) distanceSpinner.getValue()).intValue(),
            ((Number) toleranceSpinner.getValue()).intValue(),
            (Hoop.AnchorMode) anchorModeCombo.getSelectedItem()
        );

        List<Hoop> hoops = new ArrayList<>(pattern.getHoops());
        hoops.set(idx, newHoop);
        pattern.setHoops(hoops);

        updateHoopList();
        chartPanel.setPattern(pattern);
        scheduleAutoSave();
    }

    private void addHoop() {
        if (pattern == null) return;

        List<Hoop> hoops = new ArrayList<>(pattern.getHoops());
        int count = hoops.size();
        Hoop newHoop = new Hoop("hoop-" + (count + 1), -2.0, 2.0, 5, 2, Hoop.AnchorMode.ACTUAL_HIT);
        hoops.add(newHoop);
        pattern.setHoops(hoops);

        updateHoopList();
        chartPanel.setPattern(pattern);
        hoopList.setSelectedIndex(hoops.size() - 1);
        scheduleAutoSave();
    }

    private void removeHoop() {
        int idx = hoopList.getSelectedIndex();
        if (idx < 0 || pattern == null || idx >= pattern.getHoops().size()) return;

        List<Hoop> hoops = new ArrayList<>(pattern.getHoops());
        hoops.remove(idx);
        pattern.setHoops(hoops);

        updateHoopList();
        chartPanel.setPattern(pattern);
        chartPanel.setSelectedHoop(-1);
        scheduleAutoSave();
    }

    private void onPatternChanged() {
        updateHoopList();
        updatePropertiesPanel();
        scheduleAutoSave();
    }

    private void onSelectionChanged() {
        int idx = chartPanel.getSelectedHoop();
        if (idx >= 0 && idx < hoopListModel.size()) {
            hoopList.setSelectedIndex(idx);
        }
        updatePropertiesPanel();
    }

    private void updateSmoothingPeriodVisibility() {
        PriceSmoothingType type = (PriceSmoothingType) smoothingTypeCombo.getSelectedItem();
        boolean needsPeriod = type == PriceSmoothingType.SMA || type == PriceSmoothingType.EMA;
        smoothingPeriodLabel.setVisible(needsPeriod);
        smoothingPeriodSpinner.setVisible(needsPeriod);
    }

    private void onSmoothingChanged() {
        if (pattern == null) return;

        PriceSmoothingType type = (PriceSmoothingType) smoothingTypeCombo.getSelectedItem();
        int period = ((Number) smoothingPeriodSpinner.getValue()).intValue();

        pattern.setPriceSmoothingType(type);
        pattern.setPriceSmoothingPeriod(period);

        // Auto-set price line opacity: 50% when smoothing active, 100% otherwise
        if (type != PriceSmoothingType.NONE) {
            priceOpacitySlider.setValue(50);
        } else {
            priceOpacitySlider.setValue(100);
        }

        // Update chart to show smoothed line
        chartPanel.setSmoothing(type, period);
        scheduleAutoSave();
    }

    private void loadPatterns() {
        patternListModel.clear();
        List<HoopPattern> patterns = patternStore.loadAll();
        for (HoopPattern p : patterns) {
            patternListModel.addElement(p);
        }
        // Select first pattern if available
        if (!patternListModel.isEmpty()) {
            patternList.setSelectedIndex(0);
        }
    }

    private void onPatternSelected() {
        HoopPattern selected = patternList.getSelectedValue();
        if (selected != null) {
            pattern = selected;
            chartPanel.setPattern(pattern);
            loadPatternData();
            updateHoopList();
            updatePropertiesPanel();

            // Sync smoothing controls
            smoothingTypeCombo.setSelectedItem(pattern.getPriceSmoothingType());
            smoothingPeriodSpinner.setValue(pattern.getPriceSmoothingPeriod());
            updateSmoothingPeriodVisibility();

            // Set opacity slider based on smoothing
            if (pattern.getPriceSmoothingType() != PriceSmoothingType.NONE) {
                priceOpacitySlider.setValue(50);
            } else {
                priceOpacitySlider.setValue(100);
            }

            // Update chart smoothing display
            chartPanel.setSmoothing(pattern.getPriceSmoothingType(), pattern.getPriceSmoothingPeriod());
            chartPanel.setPriceLineOpacity(priceOpacitySlider.getValue() * 255 / 100);

            deletePatternBtn.setEnabled(true);
            findMatchesBtn.setEnabled(!candles.isEmpty());
            statusLabel.setText("Pattern: " + pattern.getName());
        } else {
            pattern = null;
            chartPanel.setPattern(null);
            hoopListModel.clear();
            setPropertiesEnabled(false);
            deletePatternBtn.setEnabled(false);
            findMatchesBtn.setEnabled(false);
            statusLabel.setText("Select a pattern to begin");
        }
    }

    private void createPattern() {
        String name = JOptionPane.showInputDialog(this, "Enter pattern name:", "New Pattern", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;

        String id = name.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        HoopPattern newPattern = new HoopPattern();
        newPattern.setId(id);
        newPattern.setName(name.trim());
        newPattern.setSymbol((String) symbolCombo.getSelectedItem());
        newPattern.setTimeframe((String) timeframeCombo.getSelectedItem());
        newPattern.setHoops(new ArrayList<>());

        patternStore.save(newPattern);
        patternListModel.addElement(newPattern);
        patternList.setSelectedValue(newPattern, true);
    }

    private void deletePattern() {
        HoopPattern selected = patternList.getSelectedValue();
        if (selected == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete pattern '" + selected.getName() + "'?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            patternStore.delete(selected.getId());
            patternListModel.removeElement(selected);
            if (!patternListModel.isEmpty()) {
                patternList.setSelectedIndex(0);
            } else {
                onPatternSelected();
            }
        }
    }

    /**
     * Custom cell renderer for pattern list.
     */
    private static class PatternCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof HoopPattern pattern) {
                String text = pattern.getName();
                if (pattern.getHoops() != null && !pattern.getHoops().isEmpty()) {
                    text += " (" + pattern.getHoops().size() + " hoops)";
                }
                setText(text);
            }
            return this;
        }
    }
}
