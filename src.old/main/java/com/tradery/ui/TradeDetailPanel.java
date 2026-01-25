package com.tradery.ui;

import com.tradery.model.Trade;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Panel showing detailed explanatory information about a selected trade.
 * Displays MFE/MAE, phases, timing, and quality metrics in plain English.
 */
public class TradeDetailPanel extends JPanel {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd HH:mm");
    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private final JTextPane detailsPane;
    private final JLabel titleLabel;

    public TradeDetailPanel() {
        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Header
        titleLabel = new JLabel("Select a trade to see details");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11f));
        titleLabel.setForeground(Color.GRAY);

        // Details pane - using HTML for formatting
        detailsPane = new JTextPane();
        detailsPane.setContentType("text/html");
        detailsPane.setEditable(false);
        detailsPane.setOpaque(false);
        detailsPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        detailsPane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        JScrollPane scrollPane = new JScrollPane(detailsPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(titleLabel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        clear();
    }

    /**
     * Update the panel to show details for the selected trade(s)
     */
    public void setTrades(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            clear();
            return;
        }

        if (trades.size() == 1) {
            showSingleTrade(trades.get(0));
        } else {
            showTradeGroup(trades);
        }
    }

    public void clear() {
        titleLabel.setText("Select a trade to see details");
        detailsPane.setText("<html><body style='font-family:sans-serif;font-size:10px;color:#888;'>" +
            "Click on a trade in the table above to see detailed analysis.</body></html>");
    }

    private void showSingleTrade(Trade trade) {
        if ("rejected".equals(trade.exitReason())) {
            showRejectedTrade(trade);
            return;
        }

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:sans-serif;font-size:10px;'>");

        // Title
        String pnlColor = trade.pnl() != null && trade.pnl() >= 0 ? "#4CAF50" : "#F44336";
        String pnlSign = trade.pnl() != null && trade.pnl() >= 0 ? "+" : "";
        titleLabel.setText(String.format("Trade #%s  •  %s%s (%.2f%%)",
            trade.id().substring(trade.id().length() - 4),
            pnlSign,
            trade.pnl() != null ? String.format("$%.2f", trade.pnl()) : "-",
            trade.pnlPercent() != null ? trade.pnlPercent() : 0));

        // Entry/Exit Summary
        html.append("<div style='margin-bottom:8px;'>");
        html.append("<b style='color:#666;'>Entry & Exit</b><br/>");
        html.append(String.format("Entered at <b>$%.2f</b> on %s<br/>",
            trade.entryPrice(), formatTime(trade.entryTime())));
        if (trade.exitPrice() != null && trade.exitTime() != null) {
            html.append(String.format("Exited at <b>$%.2f</b> on %s<br/>",
                trade.exitPrice(), formatTime(trade.exitTime())));
            long durationMs = trade.exitTime() - trade.entryTime();
            html.append(String.format("Duration: <b>%s</b>", formatDuration(durationMs)));
        }
        html.append("</div>");

        // Exit Reason
        if (trade.exitReason() != null) {
            html.append("<div style='margin-bottom:8px;'>");
            html.append("<b style='color:#666;'>Exit Reason</b><br/>");
            html.append(explainExitReason(trade.exitReason(), trade.exitZone()));
            html.append("</div>");
        }

        // MFE/MAE Analysis
        if (trade.mfe() != null || trade.mae() != null) {
            html.append("<div style='margin-bottom:8px;'>");
            html.append("<b style='color:#666;'>Trade Quality</b><br/>");

            if (trade.mfe() != null) {
                html.append(String.format("<span style='color:#4CAF50;'>Best unrealized P&L: <b>+%.2f%%</b></span>",
                    trade.mfe()));
                if (trade.mfeBar() != null) {
                    int barsToMfe = trade.mfeBar() - trade.entryBar();
                    html.append(String.format(" (after %d bars)", barsToMfe));
                }
                html.append("<br/>");
            }

            if (trade.mae() != null) {
                html.append(String.format("<span style='color:#F44336;'>Worst drawdown: <b>%.2f%%</b></span>",
                    trade.mae()));
                if (trade.maeBar() != null) {
                    int barsToMae = trade.maeBar() - trade.entryBar();
                    html.append(String.format(" (after %d bars)", barsToMae));
                }
                html.append("<br/>");
            }

            // Capture ratio explanation
            if (trade.mfe() != null && trade.mfe() > 0 && trade.pnlPercent() != null) {
                double captureRatio = trade.pnlPercent() / trade.mfe();
                String captureColor = captureRatio > 0.7 ? "#4CAF50" : captureRatio > 0.4 ? "#FF9800" : "#F44336";
                html.append(String.format("<br/><span style='color:%s;'>Captured <b>%.0f%%</b> of the move</span>",
                    captureColor, captureRatio * 100));
                if (captureRatio < 0.5) {
                    html.append("<br/><i style='color:#888;font-size:9px;'>Exit may have been too early</i>");
                } else if (captureRatio > 0.9) {
                    html.append("<br/><i style='color:#888;font-size:9px;'>Excellent exit timing</i>");
                }
            }
            html.append("</div>");
        }

        // Phase Context
        if ((trade.activePhasesAtEntry() != null && !trade.activePhasesAtEntry().isEmpty()) ||
            (trade.activePhasesAtExit() != null && !trade.activePhasesAtExit().isEmpty())) {
            html.append("<div style='margin-bottom:8px;'>");
            html.append("<b style='color:#666;'>Market Context</b><br/>");

            if (trade.activePhasesAtEntry() != null && !trade.activePhasesAtEntry().isEmpty()) {
                html.append("At entry: ");
                html.append(formatPhases(trade.activePhasesAtEntry()));
                html.append("<br/>");
            }

            if (trade.activePhasesAtExit() != null && !trade.activePhasesAtExit().isEmpty()) {
                html.append("At exit: ");
                html.append(formatPhases(trade.activePhasesAtExit()));
            }
            html.append("</div>");
        }

        // Indicator Values at Entry
        if (trade.entryIndicators() != null && !trade.entryIndicators().isEmpty()) {
            html.append("<div style='margin-bottom:8px;'>");
            html.append("<b style='color:#666;'>Indicators at Entry</b><br/>");
            html.append(formatIndicators(trade.entryIndicators(), trade.entryPrice()));
            html.append("</div>");
        }

        // Indicator Values at Exit (if different from entry)
        if (trade.exitIndicators() != null && !trade.exitIndicators().isEmpty()) {
            html.append("<div style='margin-bottom:8px;'>");
            html.append("<b style='color:#666;'>Indicators at Exit</b><br/>");
            html.append(formatIndicators(trade.exitIndicators(), trade.exitPrice()));
            html.append("</div>");
        }

        html.append("</body></html>");
        detailsPane.setText(html.toString());
        detailsPane.setCaretPosition(0);
    }

    private void showRejectedTrade(Trade trade) {
        titleLabel.setText("Rejected Trade");

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:sans-serif;font-size:10px;'>");
        html.append("<div style='color:#888;'>");
        html.append("<b>Signal fired but trade was rejected</b><br/><br/>");
        html.append("The entry condition was met, but the trade could not be executed ");
        html.append("due to insufficient available capital.<br/><br/>");
        html.append(String.format("Signal at <b>$%.2f</b> on %s",
            trade.entryPrice(), formatTime(trade.entryTime())));

        if (trade.activePhasesAtEntry() != null && !trade.activePhasesAtEntry().isEmpty()) {
            html.append("<br/><br/>Market context: ");
            html.append(formatPhases(trade.activePhasesAtEntry()));
        }
        html.append("</div>");
        html.append("</body></html>");

        detailsPane.setText(html.toString());
        detailsPane.setCaretPosition(0);
    }

    private void showTradeGroup(List<Trade> trades) {
        double totalPnl = trades.stream()
            .filter(t -> t.pnl() != null)
            .mapToDouble(Trade::pnl)
            .sum();

        String pnlSign = totalPnl >= 0 ? "+" : "";
        titleLabel.setText(String.format("DCA Position  •  %d entries  •  %s$%.2f",
            trades.size(), pnlSign, totalPnl));

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:sans-serif;font-size:10px;'>");

        // Group summary
        html.append("<div style='margin-bottom:8px;'>");
        html.append("<b style='color:#666;'>Position Summary</b><br/>");

        double totalValue = 0;
        double totalQty = 0;
        for (Trade t : trades) {
            totalValue += t.entryPrice() * t.quantity();
            totalQty += t.quantity();
        }
        double avgEntry = totalQty > 0 ? totalValue / totalQty : 0;
        html.append(String.format("Average entry: <b>$%.2f</b><br/>", avgEntry));
        html.append(String.format("Total quantity: <b>%.6f</b><br/>", totalQty));

        // Time span
        long firstEntry = trades.stream().mapToLong(Trade::entryTime).min().orElse(0);
        long lastExit = trades.stream()
            .filter(t -> t.exitTime() != null)
            .mapToLong(Trade::exitTime)
            .max()
            .orElse(firstEntry);
        html.append(String.format("Duration: <b>%s</b>", formatDuration(lastExit - firstEntry)));
        html.append("</div>");

        // Individual entries
        html.append("<div style='margin-bottom:8px;'>");
        html.append("<b style='color:#666;'>Individual Entries</b><br/>");
        for (int i = 0; i < trades.size(); i++) {
            Trade t = trades.get(i);
            String entryPnlColor = t.pnl() != null && t.pnl() >= 0 ? "#4CAF50" : "#F44336";
            html.append(String.format("%d. $%.2f <span style='color:%s;'>(%+.2f%%)</span><br/>",
                i + 1, t.entryPrice(), entryPnlColor, t.pnlPercent() != null ? t.pnlPercent() : 0));
        }
        html.append("</div>");

        // Aggregate MFE/MAE
        double avgMfe = trades.stream()
            .filter(t -> t.mfe() != null)
            .mapToDouble(Trade::mfe)
            .average()
            .orElse(0);
        double avgMae = trades.stream()
            .filter(t -> t.mae() != null)
            .mapToDouble(Trade::mae)
            .average()
            .orElse(0);

        if (avgMfe != 0 || avgMae != 0) {
            html.append("<div style='margin-bottom:8px;'>");
            html.append("<b style='color:#666;'>Average Trade Quality</b><br/>");
            html.append(String.format("Avg best unrealized: <span style='color:#4CAF50;'><b>+%.2f%%</b></span><br/>", avgMfe));
            html.append(String.format("Avg worst drawdown: <span style='color:#F44336;'><b>%.2f%%</b></span>", avgMae));
            html.append("</div>");
        }

        html.append("</body></html>");
        detailsPane.setText(html.toString());
        detailsPane.setCaretPosition(0);
    }

    private String formatTime(long timestamp) {
        return DATE_FORMAT.format(new Date(timestamp)) + " UTC";
    }

    private String formatDuration(long ms) {
        long hours = ms / (60 * 60 * 1000);
        long minutes = (ms % (60 * 60 * 1000)) / (60 * 1000);

        if (hours >= 24) {
            long days = hours / 24;
            hours = hours % 24;
            return String.format("%dd %dh", days, hours);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    private String explainExitReason(String reason, String zoneName) {
        String explanation = switch (reason) {
            case "signal" -> "Exit condition was met";
            case "stop_loss" -> "Stop loss was triggered to limit losses";
            case "take_profit" -> "Take profit target was reached";
            case "trailing_stop" -> "Trailing stop was triggered after price reversed";
            case "signal_lost" -> "DCA abort: entry signal no longer valid";
            case "end_of_data" -> "Backtest ended with position still open";
            case "rejected" -> "Trade rejected due to insufficient capital";
            case "expired" -> "Pending order expired without filling";
            default -> reason;
        };

        if (zoneName != null && !zoneName.isEmpty()) {
            return String.format("<b>%s</b> - %s", zoneName, explanation);
        }
        return explanation;
    }

    private String formatPhases(List<String> phases) {
        if (phases == null || phases.isEmpty()) return "<i>none</i>";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < phases.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("<span style='background:#e0e0e0;padding:1px 4px;border-radius:3px;'>")
              .append(formatPhaseName(phases.get(i)))
              .append("</span>");
        }
        return sb.toString();
    }

    private String formatPhaseName(String phaseId) {
        // Convert kebab-case to Title Case
        return phaseId.replace("-", " ");
    }

    private String formatIndicators(java.util.Map<String, Double> indicators, Double currentPrice) {
        if (indicators == null || indicators.isEmpty()) return "<i>none</i>";

        StringBuilder sb = new StringBuilder();
        sb.append("<table style='font-size:10px;border-collapse:collapse;'>");

        // Sort indicators by type for better readability
        java.util.List<String> sortedKeys = new java.util.ArrayList<>(indicators.keySet());
        sortedKeys.sort((a, b) -> {
            // Order: price/volume first, then trend (SMA), then momentum (RSI, ADX), then volatility (ATR)
            int orderA = getIndicatorOrder(a);
            int orderB = getIndicatorOrder(b);
            if (orderA != orderB) return orderA - orderB;
            return a.compareTo(b);
        });

        for (String key : sortedKeys) {
            Double value = indicators.get(key);
            if (value == null || Double.isNaN(value)) continue;

            // Skip price/volume as they're shown elsewhere
            if (key.equals("price") || key.equals("volume")) continue;

            sb.append("<tr>");
            sb.append("<td style='padding:1px 8px 1px 0;color:#666;'>").append(key).append(":</td>");
            sb.append("<td style='padding:1px 0;'><b>").append(formatIndicatorValue(key, value, currentPrice)).append("</b></td>");
            sb.append("</tr>");
        }

        sb.append("</table>");
        return sb.toString();
    }

    private int getIndicatorOrder(String indicator) {
        if (indicator.equals("price") || indicator.equals("volume")) return 0;
        if (indicator.startsWith("SMA") || indicator.startsWith("EMA")) return 1;
        if (indicator.startsWith("RSI")) return 2;
        if (indicator.startsWith("ADX") || indicator.startsWith("PLUS_DI") || indicator.startsWith("MINUS_DI")) return 3;
        if (indicator.startsWith("ATR") || indicator.startsWith("AVG_VOLUME")) return 4;
        if (indicator.startsWith("MACD") || indicator.startsWith("BBANDS")) return 5;
        return 9;
    }

    private String formatIndicatorValue(String indicator, double value, Double price) {
        // Format based on indicator type
        if (indicator.startsWith("SMA") || indicator.startsWith("EMA") || indicator.startsWith("BBANDS")) {
            // Price-based indicators: show value and distance from current price
            if (price != null && price > 0) {
                double pctFromPrice = (value - price) / price * 100;
                String color = pctFromPrice >= 0 ? "#4CAF50" : "#F44336";
                return String.format("$%.2f <span style='color:%s;'>(%+.1f%%)</span>", value, color, pctFromPrice);
            }
            return String.format("$%.2f", value);
        } else if (indicator.startsWith("RSI")) {
            // RSI: color-code overbought/oversold
            String color = value > 70 ? "#F44336" : value < 30 ? "#4CAF50" : "#666";
            return String.format("<span style='color:%s;'>%.1f</span>", color, value);
        } else if (indicator.startsWith("ADX")) {
            // ADX: color-code trend strength
            String color = value > 25 ? "#4CAF50" : "#888";
            return String.format("<span style='color:%s;'>%.1f</span>", color, value);
        } else if (indicator.startsWith("ATR")) {
            // ATR: show as percentage of price if possible
            if (price != null && price > 0) {
                double pctOfPrice = value / price * 100;
                return String.format("$%.2f <span style='color:#888;'>(%.2f%%)</span>", value, pctOfPrice);
            }
            return String.format("$%.2f", value);
        } else if (indicator.startsWith("AVG_VOLUME")) {
            return formatVolume(value);
        } else if (indicator.contains("MACD")) {
            return String.format("%.4f", value);
        } else {
            return String.format("%.2f", value);
        }
    }

    private String formatVolume(double volume) {
        if (volume >= 1_000_000_000) {
            return String.format("%.2fB", volume / 1_000_000_000);
        } else if (volume >= 1_000_000) {
            return String.format("%.2fM", volume / 1_000_000);
        } else if (volume >= 1_000) {
            return String.format("%.1fK", volume / 1_000);
        }
        return String.format("%.0f", volume);
    }
}
