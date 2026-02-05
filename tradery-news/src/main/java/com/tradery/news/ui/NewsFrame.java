package com.tradery.news.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.tradery.news.ai.ClaudeCliProcessor;
import com.tradery.news.fetch.FetchScheduler;
import com.tradery.news.fetch.FetcherRegistry;
import com.tradery.news.model.Article;
import com.tradery.news.store.SqliteNewsStore;
import com.tradery.news.topic.TopicRegistry;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Main window for news visualization.
 */
public class NewsFrame extends JFrame {

    private final SqliteNewsStore store;
    private final Path dataDir;
    private final TimelineGraphPanel graphPanel;
    private final JTextArea detailArea;
    private final JLabel statusLabel;
    private JComboBox<String> limitCombo;
    private JCheckBox connectionsCheck;
    private JCheckBox labelsCheck;
    private JButton fetchBtn;
    private volatile boolean fetching = false;

    public NewsFrame(SqliteNewsStore store, Path dataDir) {
        super("Tradery - News");
        this.store = store;
        this.dataDir = dataDir;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1800, 900);
        setLocationRelativeTo(null);

        // Main layout
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setBackground(new Color(30, 32, 36));

        // Toolbar
        JToolBar toolbar = createToolbar();
        mainPanel.add(toolbar, BorderLayout.NORTH);

        // Graph panel
        graphPanel = new TimelineGraphPanel();
        graphPanel.setOnNodeSelected(this::showNodeDetails);

        // Detail panel (right sidebar)
        JPanel detailPanel = new JPanel(new BorderLayout());
        detailPanel.setPreferredSize(new Dimension(960, 0));
        detailPanel.setMinimumSize(new Dimension(640, 0));
        detailPanel.setBackground(new Color(38, 40, 44));
        detailPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(50, 52, 56)));

        JLabel detailTitle = new JLabel("  Article Details");
        detailTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        detailTitle.setForeground(new Color(180, 180, 190));
        detailTitle.setPreferredSize(new Dimension(0, 32));
        detailPanel.add(detailTitle, BorderLayout.NORTH);

        detailArea = new JTextArea();
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setBackground(new Color(38, 40, 44));
        detailArea.setForeground(new Color(200, 200, 210));
        detailArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        detailArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        detailArea.setText("Click a node to see details");

        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(null);
        detailPanel.add(detailScroll, BorderLayout.CENTER);

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, graphPanel, detailPanel);
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerSize(4);
        splitPane.setBorder(null);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // Status bar
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        statusBar.setBackground(new Color(35, 37, 41));
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 52, 56)));
        statusLabel = new JLabel("Loading...");
        statusLabel.setForeground(new Color(140, 140, 150));
        statusBar.add(statusLabel);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // Cleanup on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                graphPanel.stopPhysics();
            }
        });

        // Load data
        SwingUtilities.invokeLater(this::loadData);
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(new Color(38, 40, 44));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 52, 56)));

        // Fetch new button
        fetchBtn = new JButton("Fetch New");
        fetchBtn.setToolTipText("Fetch new articles from RSS feeds with AI extraction");
        fetchBtn.addActionListener(e -> fetchNewArticles());
        toolbar.add(fetchBtn);

        // Refresh button
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setToolTipText("Reload from database");
        refreshBtn.addActionListener(e -> loadData());
        toolbar.add(refreshBtn);

        toolbar.addSeparator();

        // Limit selector
        toolbar.add(new JLabel(" Show: "));
        limitCombo = new JComboBox<>(new String[]{"100", "250", "500", "1000"});
        limitCombo.setSelectedItem("500");
        limitCombo.addActionListener(e -> {
            graphPanel.setMaxNodes(Integer.parseInt((String) limitCombo.getSelectedItem()));
            loadData();
        });
        toolbar.add(limitCombo);

        toolbar.addSeparator();

        // Checkboxes
        connectionsCheck = new JCheckBox("Connections", true);
        connectionsCheck.addActionListener(e -> graphPanel.setShowConnections(connectionsCheck.isSelected()));
        toolbar.add(connectionsCheck);

        labelsCheck = new JCheckBox("Labels", true);
        labelsCheck.addActionListener(e -> graphPanel.setShowLabels(labelsCheck.isSelected()));
        toolbar.add(labelsCheck);

        toolbar.addSeparator();

        // Legend
        toolbar.add(Box.createHorizontalGlue());
        JLabel legend = new JLabel("Size = Importance  |  Color = Sentiment (green=positive, gray=neutral, red=negative)  ");
        legend.setForeground(new Color(120, 120, 130));
        legend.setFont(new Font("SansSerif", Font.PLAIN, 11));
        toolbar.add(legend);

        return toolbar;
    }

    private void loadData() {
        statusLabel.setText("Loading...");

        SwingWorker<List<Article>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Article> doInBackground() {
                int limit = Integer.parseInt((String) limitCombo.getSelectedItem());
                return store.getArticles(SqliteNewsStore.ArticleQuery.all(limit));
            }

            @Override
            protected void done() {
                try {
                    List<Article> articles = get();
                    graphPanel.setArticles(articles);
                    statusLabel.setText(articles.size() + " articles loaded  |  " +
                        store.getArticleCount() + " total in database  |  " +
                        store.getTopicCounts().size() + " topics");
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void fetchNewArticles() {
        if (fetching) return;
        fetching = true;
        fetchBtn.setEnabled(false);
        fetchBtn.setText("Fetching...");
        statusLabel.setText("Fetching new articles with AI extraction (this may take a while)...");

        SwingWorker<FetchScheduler.FetchResult, String> worker = new SwingWorker<>() {
            @Override
            protected FetchScheduler.FetchResult doInBackground() {
                FetcherRegistry fetchers = new FetcherRegistry();
                fetchers.registerDefaults();

                TopicRegistry topics = new TopicRegistry(dataDir.resolve("topics.json"));
                ClaudeCliProcessor ai = new ClaudeCliProcessor();

                if (!ai.isAvailable()) {
                    publish("Claude CLI not available - fetching without AI extraction");
                    ai = null;
                }

                try (var scheduler = new FetchScheduler(fetchers, topics, store, ai)) {
                    scheduler.withAiEnabled(ai != null)
                             .withArticlesPerSource(ai != null ? 5 : 10);
                    return scheduler.fetchAndProcess();
                }
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    statusLabel.setText(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                fetching = false;
                fetchBtn.setEnabled(true);
                fetchBtn.setText("Fetch New");
                try {
                    FetchScheduler.FetchResult result = get();
                    if (result.newArticles() > 0) {
                        // Get all articles and add only new ones to the graph
                        int limit = Integer.parseInt((String) limitCombo.getSelectedItem());
                        List<Article> allArticles = store.getArticles(SqliteNewsStore.ArticleQuery.all(limit));
                        int added = graphPanel.addArticles(allArticles);
                        statusLabel.setText(String.format("Added %d new articles (%d AI processed)  |  Total: %d",
                            added, result.aiProcessed(), store.getArticleCount()));
                    } else {
                        statusLabel.setText("No new articles found  |  Total: " + store.getArticleCount());
                    }
                } catch (Exception e) {
                    statusLabel.setText("Fetch error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void showNodeDetails(NewsNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append("TITLE\n");
        sb.append(node.title()).append("\n\n");

        sb.append("SOURCE\n");
        sb.append(node.source()).append("\n\n");

        if (node.sourceUrl() != null && !node.sourceUrl().isEmpty()) {
            sb.append("URL\n");
            sb.append(node.sourceUrl()).append("\n\n");
        }

        sb.append("PUBLISHED\n");
        LocalDateTime ldt = LocalDateTime.ofInstant(node.publishedAt(), ZoneId.systemDefault());
        sb.append(ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        sb.append("IMPORTANCE\n");
        sb.append(node.importance()).append("\n\n");

        sb.append("SENTIMENT\n");
        sb.append(String.format("%.2f", node.sentiment()));
        if (node.sentiment() > 0.3) sb.append(" (Positive)");
        else if (node.sentiment() < -0.3) sb.append(" (Negative)");
        else sb.append(" (Neutral)");
        sb.append("\n\n");

        sb.append("TOPICS\n");
        sb.append(node.topics().isEmpty() ? "(none)" : String.join(", ", node.topics())).append("\n\n");

        sb.append("COINS\n");
        sb.append(node.coins().isEmpty() ? "(none)" : String.join(", ", node.coins())).append("\n\n");

        if (node.summary() != null && !node.summary().isEmpty()) {
            sb.append("SUMMARY\n");
            sb.append(node.summary()).append("\n\n");
        }

        if (node.content() != null && !node.content().isEmpty()) {
            sb.append("CONTENT\n");
            sb.append(node.content()).append("\n\n");
        }

        sb.append("CONNECTIONS\n");
        sb.append(node.connections().size()).append(" related articles");

        detailArea.setText(sb.toString());
        detailArea.setCaretPosition(0);
    }

    /**
     * Launch the UI.
     */
    public static void main(String[] args) {
        try {
            FlatDarkLaf.setup();
            UIManager.put("Button.arc", 5);
            UIManager.put("Component.arc", 5);
            UIManager.put("TextComponent.arc", 5);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Path dataDir = Path.of(System.getProperty("user.home"), ".cryptonews");
        SqliteNewsStore store = new SqliteNewsStore(dataDir.resolve("news.db"));

        SwingUtilities.invokeLater(() -> {
            NewsFrame frame = new NewsFrame(store, dataDir);
            frame.setVisible(true);
        });
    }
}
