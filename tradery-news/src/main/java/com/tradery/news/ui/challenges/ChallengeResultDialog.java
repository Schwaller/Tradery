package com.tradery.news.ui.challenges;

import com.tradery.ai.challenges.model.Challenge;
import com.tradery.ai.challenges.model.ChallengeOutput;
import com.tradery.ai.challenges.model.ChallengeResult;
import com.tradery.ai.challenges.store.ChallengeStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static com.tradery.news.ui.challenges.ChallengeTheme.*;

/**
 * Read-only detail view of a single challenge result.
 * Shows full data including justifications, metadata, and signal value.
 */
public class ChallengeResultDialog extends JDialog {

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public ChallengeResultDialog(Window owner, ChallengeResult result, ChallengeStore store) {
        super(owner, "Challenge Result", ModalityType.MODELESS);

        Challenge challenge = store.getChallenge(result.challengeId());
        String title = challenge != null ? challenge.title() : result.challengeId();

        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty("FlatLaf.macOS.windowButtonsSpacing", "large");

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(48, 20, 16, 20));
        content.setBackground(bgMain());

        // Title
        addLabel(content, title, new Font("SansSerif", Font.BOLD, 16), textPrimary());

        // Metadata line
        String time = TIME_FMT.format(Instant.ofEpochMilli(result.timestamp()));
        String meta = time;
        if (result.durationMs() > 0) {
            meta += "  ·  " + formatDuration(result.durationMs());
        }
        if (result.resolvedTier() != null) {
            meta += "  ·  " + result.resolvedTier();
        }
        if (result.verified()) {
            meta += "  ·  verified";
        }
        addLabel(content, meta, new Font("SansSerif", Font.PLAIN, 11), textMuted());
        content.add(Box.createVerticalStrut(8));

        // Signal
        if (result.hasSignal()) {
            JPanel signalRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            signalRow.setBackground(bgMain());
            signalRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel signalTitle = new JLabel("Signal:");
            signalTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
            signalTitle.setForeground(textSecondary());
            signalRow.add(signalTitle);

            JLabel signalVal = new JLabel(String.format("%.2f", result.signalValue()));
            signalVal.setFont(new Font("SansSerif", Font.BOLD, 14));
            signalVal.setForeground(result.signalValue() >= 5
                ? new Color(80, 180, 80) : new Color(200, 100, 60));
            signalRow.add(signalVal);

            content.add(signalRow);
            content.add(Box.createVerticalStrut(8));
        }

        // Separator
        addSeparator(content);

        // Result body
        if (result.hasError()) {
            renderError(content, result);
        } else if (result.itemResults() != null && !result.itemResults().isEmpty()) {
            renderStructuredList(content, result, challenge);
        } else if (result.fields() != null && !result.fields().isEmpty()) {
            renderStructuredSingle(content, result, challenge);
        } else if (result.textResult() != null) {
            renderText(content, result);
        } else if (result.listResult() != null) {
            renderList(content, result);
        }

        content.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        setContentPane(scroll);

        setMinimumSize(new Dimension(420, 300));
        setSize(new Dimension(560, 500));
        setLocationRelativeTo(owner);
    }

    private void renderError(JPanel content, ChallengeResult result) {
        addLabel(content, "Error", new Font("SansSerif", Font.BOLD, 13), new Color(220, 60, 60));
        if (result.error() != null) {
            addWrappedText(content, result.error(), textSecondary());
        }
    }

    private void renderStructuredSingle(JPanel content, ChallengeResult result, Challenge challenge) {
        List<ChallengeOutput.Field> fieldDefs = challenge != null && challenge.output().fields() != null
            ? challenge.output().fields() : List.of();

        for (Map.Entry<String, String> entry : result.fields().entrySet()) {
            String fieldName = entry.getKey();
            if (fieldName.endsWith("_reason")) continue;
            String val = entry.getValue();
            String reason = result.fields().get(fieldName + "_reason");

            // Resolve label
            String label = fieldName;
            for (ChallengeOutput.Field f : fieldDefs) {
                if (f.name().equals(fieldName)) {
                    label = f.label() != null ? f.label() : f.name();
                    break;
                }
            }

            boolean isNumber = false;
            try { Double.parseDouble(val); isNumber = true; } catch (NumberFormatException ignored) {}

            content.add(Box.createVerticalStrut(10));

            // Field label
            addLabel(content, label, new Font("SansSerif", Font.BOLD, 11), textMuted());

            // Value
            if (isNumber) {
                addLabel(content, val, new Font("SansSerif", Font.BOLD, 16), textPrimary());
            } else {
                addWrappedText(content, val, textPrimary());
            }

            // Justification
            if (reason != null && !reason.isBlank()) {
                content.add(Box.createVerticalStrut(2));
                addWrappedText(content, reason, textMuted());
            }
        }
    }

    private void renderStructuredList(JPanel content, ChallengeResult result, Challenge challenge) {
        List<ChallengeOutput.Field> fieldDefs = challenge != null && challenge.output().fields() != null
            ? challenge.output().fields() : List.of();

        addLabel(content, result.itemResults().size() + " items",
            new Font("SansSerif", Font.PLAIN, 11), textMuted());
        content.add(Box.createVerticalStrut(8));

        for (int idx = 0; idx < result.itemResults().size(); idx++) {
            Map<String, String> item = result.itemResults().get(idx);
            boolean isRemoved = "removed".equals(item.get("_status"));

            // Item card
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(darker(bgCard(), 0.03f));
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(darker(bgMain(), 0.08f), 1),
                new EmptyBorder(8, 10, 8, 10)
            ));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

            for (ChallengeOutput.Field f : fieldDefs) {
                String val = item.getOrDefault(f.name(), "");
                String reason = item.get(f.name() + "_reason");
                if (val.isEmpty() && reason == null) continue;

                String label = f.label() != null ? f.label() : f.name();
                Color valColor = isRemoved ? textMuted() : (f.primary() ? textPrimary() : textSecondary());
                Font valFont = f.primary()
                    ? new Font("SansSerif", Font.BOLD, 12)
                    : new Font("SansSerif", Font.PLAIN, 11);

                // Label: Value on same conceptual line
                JPanel fieldRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                fieldRow.setBackground(card.getBackground());
                fieldRow.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel lblComp = new JLabel(label + ":");
                lblComp.setFont(new Font("SansSerif", Font.BOLD, 10));
                lblComp.setForeground(textMuted());
                fieldRow.add(lblComp);

                JLabel valComp = new JLabel(isRemoved ? "(" + val + ")" : val);
                valComp.setFont(isRemoved ? valFont.deriveFont(Font.ITALIC) : valFont);
                valComp.setForeground(valColor);
                fieldRow.add(valComp);

                card.add(fieldRow);

                // Justification indented
                if (reason != null && !reason.isBlank()) {
                    JLabel reasonLabel = new JLabel("<html><body style='width:400px'>"
                        + escapeHtml(reason) + "</body></html>");
                    reasonLabel.setFont(new Font("SansSerif", Font.ITALIC, 10));
                    reasonLabel.setForeground(textMuted());
                    reasonLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    reasonLabel.setBorder(new EmptyBorder(0, 8, 2, 0));
                    card.add(reasonLabel);
                }
            }

            if (isRemoved) {
                JLabel removedBadge = new JLabel("removed");
                removedBadge.setFont(new Font("SansSerif", Font.ITALIC, 9));
                removedBadge.setForeground(new Color(200, 100, 60));
                removedBadge.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.add(removedBadge);
            }

            content.add(card);
            content.add(Box.createVerticalStrut(4));
        }
    }

    private void renderText(JPanel content, ChallengeResult result) {
        addWrappedText(content, result.textResult(), textPrimary());
    }

    private void renderList(JPanel content, ChallengeResult result) {
        for (String item : result.listResult()) {
            addLabel(content, "\u2022 " + item, new Font("SansSerif", Font.PLAIN, 12), textPrimary());
        }
    }

    // ==================== Helpers ====================

    private static void addLabel(JPanel parent, String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(label);
    }

    private static void addWrappedText(JPanel parent, String text, Color color) {
        JLabel label = new JLabel("<html><body style='width:480px'>" + escapeHtml(text) + "</body></html>");
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(label);
    }

    private static void addSeparator(JPanel parent) {
        JSeparator sep = new JSeparator();
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        parent.add(sep);
    }

    private static String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        double sec = ms / 1000.0;
        return String.format("%.1fs", sec);
    }
}
