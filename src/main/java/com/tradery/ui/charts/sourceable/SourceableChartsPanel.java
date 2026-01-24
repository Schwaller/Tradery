package com.tradery.ui.charts.sourceable;

import com.tradery.ui.charts.ChartStyles;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Panel that displays multiple sourceable charts.
 * Each chart has a header with its name and data source info.
 */
public class SourceableChartsPanel extends JPanel {

    private final SourceableChartsManager chartsManager;
    private final Map<String, ChartPanel> chartPanels = new HashMap<>();
    private final JPanel chartsContainer;
    private final JPanel headerPanel;
    private JButton addChartButton;

    private ChartDataContext currentContext;

    public SourceableChartsPanel(SourceableChartsManager chartsManager) {
        this.chartsManager = chartsManager;

        setLayout(new BorderLayout());
        setBackground(ChartStyles.BACKGROUND_COLOR);

        // Header with "Add Chart" button
        headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // Container for chart panels
        chartsContainer = new JPanel();
        chartsContainer.setLayout(new BoxLayout(chartsContainer, BoxLayout.Y_AXIS));
        chartsContainer.setBackground(ChartStyles.BACKGROUND_COLOR);

        JScrollPane scrollPane = new JScrollPane(chartsContainer);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getViewport().setBackground(ChartStyles.BACKGROUND_COLOR);
        add(scrollPane, BorderLayout.CENTER);

        // Listen for chart changes
        chartsManager.addChangeListener(this::rebuildChartPanels);

        // Initial build
        rebuildChartPanels();
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ChartStyles.BACKGROUND_COLOR);
        panel.setBorder(new EmptyBorder(4, 8, 4, 8));

        JLabel title = new JLabel("Orderflow Charts");
        title.setForeground(ChartStyles.TEXT_COLOR);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        panel.add(title, BorderLayout.WEST);

        addChartButton = new JButton("+");
        addChartButton.setToolTipText("Add new chart");
        addChartButton.setMargin(new Insets(2, 8, 2, 8));
        addChartButton.addActionListener(e -> showAddChartDialog());
        panel.add(addChartButton, BorderLayout.EAST);

        return panel;
    }

    private void showAddChartDialog() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        AddChartDialog.show(owner, chartsManager, chart -> {
            if (currentContext != null) {
                chart.updateData(currentContext);
            }
        });
    }

    private void rebuildChartPanels() {
        chartsContainer.removeAll();
        chartPanels.clear();

        for (SourceableChart chart : chartsManager.getVisibleCharts()) {
            JPanel wrapper = createChartWrapper(chart);
            chartsContainer.add(wrapper);
        }

        if (chartsManager.getVisibleCharts().isEmpty()) {
            JLabel emptyLabel = new JLabel("No charts configured. Click + to add.");
            emptyLabel.setForeground(ChartStyles.TEXT_COLOR);
            emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
            emptyLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
            chartsContainer.add(emptyLabel);
        }

        revalidate();
        repaint();
    }

    private JPanel createChartWrapper(SourceableChart chart) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ChartStyles.PLOT_BACKGROUND_COLOR);
        wrapper.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ChartStyles.GRIDLINE_COLOR));

        // Chart header
        JPanel header = createChartHeader(chart);
        wrapper.add(header, BorderLayout.NORTH);

        // Chart panel
        JFreeChart jfreeChart = chart.getChart();
        if (jfreeChart != null) {
            ChartPanel chartPanel = new ChartPanel(jfreeChart);
            chartPanel.setPreferredSize(new Dimension(0, chart.getHeight()));
            chartPanel.setMinimumSize(new Dimension(100, 60));
            chartPanel.setMaximumDrawWidth(Integer.MAX_VALUE);
            chartPanel.setMaximumDrawHeight(Integer.MAX_VALUE);
            chartPanel.setMouseWheelEnabled(false);
            chartPanel.setPopupMenu(createChartPopupMenu(chart));

            chartPanels.put(chart.getId(), chartPanel);
            wrapper.add(chartPanel, BorderLayout.CENTER);
        } else {
            JLabel placeholder = new JLabel("Loading...");
            placeholder.setForeground(ChartStyles.TEXT_COLOR);
            placeholder.setHorizontalAlignment(SwingConstants.CENTER);
            placeholder.setPreferredSize(new Dimension(0, chart.getHeight()));
            wrapper.add(placeholder, BorderLayout.CENTER);
        }

        // Set maximum height
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, chart.getHeight() + 24));

        return wrapper;
    }

    private JPanel createChartHeader(SourceableChart chart) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ChartStyles.BACKGROUND_COLOR);
        header.setBorder(new EmptyBorder(2, 8, 2, 8));

        JLabel label = new JLabel(chart.getDisplayLabel());
        label.setForeground(ChartStyles.TEXT_COLOR);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 10f));
        header.add(label, BorderLayout.WEST);

        // Control buttons
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        controls.setOpaque(false);

        JButton hideBtn = new JButton("−");
        hideBtn.setToolTipText("Hide chart");
        hideBtn.setMargin(new Insets(0, 4, 0, 4));
        hideBtn.setFont(hideBtn.getFont().deriveFont(10f));
        hideBtn.addActionListener(e -> {
            chartsManager.setVisibility(chart.getId(), false);
        });
        controls.add(hideBtn);

        JButton removeBtn = new JButton("×");
        removeBtn.setToolTipText("Remove chart");
        removeBtn.setMargin(new Insets(0, 4, 0, 4));
        removeBtn.setFont(removeBtn.getFont().deriveFont(10f));
        removeBtn.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(this,
                "Remove chart '" + chart.getName() + "'?",
                "Confirm Remove", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                chartsManager.removeChart(chart.getId());
            }
        });
        controls.add(removeBtn);

        header.add(controls, BorderLayout.EAST);

        return header;
    }

    private JPopupMenu createChartPopupMenu(SourceableChart chart) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem editItem = new JMenuItem("Edit Settings...");
        editItem.addActionListener(e -> showEditDialog(chart));
        menu.add(editItem);

        menu.addSeparator();

        JMenuItem moveUpItem = new JMenuItem("Move Up");
        moveUpItem.addActionListener(e -> chartsManager.moveChartUp(chart.getId()));
        menu.add(moveUpItem);

        JMenuItem moveDownItem = new JMenuItem("Move Down");
        moveDownItem.addActionListener(e -> chartsManager.moveChartDown(chart.getId()));
        menu.add(moveDownItem);

        menu.addSeparator();

        JMenuItem hideItem = new JMenuItem("Hide");
        hideItem.addActionListener(e -> chartsManager.setVisibility(chart.getId(), false));
        menu.add(hideItem);

        JMenuItem removeItem = new JMenuItem("Remove");
        removeItem.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(this,
                "Remove chart '" + chart.getName() + "'?",
                "Confirm Remove", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                chartsManager.removeChart(chart.getId());
            }
        });
        menu.add(removeItem);

        return menu;
    }

    private void showEditDialog(SourceableChart chart) {
        // TODO: Implement edit dialog for chart settings
        JOptionPane.showMessageDialog(this,
            "Edit settings for: " + chart.getName(),
            "Edit Chart", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Update all charts with new data context.
     */
    public void setDataContext(ChartDataContext context) {
        this.currentContext = context;
        chartsManager.setDataContext(context);

        // Refresh chart panels
        for (SourceableChart chart : chartsManager.getVisibleCharts()) {
            ChartPanel panel = chartPanels.get(chart.getId());
            if (panel != null && chart.getChart() != null) {
                panel.setChart(chart.getChart());
            }
        }
    }

    /**
     * Get the charts manager.
     */
    public SourceableChartsManager getChartsManager() {
        return chartsManager;
    }

    /**
     * Check if any charts are visible.
     */
    public boolean hasVisibleCharts() {
        return !chartsManager.getVisibleCharts().isEmpty();
    }
}
