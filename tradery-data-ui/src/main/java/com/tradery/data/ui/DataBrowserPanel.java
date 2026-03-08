package com.tradery.data.ui;

import com.tradery.core.model.Exchange;
import com.tradery.dataclient.DataServiceClient;
import com.tradery.dataclient.DataServiceClient.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Left-side browser panel showing available data series.
 * Fetches all data from the data service inventory API.
 * Shows exchange + market type for candles and aggTrades.
 */
public class DataBrowserPanel extends JPanel {

    // Status colors
    private static final Color COMPLETE_COLOR = new Color(76, 175, 80);
    private static final Color PARTIAL_COLOR = new Color(255, 193, 7);
    private static final Color MISSING_COLOR = new Color(100, 100, 100);

    private static final int ROW_HEIGHT = 24;
    private static final int INDENT = 20;
    private static final DecimalFormat COUNT_FORMAT = new DecimalFormat("#,###");


    private final DataServiceClient client;
    private final List<RowData> rows = new ArrayList<>();

    // Cached data
    private volatile InventoryResponse inventory;
    private volatile List<DataServiceClient.HlTradeSubscription> hlSubscriptions;
    private volatile boolean loading = true;
    private Map<String, Long> symbolSizes = Map.of();
    private Map<String, Long> dataTypeSizes = Map.of();
    private Map<String, List<DataServiceClient.ExchangeMarketStats>> exchangeStats = Map.of();

    private String selectedSymbol;
    private String selectedResolution; // timeframe, "aggTrades", "funding", "openInterest", "premiumIndex", "fearGreed"
    private String selectedExchange;   // for aggTrades/candles with exchange info
    private String selectedMarketType; // for candles with market type
    private String selectedSubKey;     // additional disambiguation (e.g., interval for premium index)
    private int hoveredRow = -1;

    private BiConsumer<String, String> onSelectionChanged;

    public DataBrowserPanel(DataServiceClient client) {
        this.client = client;

        setPreferredSize(new Dimension(200, 300));

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = e.getY() / ROW_HEIGHT;
                if (row >= 0 && row < rows.size()) {
                    RowData data = rows.get(row);
                    if (data.selectable) {
                        selectedSymbol = data.symbol;
                        selectedResolution = data.resolution;
                        selectedExchange = data.exchange;
                        selectedMarketType = data.marketType;
                        selectedSubKey = data.subKey;
                        repaint();
                        if (onSelectionChanged != null) {
                            onSelectionChanged.accept(data.symbol, data.resolution);
                        }
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int row = e.getY() / ROW_HEIGHT;
                if (row != hoveredRow) {
                    hoveredRow = row;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoveredRow = -1;
                repaint();
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);

        // Initial data load
        refreshData();
    }

    public void setOnSelectionChanged(BiConsumer<String, String> callback) {
        this.onSelectionChanged = callback;
    }

    public String getSelectedSymbol() { return selectedSymbol; }
    public String getSelectedResolution() { return selectedResolution; }
    public String getSelectedExchange() { return selectedExchange; }
    public String getSelectedMarketType() { return selectedMarketType; }
    public String getSelectedSubKey() { return selectedSubKey; }

    /**
     * Get the cached inventory response (for info display in dialog).
     */
    public InventoryResponse getInventory() { return inventory; }

    /**
     * Find the SymbolInventory for a given symbol name.
     */
    public SymbolInventory findSymbolInventory(String symbol) {
        if (inventory == null || inventory.symbols() == null) return null;
        return inventory.symbols().stream()
            .filter(s -> s.symbol().equals(symbol))
            .findFirst().orElse(null);
    }

    /**
     * Refresh data from the data service inventory API.
     */
    public void refreshData() {
        boolean isFirstLoad = inventory == null;
        if (isFirstLoad) {
            loading = true;
            SwingUtilities.invokeLater(() -> { rebuildRows(); repaint(); });
        }
        Thread.startVirtualThread(() -> {
            try {
                if (client == null) return;
                InventoryResponse inv = client.getInventory();
                Map<String, Long> sizes = Map.of();
                Map<String, Long> dtSizes = Map.of();
                try {
                    DiskUsageResponse du = client.getDiskUsage();
                    if (du.bySymbol() != null) sizes = du.bySymbol();
                    if (du.byDataType() != null) dtSizes = du.byDataType();
                } catch (Exception ignored) {}
                Map<String, List<DataServiceClient.ExchangeMarketStats>> exStats = Map.of();
                try {
                    DataServiceClient.SymbolStats ss = client.getSymbolStats();
                    if (ss.byExchange() != null) exStats = ss.byExchange();
                } catch (Exception ignored) {}
                List<DataServiceClient.HlTradeSubscription> hlSubs = List.of();
                try {
                    hlSubs = client.getHlTradeSubscriptions();
                } catch (Exception ignored) {}
                Map<String, Long> finalSizes = sizes;
                Map<String, Long> finalDtSizes = dtSizes;
                Map<String, List<DataServiceClient.ExchangeMarketStats>> finalExStats = exStats;
                List<DataServiceClient.HlTradeSubscription> finalHlSubs = hlSubs;
                SwingUtilities.invokeLater(() -> {
                    this.inventory = inv;
                    this.symbolSizes = finalSizes;
                    this.dataTypeSizes = finalDtSizes;
                    this.exchangeStats = finalExStats;
                    this.hlSubscriptions = finalHlSubs;
                    this.loading = false;
                    rebuildRows();
                    revalidate();
                    repaint();
                });
            } catch (Exception e) {
                // Silently fail - data service may not be running
                System.err.println("Failed to load inventory: " + e.getMessage());
                loading = false;
            }
        });
    }

    private void rebuildRows() {
        rows.clear();

        // Show loading indicator on first load
        if (loading && inventory == null) {
            rows.add(RowData.emptyLabel("Loading inventory..."));
            setPreferredSize(new Dimension(200, rows.size() * ROW_HEIGHT + 10));
            return;
        }

        // Always show all categories, even when inventory is null (data service not connected)
        List<SymbolInventory> symbols = (inventory != null && inventory.symbols() != null)
            ? inventory.symbols() : List.of();

        // === HL Trade Collection ===
        if (hlSubscriptions != null && !hlSubscriptions.isEmpty()) {
            rows.add(RowData.sectionHeader("HL Trade Collection"));
            for (DataServiceClient.HlTradeSubscription sub : hlSubscriptions) {
                String info = COUNT_FORMAT.format(sub.tradeCount()) + " trades";
                rows.add(new RowData(null, "hlTradeCollection", sub.exchange(), null, sub.coin(),
                    true, false, false, false, 1, sub.coin(), info, COMPLETE_COLOR));
            }
        }

        // === Symbols & Pairs ===
        rows.add(RowData.sectionHeader("Symbols & Pairs"));
        if (!exchangeStats.isEmpty()) {
            for (var entry : exchangeStats.entrySet()) {
                String exchange = entry.getKey();
                int totalPairs = entry.getValue().stream().mapToInt(DataServiceClient.ExchangeMarketStats::pairCount).sum();
                String info = totalPairs + " pairs";
                rows.add(new RowData(null, "exchange", exchange, null, null,
                    true, false, false, false, 1, formatExchange(exchange), info, null));
            }
        } else {
            rows.add(RowData.emptyLabel("No exchange data"));
        }

        // === Candles ===
        rows.add(RowData.sectionHeader("Candles (OHLCV)"));
        boolean hasCandles = symbols.stream().anyMatch(s -> s.candles() != null && !s.candles().isEmpty());
        if (hasCandles) {
            for (SymbolInventory sym : symbols) {
                if (sym.candles() == null || sym.candles().isEmpty()) continue;

                rows.add(symbolHeaderWithSize(sym.symbol(), "candles.db"));

                for (CandleInventory c : sym.candles()) {
                    String label = formatExchange(c.exchange()) + " " + formatMarketType(c.marketType()) + " " + c.timeframe();
                    rows.add(new RowData(sym.symbol(), c.timeframe(), c.exchange(), c.marketType(), null,
                        true, false, false, false, 2, label, null, COMPLETE_COLOR));
                }
            }
        } else {
            rows.add(RowData.emptyLabel("No candle data"));
        }

        // === Aggregated Trades ===
        rows.add(RowData.sectionHeader("Aggregated Trades"));
        boolean hasAggTrades = symbols.stream().anyMatch(s -> s.aggTrades() != null && !s.aggTrades().isEmpty());
        if (hasAggTrades) {
            for (SymbolInventory sym : symbols) {
                if (sym.aggTrades() == null || sym.aggTrades().isEmpty()) continue;

                rows.add(symbolHeaderWithSize(sym.symbol(), "agg_trades.db"));

                for (AggTradesInventory at : sym.aggTrades()) {
                    String label = formatExchange(at.exchange()) + " " + formatMarketType(at.marketType());
                    rows.add(new RowData(sym.symbol(), "aggTrades", at.exchange(), at.marketType(), null,
                        true, false, false, false, 2, label, null, COMPLETE_COLOR));
                }
            }
        } else {
            rows.add(RowData.emptyLabel("No aggregated trades"));
        }

        // === Funding Rate ===
        rows.add(RowData.sectionHeader("Funding Rate (8h)"));
        boolean hasFunding = symbols.stream().anyMatch(s -> s.funding() != null);
        if (hasFunding) {
            for (SymbolInventory sym : symbols) {
                if (sym.funding() == null) continue;
                rows.add(symbolHeaderWithSize(sym.symbol(), "funding_rates.db"));
                rows.add(new RowData(sym.symbol(), "fundingRate", "binance", "perp", null,
                    true, false, false, false, 2, "Binance Futures", null, COMPLETE_COLOR));
            }
        } else {
            rows.add(RowData.emptyLabel("No funding data"));
        }

        // === Open Interest ===
        rows.add(RowData.sectionHeader("Open Interest (5m)"));
        boolean hasOi = symbols.stream().anyMatch(s -> s.openInterest() != null);
        if (hasOi) {
            for (SymbolInventory sym : symbols) {
                if (sym.openInterest() == null) continue;
                rows.add(symbolHeaderWithSize(sym.symbol(), "open_interest.db"));
                rows.add(new RowData(sym.symbol(), "openInterest", "binance", "perp", null,
                    true, false, false, false, 2, "Binance Futures", null, COMPLETE_COLOR));
            }
        } else {
            rows.add(RowData.emptyLabel("No open interest data"));
        }

        // === Premium Index ===
        rows.add(RowData.sectionHeader("Premium Index"));
        boolean hasPremium = symbols.stream().anyMatch(s -> s.premiumIndex() != null && !s.premiumIndex().isEmpty());
        if (hasPremium) {
            for (SymbolInventory sym : symbols) {
                if (sym.premiumIndex() == null || sym.premiumIndex().isEmpty()) continue;
                rows.add(symbolHeaderWithSize(sym.symbol(), "premium_index.db"));
                for (PremiumIndexInventory pi : sym.premiumIndex()) {
                    rows.add(new RowData(sym.symbol(), "premiumIndex", "binance", "perp", pi.interval(),
                        true, false, false, false, 2, "Binance " + pi.interval(), null, COMPLETE_COLOR));
                }
            }
        } else {
            rows.add(RowData.emptyLabel("No premium index data"));
        }

        // === Volume Profiles ===
        rows.add(RowData.sectionHeader("Volume Profiles"));
        boolean hasProfiles = symbols.stream().anyMatch(s -> s.volumeProfiles() != null && !s.volumeProfiles().isEmpty());
        if (hasProfiles) {
            for (SymbolInventory sym : symbols) {
                if (sym.volumeProfiles() == null || sym.volumeProfiles().isEmpty()) continue;
                rows.add(symbolHeaderWithSize(sym.symbol(), "volume_profiles.db"));
                for (VolumeProfileInventory vp : sym.volumeProfiles()) {
                    String mt = vp.marketType() != null ? vp.marketType() : "perp";
                    String label = vp.timeframe() + " (" + mt + ")";
                    String info = COUNT_FORMAT.format(vp.recordCount()) + " windows";
                    rows.add(new RowData(sym.symbol(), "volumeProfile:" + vp.timeframe() + ":" + mt, null, null, vp.timeframe(),
                        true, false, false, false, 2, label, info, COMPLETE_COLOR));
                }
            }
        } else {
            rows.add(RowData.emptyLabel("No volume profile data"));
        }

        // === Trade Size Spectrum ===
        rows.add(RowData.sectionHeader("Trade Size Spectrum"));
        boolean hasSpectrum = symbols.stream().anyMatch(s -> s.spectrum() != null);
        if (hasSpectrum) {
            for (SymbolInventory sym : symbols) {
                if (sym.spectrum() == null) continue;
                rows.add(symbolHeaderWithSize(sym.symbol(), "spectrum.db"));
                String info = COUNT_FORMAT.format(sym.spectrum().recordCount()) + " records";
                rows.add(new RowData(sym.symbol(), "spectrum", null, null, null,
                    true, false, false, false, 2, "All modes", info, COMPLETE_COLOR));
            }
        } else {
            rows.add(RowData.emptyLabel("No spectrum data"));
        }

        // === Fear & Greed ===
        rows.add(RowData.sectionHeader("Fear & Greed Index"));
        if (inventory != null && inventory.fearGreed() != null) {
            FearGreedInventory fg = inventory.fearGreed();
            rows.add(new RowData(null, "fearGreed", null, null, null,
                true, false, false, false, 1, "Global", COUNT_FORMAT.format(fg.recordCount()) + " entries", COMPLETE_COLOR));
        } else {
            rows.add(RowData.emptyLabel("No fear & greed data"));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int y = 0;
        for (int i = 0; i < rows.size(); i++) {
            RowData row = rows.get(i);

            if (row.isSectionHeader) {
                drawSectionHeader(g2, row.label, y, i > 0);
                y += ROW_HEIGHT;
                continue;
            }

            if (row.isSubHeader) {
                // Market type / exchange sub-header
                g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
                g2.setColor(UIManager.getColor("Label.disabledForeground"));
                g2.drawString(row.label, INDENT + 8, y + 15);
                y += ROW_HEIGHT;
                continue;
            }

            if (row.isEmptyLabel) {
                // Empty category placeholder (plain font, not italic)
                g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                g2.setColor(UIManager.getColor("Label.disabledForeground"));
                g2.drawString(row.label, INDENT + 8, y + 15);
                y += ROW_HEIGHT;
                continue;
            }

            // Selection/hover
            boolean isSelected = row.selectable && isRowSelected(row);
            boolean isHovered = i == hoveredRow && row.selectable;

            if (isSelected) {
                g2.setColor(UIManager.getColor("List.selectionBackground"));
                g2.fillRect(0, y, getWidth(), ROW_HEIGHT);
            } else if (isHovered) {
                g2.setColor(UIManager.getColor("List.selectionInactiveBackground"));
                g2.fillRect(0, y, getWidth(), ROW_HEIGHT);
            }

            // Draw row content
            int x = row.indentLevel * INDENT + 8;

            // Label
            g2.setFont(new Font(Font.SANS_SERIF, row.indentLevel <= 1 ? Font.BOLD : Font.PLAIN,
                row.indentLevel <= 1 ? 12 : 11));
            g2.setColor(UIManager.getColor("Label.foreground"));
            g2.drawString(row.label, x, y + 16);

            // Status dot + info (for data rows)
            if (row.info != null) {
                int labelWidth = g2.getFontMetrics().stringWidth(row.label);
                int infoX = x + labelWidth + 6;

                if (row.statusColor != null) {
                    g2.setColor(row.statusColor);
                    g2.fillOval(infoX, y + 8, 8, 8);
                    infoX += 12;
                }

                g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
                g2.setColor(UIManager.getColor("Label.disabledForeground"));
                g2.drawString(row.info, infoX, y + 15);
            }

            y += ROW_HEIGHT;
        }

        g2.dispose();
    }

    private boolean isRowSelected(RowData row) {
        if (!Objects.equals(row.symbol, selectedSymbol)) return false;
        if (!Objects.equals(row.resolution, selectedResolution)) return false;
        // For aggTrades and candles, also match exchange/marketType if set
        if (selectedExchange != null && !Objects.equals(row.exchange, selectedExchange)) return false;
        if (selectedMarketType != null && row.marketType != null && !Objects.equals(row.marketType, selectedMarketType)) return false;
        if (!Objects.equals(row.subKey, selectedSubKey)) return false;
        return true;
    }

    private void drawSectionHeader(Graphics2D g2, String title, int y, boolean drawSeparator) {
        if (drawSeparator) {
            g2.setColor(UIManager.getColor("Separator.foreground"));
            g2.drawLine(8, y + 2, getWidth() - 8, y + 2);
        }

        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        Color accentColor = UIManager.getColor("Component.accentColor");
        if (accentColor == null) accentColor = UIManager.getColor("Focus.color");
        if (accentColor == null) accentColor = new Color(0, 122, 255);
        g2.setColor(accentColor);
        g2.drawString(title, 8, y + 17);
    }

    @Override
    public Dimension getPreferredSize() {
        int height = Math.max(300, rows.size() * ROW_HEIGHT + 10);
        return new Dimension(200, height);
    }

    // ========== Formatting helpers ==========

    /**
     * Symbol header row that shows the per-data-type DB file size next to the symbol name.
     * @param dbFilename e.g. "candles.db", "agg_trades.db"
     */
    private RowData symbolHeaderWithSize(String symbol, String dbFilename) {
        String size = sizeOfDataType(symbol, dbFilename);
        return new RowData(symbol, null, null, null, null, false, false, false, false, 1, symbol, size, null);
    }

    private String sizeOfDataType(String symbol, String dbFilename) {
        Long bytes = dataTypeSizes.get(symbol + ":" + dbFilename);
        return bytes != null ? formatSize(bytes) : null;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.0f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String formatMarketType(String mt) {
        if (mt == null) return "";
        return switch (mt) {
            case "perp" -> "Futures";
            case "spot" -> "Spot";
            case "dated" -> "Dated";
            default -> mt;
        };
    }

    private String formatExchange(String exchange) {
        return Exchange.formatDisplayName(exchange);
    }

    // Row data model
    private record RowData(
        String symbol,
        String resolution,     // timeframe or "aggTrades"/"fundingRate"/"openInterest"/"premiumIndex"/"fearGreed"
        String exchange,       // exchange key (for aggTrades/candles)
        String marketType,     // market type (for candles)
        String subKey,         // additional disambiguation (e.g., interval for premium index)
        boolean selectable,
        boolean isSectionHeader,
        boolean isSubHeader,
        boolean isEmptyLabel,  // "No data" placeholder
        int indentLevel,       // 0=section, 1=symbol/top, 2=timeframe/exchange, 3=deep
        String label,          // display text
        String info,           // secondary text (date range, count)
        Color statusColor      // dot color
    ) {
        static RowData sectionHeader(String title) {
            return new RowData(null, null, null, null, null, false, true, false, false, 0, title, null, null);
        }

        static RowData symbolHeader(String symbol) {
            return new RowData(symbol, null, null, null, null, false, false, false, false, 1, symbol, null, null);
        }

        static RowData subHeader(String symbol, String label) {
            return new RowData(symbol, null, null, null, null, false, false, true, false, 2, label, null, null);
        }

        static RowData emptyLabel(String text) {
            return new RowData(null, null, null, null, null, false, false, false, true, 1, text, null, null);
        }
    }
}
