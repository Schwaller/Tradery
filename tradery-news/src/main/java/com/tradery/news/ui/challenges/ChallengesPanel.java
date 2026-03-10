package com.tradery.news.ui.challenges;

import com.tradery.ai.challenges.execution.ChallengeExecutor;
import com.tradery.ai.challenges.model.Challenge;
import com.tradery.ai.challenges.model.ChallengeOutput;
import com.tradery.ai.challenges.model.ChallengeResult;
import com.tradery.ai.challenges.schedule.ChallengeScheduler;
import com.tradery.ai.challenges.store.ChallengeStore;
import com.tradery.ai.challenges.subject.ChallengeSubject;
import com.tradery.news.ui.IntelConfig;
import com.tradery.news.ui.IntelLogPanel;
import com.tradery.ui.controls.BorderlessScrollPane;
import com.tradery.ui.controls.ToolbarButton;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tradery.news.ui.challenges.ChallengeTheme.*;

/**
 * Panel that displays all enabled challenges with their result timelines,
 * charts, and run/edit controls.
 */
public class ChallengesPanel {

    public static final String CARD_ID = "__challenges__";

    private static final int RESULT_BOX_WIDTH = 320;

    private final ChallengeStore store;
    private final ChallengeExecutor executor;
    private final ChallengeScheduler scheduler;
    private final Window owner;

    private final JPanel card;
    private final JPanel content;
    private volatile boolean challengeRunning = false;

    public ChallengesPanel(ChallengeStore store, ChallengeExecutor executor,
                           ChallengeScheduler scheduler, Window owner) {
        this.store = store;
        this.executor = executor;
        this.scheduler = scheduler;
        this.owner = owner;

        card = new JPanel(new BorderLayout());
        content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(bgMain());
        content.setBorder(new EmptyBorder(12, 16, 12, 16));

        BorderlessScrollPane scroll = new BorderlessScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        card.add(scroll, BorderLayout.CENTER);
    }

    /** The card panel to add to a CardLayout container. */
    public JPanel getPanel() { return card; }

    /** The card ID for CardLayout switching. */
    public String getCardId() { return CARD_ID; }

    /** Rebuild the challenges content. */
    public void refresh() {
        content.removeAll();

        List<Challenge> challenges = store.listChallenges().stream()
            .filter(Challenge::enabled)
            .toList();

        // Top bar: auto-refresh + new + run all
        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setBackground(bgMain());
        topBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        topBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightControls.setBackground(bgMain());

        JCheckBox autoToggle = new JCheckBox("Auto-refresh");
        autoToggle.setFont(new Font("SansSerif", Font.PLAIN, 10));
        autoToggle.setSelected(scheduler.isEnabled());
        autoToggle.addActionListener(e -> {
            boolean enabled = autoToggle.isSelected();
            scheduler.setEnabled(enabled);
            IntelConfig.get().setChallengeAutoRefreshEnabled(enabled);
            IntelConfig.get().save();
        });
        rightControls.add(autoToggle);

        JButton newChallengeBtn = new ToolbarButton("+ New");
        newChallengeBtn.addActionListener(e -> {
            ChallengeEditorDialog dlg = new ChallengeEditorDialog(owner, store, null);
            dlg.setVisible(true);
            if (dlg.wasSaved()) refresh();
        });
        rightControls.add(newChallengeBtn);

        JButton runAllBtn = new ToolbarButton("Run All");
        runAllBtn.setEnabled(!challengeRunning);
        runAllBtn.addActionListener(e -> {
            List<Challenge> toRun = List.copyOf(challenges);
            challengeRunning = true;
            refresh();
            Thread.ofVirtual().start(() -> {
                for (Challenge ch : toRun) {
                    ChallengeSubject subject = new StandaloneChallengeSubject(ch);
                    ChallengeResult prev = store.getLatestResult(ch.id(), subject.id());
                    ChallengeResult result = executor.execute(ch, subject, 0,
                        msg -> IntelLogPanel.logAI(msg), prev);
                    store.saveResult(result);
                }
                SwingUtilities.invokeLater(() -> {
                    challengeRunning = false;
                    refresh();
                });
            });
        });
        rightControls.add(runAllBtn);

        topBar.add(rightControls, BorderLayout.EAST);
        content.add(topBar);

        // Challenge rows
        for (Challenge challenge : challenges) {
            addChallengeRow(challenge);
        }

        if (challenges.isEmpty()) {
            JLabel empty = new JLabel("No challenges configured");
            empty.setFont(new Font("SansSerif", Font.PLAIN, 12));
            empty.setForeground(textMuted());
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(empty);
        }

        content.add(Box.createVerticalGlue());
        content.revalidate();
        content.repaint();
    }

    private void addChallengeRow(Challenge challenge) {
        // Row container
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setBackground(bgMain());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(8, 0, 8, 0)
        ));

        // Top: title + run buttons
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(bgMain());

        // Left: title + description
        JPanel titleArea = new JPanel();
        titleArea.setLayout(new BoxLayout(titleArea, BoxLayout.Y_AXIS));
        titleArea.setBackground(bgMain());

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        titleRow.setBackground(bgMain());
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(challenge.title());
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(textPrimary());
        titleRow.add(titleLabel);

        JButton editBtn = new JButton("Edit");
        editBtn.setFont(new Font("SansSerif", Font.PLAIN, 9));
        editBtn.setForeground(linkColor());
        editBtn.setBorderPainted(false);
        editBtn.setContentAreaFilled(false);
        editBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        editBtn.addActionListener(e -> {
            ChallengeEditorDialog dlg = new ChallengeEditorDialog(owner, store, challenge);
            dlg.setVisible(true);
            if (dlg.wasSaved()) refresh();
        });
        titleRow.add(editBtn);

        JButton resultsBtn = new JButton("Results");
        resultsBtn.setFont(new Font("SansSerif", Font.PLAIN, 9));
        resultsBtn.setForeground(linkColor());
        resultsBtn.setBorderPainted(false);
        resultsBtn.setContentAreaFilled(false);
        resultsBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        resultsBtn.addActionListener(e -> {
            ChallengeResultsDialog dlg = new ChallengeResultsDialog(owner, challenge, store);
            dlg.setVisible(true);
            dlg.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent we) {
                    if (dlg.wasChanged()) refresh();
                }
            });
        });
        titleRow.add(resultsBtn);

        titleArea.add(titleRow);

        if (challenge.description() != null) {
            String shortDesc = challenge.description();
            if (shortDesc.length() > 120) shortDesc = shortDesc.substring(0, 117) + "...";
            JLabel desc = new JLabel(shortDesc);
            desc.setFont(new Font("SansSerif", Font.PLAIN, 10));
            desc.setForeground(textMuted());
            desc.setAlignmentX(Component.LEFT_ALIGNMENT);
            desc.setToolTipText("<html><body style='width:300px'>" + challenge.description() + "</body></html>");
            titleArea.add(desc);
        }
        header.add(titleArea, BorderLayout.CENTER);

        // Right: single run button
        JButton runBtn = new JButton("Run");
        runBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
        runBtn.setEnabled(!challengeRunning);
        runBtn.addActionListener(e -> {
            if (challengeRunning) return;
            challengeRunning = true;
            runBtn.setEnabled(false);
            ChallengeSubject subject = new StandaloneChallengeSubject(challenge);
            ChallengeResult prev = store.getLatestResult(challenge.id(), subject.id());
            Thread.ofVirtual().start(() -> {
                ChallengeResult result = executor.execute(challenge, subject, 0,
                    msg -> IntelLogPanel.logAI(msg), prev);
                store.saveResult(result);
                SwingUtilities.invokeLater(() -> {
                    challengeRunning = false;
                    refresh();
                });
            });
        });
        header.add(runBtn, BorderLayout.EAST);
        row.add(header, BorderLayout.NORTH);

        // Bottom: horizontal scrollable result boxes (oldest left -> newest right)
        List<ChallengeResult> results = store.getResultsForChallenge(challenge.id(), 50);

        JPanel timeline = new JPanel();
        timeline.setLayout(new BoxLayout(timeline, BoxLayout.X_AXIS));
        timeline.setBackground(bgMain());

        if (results.isEmpty()) {
            JLabel noResults = new JLabel("  No results yet — click a run button above");
            noResults.setFont(new Font("SansSerif", Font.ITALIC, 10));
            noResults.setForeground(textMuted());
            noResults.setPreferredSize(new Dimension(300, 40));
            timeline.add(noResults);
        } else {
            for (int i = 0; i < results.size(); i++) {
                if (i > 0) timeline.add(Box.createRigidArea(new Dimension(4, 0)));
                timeline.add(createResultBox(results.get(i)));
            }
        }

        // Let the timeline compute its preferred height from content
        Dimension timelinePref = timeline.getPreferredSize();
        int timelineH = timelinePref.height + 12;

        BorderlessScrollPane timelineScroll = new BorderlessScrollPane(timeline);
        timelineScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        timelineScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        timelineScroll.setPreferredSize(new Dimension(0, timelineH));
        timelineScroll.setMinimumSize(new Dimension(0, timelineH));
        timelineScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, timelineH));
        // Scroll to the right (latest results)
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
            JScrollBar hbar = timelineScroll.getHorizontalScrollBar();
            hbar.setValue(hbar.getMaximum());
        }));

        // Per-field charts over time (one chart per numeric field)
        List<ChallengeChartPanel> charts = ChallengeChartPanel.createCharts(challenge, results);
        if (!charts.isEmpty()) {
            JPanel centerPanel = new JPanel();
            centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
            centerPanel.setBackground(bgMain());
            timelineScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
            centerPanel.add(timelineScroll);
            for (ChallengeChartPanel chart : charts) {
                chart.setAlignmentX(Component.LEFT_ALIGNMENT);
                centerPanel.add(Box.createVerticalStrut(8));
                centerPanel.add(chart);
            }
            row.add(centerPanel, BorderLayout.CENTER);
        } else {
            row.add(timelineScroll, BorderLayout.CENTER);
        }

        content.add(row);
    }

    private JPanel createResultBox(ChallengeResult result) {
        // Compute box width: wider for structured lists with many columns
        int boxWidth = RESULT_BOX_WIDTH;
        if (result.itemResults() != null && !result.itemResults().isEmpty()) {
            Challenge ch = store.getChallenge(result.challengeId());
            if (ch != null && ch.output().fields() != null) {
                int fieldCount = ch.output().fields().size();
                boxWidth = Math.max(RESULT_BOX_WIDTH, 60 * fieldCount + 40);
            }
        }

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(darker(bgCard(), 0.03f));
        box.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(darker(bgMain(), 0.12f), 1),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        box.setMinimumSize(new Dimension(boxWidth, 60));
        box.setMaximumSize(new Dimension(boxWidth, Integer.MAX_VALUE));

        // Timestamp
        long ago = System.currentTimeMillis() - result.timestamp();
        String agoStr = ago < 60_000 ? "just now"
            : ago < 3600_000 ? (ago / 60_000) + "m ago"
            : ago < 86_400_000 ? (ago / 3600_000) + "h ago"
            : (ago / 86_400_000) + "d ago";
        JLabel timeLabel = new JLabel(agoStr);
        timeLabel.setFont(new Font("SansSerif", Font.PLAIN, 9));
        timeLabel.setForeground(textMuted());
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(timeLabel);
        box.add(Box.createVerticalStrut(3));

        // Signal badge
        if (result.hasSignal()) {
            JLabel signalLabel = new JLabel(String.format("%.1f", result.signalValue()));
            signalLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
            signalLabel.setForeground(result.signalValue() >= 5
                ? new Color(80, 180, 80) : new Color(200, 100, 60));
            signalLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.add(signalLabel);
            box.add(Box.createVerticalStrut(2));
        }

        // Result content
        int contentWidth = boxWidth - 20;
        if (result.hasError()) {
            renderError(box, result, contentWidth);
        } else if (result.itemResults() != null && !result.itemResults().isEmpty()) {
            renderStructuredList(box, result, contentWidth);
        } else if (result.fields() != null && !result.fields().isEmpty()) {
            renderStructuredSingle(box, result, contentWidth);
        } else if (result.textResult() != null) {
            renderText(box, result, contentWidth);
        } else if (result.listResult() != null) {
            renderList(box, result, contentWidth);
        } else if (result.entityResult() != null && result.entityResult().entities() != null) {
            JLabel countLabel = new JLabel(result.entityResult().entities().size() + " entities");
            countLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
            countLabel.setForeground(textSecondary());
            countLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.add(countLabel);
        }

        // Click to open detail dialog
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        box.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                new ChallengeResultDialog(owner, result, store).setVisible(true);
            }
        });

        // Fix width so BoxLayout doesn't inflate timeline preferred width
        Dimension pref = box.getPreferredSize();
        box.setPreferredSize(new Dimension(boxWidth, pref.height));
        return box;
    }

    private void renderError(JPanel box, ChallengeResult result, int contentWidth) {
        JLabel errLabel = new JLabel("ERROR");
        errLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        errLabel.setForeground(new Color(220, 60, 60));
        errLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(errLabel);
        if (result.error() != null) {
            JLabel errDetail = new JLabel("<html><body style='width:" + contentWidth + "px'>"
                + escapeHtml(result.error()) + "</body></html>");
            errDetail.setFont(new Font("SansSerif", Font.PLAIN, 9));
            errDetail.setForeground(textMuted());
            errDetail.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.add(errDetail);
        }
    }

    private void renderStructuredList(JPanel box, ChallengeResult result, int contentWidth) {
        Challenge ch = store.getChallenge(result.challengeId());
        List<ChallengeOutput.Field> fieldDefs = (ch != null && ch.output().fields() != null)
            ? ch.output().fields() : List.of();

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='width:").append(contentWidth).append("px'>");
        html.append("<table cellspacing='0' cellpadding='1' style='font-size:9px'>");

        // Header row
        html.append("<tr>");
        for (ChallengeOutput.Field f : fieldDefs) {
            String lbl = f.label() != null ? f.label() : f.name();
            int maxLbl = f.primary() ? 16 : 10;
            if (lbl.length() > maxLbl) lbl = lbl.substring(0, maxLbl - 1) + ".";
            html.append("<td><b>").append(escapeHtml(lbl)).append("</b></td>");
        }
        html.append("</tr>");

        // Data rows
        int shown = 0;
        for (Map<String, String> item : result.itemResults()) {
            if (shown >= 10) break;
            boolean isRemoved = "removed".equals(item.get("_status"));
            html.append(isRemoved ? "<tr style='color:gray'>" : "<tr>");
            for (ChallengeOutput.Field f : fieldDefs) {
                String v = item.getOrDefault(f.name(), "");
                int maxLen = f.primary() ? 22 : 14;
                if (v.length() > maxLen) v = v.substring(0, maxLen - 2) + "..";
                boolean isPrimary = f.primary();
                String reason = item.get(f.name() + "_reason");
                String titleAttr = reason != null ? " title='" + escapeHtml(reason).replace("'", "&#39;") + "'" : "";
                if (isRemoved) {
                    html.append("<td").append(titleAttr).append("><s>").append(escapeHtml(v)).append("</s></td>");
                } else {
                    html.append("<td").append(titleAttr).append(">").append(isPrimary ? "<b>" : "").append(escapeHtml(v))
                        .append(isPrimary ? "</b>" : "").append("</td>");
                }
            }
            html.append("</tr>");
            shown++;
        }
        if (result.itemResults().size() > 10) {
            html.append("<tr><td colspan='").append(fieldDefs.size()).append("'><i>+")
                .append(result.itemResults().size() - 10).append(" more</i></td></tr>");
        }
        html.append("</table></body></html>");

        JLabel tableLabel = new JLabel(html.toString());
        tableLabel.setFont(new Font("SansSerif", Font.PLAIN, 9));
        tableLabel.setForeground(textSecondary());
        tableLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(tableLabel);

        // Full table tooltip with _reason details
        StringBuilder tip = new StringBuilder("<html><body style='width:600px'><table cellspacing='2' cellpadding='1'><tr>");
        for (ChallengeOutput.Field f : fieldDefs) {
            tip.append("<th style='text-align:left'><b>").append(escapeHtml(f.label())).append("</b></th>");
        }
        tip.append("</tr>");
        for (Map<String, String> item : result.itemResults()) {
            tip.append("<tr>");
            for (ChallengeOutput.Field f : fieldDefs) {
                String val = item.getOrDefault(f.name(), "");
                String reason = item.get(f.name() + "_reason");
                tip.append("<td>").append(escapeHtml(val));
                if (reason != null) {
                    tip.append("<br><i style='color:gray; font-size:9px'>").append(escapeHtml(reason)).append("</i>");
                }
                tip.append("</td>");
            }
            tip.append("</tr>");
        }
        tip.append("</table></body></html>");
        box.setToolTipText(tip.toString());
    }

    private void renderStructuredSingle(JPanel box, ChallengeResult result, int contentWidth) {
        Challenge ch = store.getChallenge(result.challengeId());
        Map<String, String> labelMap = new LinkedHashMap<>();
        if (ch != null && ch.output().fields() != null) {
            for (ChallengeOutput.Field f : ch.output().fields()) {
                labelMap.put(f.name(), f.label());
            }
        }

        StringBuilder tip = new StringBuilder("<html><body style='width:500px'>");
        for (Map.Entry<String, String> entry : result.fields().entrySet()) {
            String fieldName = entry.getKey();
            if (fieldName.endsWith("_reason")) continue;
            String val = entry.getValue();
            String label = labelMap.getOrDefault(fieldName, fieldName);
            String reason = result.fields().get(fieldName + "_reason");
            boolean isNumber = false;
            try { Double.parseDouble(val); isNumber = true; } catch (NumberFormatException ignored) {}

            if (isNumber) {
                JLabel numLabel = new JLabel(label + ": " + val);
                numLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
                numLabel.setForeground(textPrimary());
                numLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                if (reason != null) {
                    numLabel.setToolTipText("<html><body style='width:400px'>" + escapeHtml(reason) + "</body></html>");
                }
                box.add(numLabel);
            } else {
                JLabel headerLabel = new JLabel(label);
                headerLabel.setFont(new Font("SansSerif", Font.BOLD, 9));
                headerLabel.setForeground(textMuted());
                headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                box.add(headerLabel);
                JLabel textLabel = new JLabel("<html><body style='width:" + contentWidth + "px'>"
                    + escapeHtml(val) + "</body></html>");
                textLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
                textLabel.setForeground(textSecondary());
                textLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                box.add(textLabel);
                box.add(Box.createVerticalStrut(3));
            }
            tip.append("<b>").append(escapeHtml(label)).append(":</b> ")
                .append(escapeHtml(val));
            if (reason != null) {
                tip.append("<br><i style='color:gray'>").append(escapeHtml(reason)).append("</i>");
            }
            tip.append("<br><br>");
        }
        tip.append("</body></html>");
        box.setToolTipText(tip.toString());
    }

    private void renderText(JPanel box, ChallengeResult result, int contentWidth) {
        JLabel textLabel = new JLabel("<html><body style='width:" + contentWidth + "px'>"
            + escapeHtml(result.textResult()) + "</body></html>");
        textLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        textLabel.setForeground(textSecondary());
        textLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(textLabel);
        box.setToolTipText("<html><body style='width:500px'>" + escapeHtml(result.textResult()) + "</body></html>");
    }

    private void renderList(JPanel box, ChallengeResult result, int contentWidth) {
        for (String item : result.listResult()) {
            JLabel itemLabel = new JLabel("\u2022 " + item);
            itemLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
            itemLabel.setForeground(textSecondary());
            itemLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.add(itemLabel);
        }
        StringBuilder tip = new StringBuilder("<html><body style='width:500px'>");
        for (String s : result.listResult()) tip.append("\u2022 ").append(escapeHtml(s)).append("<br>");
        tip.append("</body></html>");
        box.setToolTipText(tip.toString());
    }
}
