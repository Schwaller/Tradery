package com.tradery.ui;

import com.tradery.ApplicationContext;
import com.tradery.TraderyApp;
import com.tradery.data.CandleStore;
import com.tradery.engine.HoopPatternEvaluator;
import com.tradery.io.HoopPatternStore;
import com.tradery.model.Candle;
import com.tradery.model.Hoop;
import com.tradery.model.HoopMatchResult;
import com.tradery.model.HoopPattern;

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
    private JLabel statusLabel;
    private JList<String> hoopList;
    private DefaultListModel<String> hoopListModel;

    // Properties panel
    private JTextField nameField;
    private JSpinner minPercentSpinner;
    private JSpinner maxPercentSpinner;
    private JSpinner distanceSpinner;
    private JSpinner toleranceSpinner;
    private JComboBox<Hoop.AnchorMode> anchorModeCombo;

    private boolean suppressPropertyChanges = false;
    private Timer autoSaveTimer;

    public InteractiveHoopEditorFrame(HoopPattern pattern, HoopPatternStore patternStore) {
        super("Interactive Hoop Editor - " + TraderyApp.APP_NAME);
        this.pattern = pattern;
        this.patternStore = patternStore;
        this.candleStore = ApplicationContext.getInstance().getCandleStore();

        initializeFrame();
        initializeComponents();
        layoutComponents();
        loadPatternData();
        setupAutoSave();
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
        // Toolbar components
        symbolCombo = new JComboBox<>(SYMBOLS);
        symbolCombo.setEditable(true);
        if (pattern.getSymbol() != null) {
            symbolCombo.setSelectedItem(pattern.getSymbol());
        }

        timeframeCombo = new JComboBox<>(TIMEFRAMES);
        if (pattern.getTimeframe() != null) {
            timeframeCombo.setSelectedItem(pattern.getTimeframe());
        }

        durationCombo = new JComboBox<>(DURATIONS);
        durationCombo.setSelectedItem("30 days");

        loadDataBtn = new JButton("Load Data");
        loadDataBtn.addActionListener(e -> loadCandles());

        findMatchesBtn = new JButton("Find Matches");
        findMatchesBtn.addActionListener(e -> findMatches());
        findMatchesBtn.setEnabled(false);

        showMatchesToggle = new JToggleButton("Show Matches", true);
        showMatchesToggle.addActionListener(e -> chartPanel.setShowMatches(showMatchesToggle.isSelected()));

        statusLabel = new JLabel("Click 'Load Data' to fetch candles");

        // Chart panel
        chartPanel = new HoopPatternChartPanel();
        chartPanel.setPattern(pattern);
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
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(statusLabel);

        contentPane.add(toolbar, BorderLayout.NORTH);

        // Right panel: Hoop list
        JPanel rightPanel = new JPanel(new BorderLayout(0, 4));
        rightPanel.setPreferredSize(new Dimension(150, 0));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel hoopsLabel = new JLabel("Hoops");
        hoopsLabel.setFont(hoopsLabel.getFont().deriveFont(Font.BOLD));
        rightPanel.add(hoopsLabel, BorderLayout.NORTH);

        JScrollPane listScroll = new JScrollPane(hoopList);
        rightPanel.add(listScroll, BorderLayout.CENTER);

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
        rightPanel.add(listButtons, BorderLayout.SOUTH);

        // Bottom panel: Properties
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        bottomPanel.add(new JLabel("Name:"));
        bottomPanel.add(nameField);
        bottomPanel.add(Box.createHorizontalStrut(8));
        bottomPanel.add(new JLabel("Min %:"));
        bottomPanel.add(minPercentSpinner);
        bottomPanel.add(new JLabel("Max %:"));
        bottomPanel.add(maxPercentSpinner);
        bottomPanel.add(Box.createHorizontalStrut(8));
        bottomPanel.add(new JLabel("Distance:"));
        bottomPanel.add(distanceSpinner);
        bottomPanel.add(new JLabel("Tolerance:"));
        bottomPanel.add(toleranceSpinner);
        bottomPanel.add(Box.createHorizontalStrut(8));
        bottomPanel.add(new JLabel("Anchor:"));
        bottomPanel.add(anchorModeCombo);

        contentPane.add(bottomPanel, BorderLayout.SOUTH);

        // Center: Chart
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(chartPanel, BorderLayout.CENTER);
        centerPanel.add(rightPanel, BorderLayout.EAST);

        contentPane.add(centerPanel, BorderLayout.CENTER);
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

        SwingWorker<List<Candle>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Candle> doInBackground() throws Exception {
                return candleStore.getCandles(symbol, timeframe, startTime, endTime);
            }

            @Override
            protected void done() {
                try {
                    candles = get();
                    chartPanel.setCandles(candles);

                    // Auto-set anchor to 20% into the data so hoops are visible
                    if (!candles.isEmpty()) {
                        int anchorBar = Math.max(0, candles.size() / 5);
                        chartPanel.setAnchorBar(anchorBar);
                        statusLabel.setText("Loaded " + candles.size() + " candles. Right-click to set anchor.");
                    } else {
                        statusLabel.setText("No candles loaded.");
                    }
                    findMatchesBtn.setEnabled(true);
                } catch (Exception ex) {
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
}
