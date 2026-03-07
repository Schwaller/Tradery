package com.tradery.symbols.ui;

import com.tradery.core.model.DataMarketType;
import com.tradery.core.model.Exchange;
import com.tradery.symbols.model.SymbolEntry;
import com.tradery.symbols.service.SymbolService;
import com.tradery.symbols.service.SymbolService.CoinInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Symbol picker: 4 cascading JComboBoxes (Exchange → Market → Coin → Quote) + browse button.
 * Switching exchange/market preserves the selected coin via coingecko_base_id resolution.
 */
public class SymbolComboBox extends JPanel {

    private static final List<String> FALLBACK_QUOTES = List.of("USDT", "USD", "BUSD", "BTC");

    private final SymbolService service;
    private final JComboBox<String> exchangeCombo;
    private final JComboBox<String> marketCombo;
    private final JComboBox<CoinItem> coinCombo;
    private final JComboBox<String> quoteCombo;
    private final JButton browseButton;
    private final List<Runnable> changeListeners = new ArrayList<>();

    private boolean suppressEvents = false;

    // Internal state
    private String exchange = "binance";
    private String symbolMarket = "spot";
    private String base = "BTC";
    private String quote = "USDT";
    private String resolvedSymbol = "BTCUSDT";
    private String coingeckoId;   // cached for cross-exchange resolution
    private String coinName;      // display name from coins_cache

    public SymbolComboBox(SymbolService service) {
        this(service, false);
    }

    public SymbolComboBox(SymbolService service, boolean horizontal) {
        this.service = service;
        setOpaque(false);

        exchangeCombo = new JComboBox<>();
        exchangeCombo.addActionListener(e -> onExchangeChanged());

        marketCombo = new JComboBox<>();
        marketCombo.addActionListener(e -> onMarketChanged());

        coinCombo = new JComboBox<>();
        coinCombo.setEditable(true);
        coinCombo.setRenderer(new CoinItemRenderer());
        coinCombo.addActionListener(e -> onCoinChanged());

        quoteCombo = new JComboBox<>();
        quoteCombo.addActionListener(e -> onQuoteChanged());

        browseButton = new JButton("...");
        browseButton.setMargin(new Insets(1, 4, 1, 4));
        browseButton.setToolTipText("Browse symbols");
        browseButton.addActionListener(e -> openChooser());

        if (horizontal) {
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            add(exchangeCombo);
            add(Box.createHorizontalStrut(4));
            add(marketCombo);
            add(Box.createHorizontalStrut(4));
            add(coinCombo);
            add(Box.createHorizontalStrut(4));
            add(quoteCombo);
            add(Box.createHorizontalStrut(4));
            add(browseButton);
        } else {
            setLayout(new GridBagLayout());
            var gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(1, 0, 1, 0);

            // Row 0: Exchange
            gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.weightx = 1.0;
            add(exchangeCombo, gbc);

            // Row 1: Market
            gbc.gridy = 1;
            add(marketCombo, gbc);

            // Row 2: Coin + Quote
            gbc.gridy = 2; gbc.gridwidth = 1;
            add(coinCombo, gbc);
            gbc.gridx = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
            add(quoteCombo, gbc);

            // Row 3: Browse
            gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.EAST;
            add(browseButton, gbc);
        }

        // Initial population
        SwingUtilities.invokeLater(this::populateExchanges);
    }

    public String getExchange() {
        return exchange;
    }

    public String getSymbolMarket() {
        return symbolMarket;
    }

    public String getSelectedSymbol() {
        return resolvedSymbol;
    }

    public String getBase() {
        return base;
    }

    public String getQuote() {
        return quote;
    }

    /**
     * Set all fields programmatically. Uses reverseResolve to extract base/quote/coingeckoId.
     */
    public void setSelection(String exchange, String symbolMarket, String symbol) {
        if (exchange != null) this.exchange = exchange;
        if (symbolMarket != null) this.symbolMarket = symbolMarket;
        if (symbol != null) {
            this.resolvedSymbol = symbol;
            // Reverse resolve to get base/quote/coingeckoId
            if (exchange != null) {
                Optional<SymbolEntry> entry = service.reverseResolve(symbol, exchange);
                if (entry.isPresent()) {
                    this.base = entry.get().base();
                    this.quote = entry.get().quote();
                    this.coingeckoId = entry.get().coingeckoId();
                    // Lookup coin name
                    this.coinName = lookupCoinName(this.base, this.coingeckoId);
                }
            }
        }
        syncCombosToState();
    }

    /**
     * Set just the symbol (backward compatibility).
     */
    public void setSelectedSymbol(String symbol) {
        if (symbol == null) return;
        this.resolvedSymbol = symbol;
        // Try to reverse resolve for base/quote
        Optional<SymbolEntry> entry = service.reverseResolve(symbol, exchange);
        if (entry.isPresent()) {
            this.base = entry.get().base();
            this.quote = entry.get().quote();
            this.coingeckoId = entry.get().coingeckoId();
            this.coinName = lookupCoinName(this.base, this.coingeckoId);
        }
        suppressEvents = true;
        try {
            selectCoinInCombo(base);
            selectQuoteInCombo(quote);
        } finally {
            suppressEvents = false;
        }
    }

    /**
     * Apply compact toolbar styling to internal combos and browse button.
     */
    public void setToolbarMode() {
        Font toolbarFont = new Font("SansSerif", Font.PLAIN, 11);
        int height = 32;
        applyToolbarStyle(exchangeCombo, toolbarFont, height);
        applyToolbarStyle(marketCombo, toolbarFont, height);
        applyToolbarStyle(coinCombo, toolbarFont, height);
        applyToolbarStyle(quoteCombo, toolbarFont, height);
        browseButton.setFont(toolbarFont);
        browseButton.setMargin(new Insets(6, 10, 6, 10));
        browseButton.setFocusPainted(false);
        browseButton.setPreferredSize(new Dimension(browseButton.getPreferredSize().width, height));
        browseButton.setMinimumSize(new Dimension(browseButton.getMinimumSize().width, height));
        browseButton.setMaximumSize(new Dimension(browseButton.getMaximumSize().width, height));
    }

    private void applyToolbarStyle(JComboBox<?> combo, Font font, int height) {
        combo.setFont(font);
        Dimension pref = combo.getPreferredSize();
        combo.setPreferredSize(new Dimension(pref.width, height));
        combo.setMinimumSize(new Dimension(combo.getMinimumSize().width, height));
        combo.setMaximumSize(new Dimension(combo.getMaximumSize().width, height));
    }

    public void addActionListener(ActionListener l) {
        changeListeners.add(() -> l.actionPerformed(
            new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "symbolChanged")));
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        exchangeCombo.setEnabled(enabled);
        marketCombo.setEnabled(enabled);
        coinCombo.setEnabled(enabled);
        quoteCombo.setEnabled(enabled);
        browseButton.setEnabled(enabled);
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }

    // --- Population methods ---

    private void populateExchanges() {
        suppressEvents = true;
        try {
            List<String> exchanges = service.getExchanges();
            exchangeCombo.removeAllItems();
            for (String ex : exchanges) exchangeCombo.addItem(formatExchange(ex));
            String displayName = formatExchange(exchange);
            int idx = indexOfItem(exchangeCombo, displayName);
            if (idx >= 0) {
                exchangeCombo.setSelectedIndex(idx);
            } else if (!exchanges.isEmpty()) {
                exchangeCombo.setSelectedIndex(0);
                exchange = exchanges.get(0);
            }
            populateMarkets();
        } finally {
            suppressEvents = false;
        }
    }

    private void populateMarkets() {
        suppressEvents = true;
        try {
            List<String> markets = service.getMarketTypes(exchange);
            marketCombo.removeAllItems();
            for (String m : markets) marketCombo.addItem(formatMarket(m));
            String displayName = formatMarket(symbolMarket);
            int idx = indexOfItem(marketCombo, displayName);
            if (idx >= 0) {
                marketCombo.setSelectedIndex(idx);
            } else if (!markets.isEmpty()) {
                marketCombo.setSelectedIndex(0);
                symbolMarket = markets.get(0);
            }
            populateCoins();
        } finally {
            suppressEvents = false;
        }
    }

    private void populateCoins() {
        suppressEvents = true;
        try {
            List<CoinInfo> coins = service.getCoins(exchange, symbolMarket, 500);
            coinCombo.removeAllItems();
            CoinItem matchItem = null;
            for (CoinInfo ci : coins) {
                CoinItem item = new CoinItem(ci.base(), ci.coinName(), ci.coingeckoId());
                coinCombo.addItem(item);
                if (ci.base().equals(base)) {
                    matchItem = item;
                }
            }

            if (matchItem != null) {
                coinCombo.setSelectedItem(matchItem);
                coingeckoId = matchItem.coingeckoId;
                coinName = matchItem.coinName;
            } else if (base != null && !base.isEmpty()) {
                // Base not in top 500 but combo is editable — set as typed value
                coinCombo.setSelectedItem(base);
            } else if (coinCombo.getItemCount() > 0) {
                coinCombo.setSelectedIndex(0);
                CoinItem first = coinCombo.getItemAt(0);
                base = first.base;
                coingeckoId = first.coingeckoId;
                coinName = first.coinName;
            }
            populateQuotes();
        } finally {
            suppressEvents = false;
        }
    }

    private void populateQuotes() {
        suppressEvents = true;
        try {
            List<String> quotes = service.getQuoteCurrencies(exchange, symbolMarket, base);
            quoteCombo.removeAllItems();
            for (String q : quotes) quoteCombo.addItem(q);
            if (quotes.contains(quote)) {
                quoteCombo.setSelectedItem(quote);
            } else if (!quotes.isEmpty()) {
                quoteCombo.setSelectedIndex(0);
                quote = quotes.get(0);
            }
            resolveSymbol();
        } finally {
            suppressEvents = false;
        }
    }

    private void resolveSymbol() {
        Optional<String> resolved = service.resolveToSymbol(exchange, symbolMarket, base, quote);
        if (resolved.isPresent()) {
            resolvedSymbol = resolved.get();
        } else {
            // Construct a reasonable fallback
            resolvedSymbol = base + quote;
        }
    }

    private void syncCombosToState() {
        suppressEvents = true;
        try {
            if (exchangeCombo.getItemCount() == 0) {
                populateExchanges();
                return;
            }
            exchangeCombo.setSelectedItem(formatExchange(exchange));
            populateMarkets();
        } finally {
            suppressEvents = false;
        }
    }

    // --- Change handlers ---

    private void onExchangeChanged() {
        if (suppressEvents) return;
        String sel = exchangeConfigKey((String) exchangeCombo.getSelectedItem());
        if (sel == null || sel.equals(exchange)) return;

        String oldExchange = exchange;
        exchange = sel;

        suppressEvents = true;
        try {
            // Populate markets for new exchange
            List<String> markets = service.getMarketTypes(exchange);
            marketCombo.removeAllItems();
            for (String m : markets) marketCombo.addItem(formatMarket(m));

            // Try to keep same market type
            String displayMarket = formatMarket(symbolMarket);
            int idx = indexOfItem(marketCombo, displayMarket);
            if (idx >= 0) {
                marketCombo.setSelectedIndex(idx);
            } else if (!markets.isEmpty()) {
                marketCombo.setSelectedIndex(0);
                symbolMarket = markets.get(0);
            }

            // Resolve the same coin on the new exchange
            resolveCoinOnCurrentExchange();
        } finally {
            suppressEvents = false;
        }
        fireChange();
    }

    private void onMarketChanged() {
        if (suppressEvents) return;
        String sel = marketConfigKey((String) marketCombo.getSelectedItem());
        if (sel == null || sel.equals(symbolMarket)) return;
        symbolMarket = sel;

        suppressEvents = true;
        try {
            resolveCoinOnCurrentExchange();
        } finally {
            suppressEvents = false;
        }
        fireChange();
    }

    private void onCoinChanged() {
        if (suppressEvents) return;
        Object sel = coinCombo.getSelectedItem();
        if (sel == null) return;

        String newBase;
        if (sel instanceof CoinItem ci) {
            newBase = ci.base;
            coingeckoId = ci.coingeckoId;
            coinName = ci.coinName;
        } else {
            // User typed a raw base symbol
            newBase = sel.toString().trim().toUpperCase();
            // Try to find CoinInfo for this base
            coingeckoId = null;
            coinName = null;
            for (int i = 0; i < coinCombo.getItemCount(); i++) {
                CoinItem item = coinCombo.getItemAt(i);
                if (item.base.equalsIgnoreCase(newBase)) {
                    coingeckoId = item.coingeckoId;
                    coinName = item.coinName;
                    break;
                }
            }
        }

        if (newBase.isEmpty() || newBase.equals(base)) return;
        base = newBase;

        suppressEvents = true;
        try {
            populateQuotes();
        } finally {
            suppressEvents = false;
        }
        fireChange();
    }

    private void onQuoteChanged() {
        if (suppressEvents) return;
        String sel = (String) quoteCombo.getSelectedItem();
        if (sel == null || sel.equals(quote)) return;
        quote = sel;

        resolveSymbol();
        fireChange();
    }

    /**
     * Try to resolve the current coin (base/coingeckoId) on the current exchange/market.
     * Uses coingecko resolution first, then base symbol, then fallback quotes.
     */
    private void resolveCoinOnCurrentExchange() {
        // Step 1: Try exact resolution via coingeckoId
        if (coingeckoId != null && !coingeckoId.isEmpty()) {
            Optional<SymbolEntry> entry = service.resolve(coingeckoId, exchange, symbolMarket, quote);
            if (entry.isPresent()) {
                applyResolvedEntry(entry.get());
                return;
            }
            // Try fallback quotes with coingeckoId
            for (String fallbackQuote : FALLBACK_QUOTES) {
                if (fallbackQuote.equals(quote)) continue;
                entry = service.resolve(coingeckoId, exchange, symbolMarket, fallbackQuote);
                if (entry.isPresent()) {
                    applyResolvedEntry(entry.get());
                    return;
                }
            }
        }

        // Step 2: Try base symbol match with current quote
        Optional<String> sym = service.resolveToSymbol(exchange, symbolMarket, base, quote);
        if (sym.isPresent()) {
            resolvedSymbol = sym.get();
            populateCoins();
            return;
        }

        // Step 3: Try fallback quotes with base symbol
        for (String fallbackQuote : FALLBACK_QUOTES) {
            if (fallbackQuote.equals(quote)) continue;
            sym = service.resolveToSymbol(exchange, symbolMarket, base, fallbackQuote);
            if (sym.isPresent()) {
                quote = fallbackQuote;
                resolvedSymbol = sym.get();
                populateCoins();
                return;
            }
        }

        // Step 4: Try any quote for this base
        List<String> availableQuotes = service.getQuoteCurrencies(exchange, symbolMarket, base);
        if (!availableQuotes.isEmpty()) {
            quote = availableQuotes.get(0);
            populateCoins();
            return;
        }

        // Step 5: Last resort — select first available base alphabetically
        populateCoins();
    }

    private void applyResolvedEntry(SymbolEntry entry) {
        base = entry.base();
        quote = entry.quote();
        resolvedSymbol = entry.symbol();
        if (entry.coingeckoId() != null) coingeckoId = entry.coingeckoId();
        coinName = lookupCoinName(base, coingeckoId);
        populateCoins();
    }

    private String lookupCoinName(String base, String coingeckoId) {
        // Search current coin list for name
        List<CoinInfo> coins = service.getCoins(exchange, symbolMarket, 500);
        for (CoinInfo ci : coins) {
            if (ci.base().equals(base)) return ci.coinName();
        }
        return null;
    }

    private void fireChange() {
        changeListeners.forEach(Runnable::run);
    }

    private void openChooser() {
        if (!isEnabled()) return;
        SymbolEntry entry = SymbolChooserDialog.showDialog(this, "Choose Symbol", service);
        if (entry != null) {
            exchange = entry.exchange();
            symbolMarket = entry.marketType();
            base = entry.base();
            quote = entry.quote();
            coingeckoId = entry.coingeckoId();
            coinName = lookupCoinName(base, coingeckoId);
            resolvedSymbol = entry.symbol();
            syncCombosToState();
            fireChange();
        }
    }

    // --- Combo selection helpers ---

    private void selectCoinInCombo(String base) {
        for (int i = 0; i < coinCombo.getItemCount(); i++) {
            CoinItem item = coinCombo.getItemAt(i);
            if (item.base.equals(base)) {
                coinCombo.setSelectedIndex(i);
                return;
            }
        }
        coinCombo.setSelectedItem(base);
    }

    private void selectQuoteInCombo(String quote) {
        int idx = indexOfItem(quoteCombo, quote);
        if (idx >= 0) {
            quoteCombo.setSelectedIndex(idx);
        }
    }

    // --- Display name helpers ---

    private static String formatExchange(String configKey) {
        Exchange ex = Exchange.fromConfigKey(configKey);
        return ex != null ? ex.getDisplayName() : configKey;
    }

    private static String formatMarket(String configKey) {
        DataMarketType mt = DataMarketType.fromConfigKey(configKey);
        return mt != null ? mt.getDisplayName() : configKey;
    }

    private static String exchangeConfigKey(String displayName) {
        if (displayName == null) return null;
        for (Exchange ex : Exchange.values()) {
            if (ex.getDisplayName().equals(displayName)) return ex.getConfigKey();
        }
        return displayName;
    }

    private static String marketConfigKey(String displayName) {
        if (displayName == null) return null;
        for (DataMarketType mt : DataMarketType.values()) {
            if (mt.getDisplayName().equals(displayName)) return mt.getConfigKey();
        }
        return displayName;
    }

    private static int indexOfItem(JComboBox<String> combo, String item) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).equals(item)) return i;
        }
        return -1;
    }

    // --- CoinItem: wrapper for coin combo items ---

    /**
     * Represents a coin in the coin combo. Shows coinName (e.g., "Bitcoin") if available,
     * otherwise shows base symbol (e.g., "BTC").
     */
    static class CoinItem {
        final String base;
        final String coinName;
        final String coingeckoId;

        CoinItem(String base, String coinName, String coingeckoId) {
            this.base = base;
            this.coinName = coinName;
            this.coingeckoId = coingeckoId;
        }

        @Override
        public String toString() {
            // This is what the editable combo editor shows and what typed input is compared against
            return base;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof CoinItem ci) return base.equals(ci.base);
            if (o instanceof String s) return base.equalsIgnoreCase(s);
            return false;
        }

        @Override
        public int hashCode() {
            return base.hashCode();
        }
    }

    /**
     * Custom renderer that shows "Bitcoin (BTC)" style in the dropdown,
     * but just "BTC" in the editor field.
     */
    private static class CoinItemRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            String display;
            if (value instanceof CoinItem ci) {
                if (ci.coinName != null && !ci.coinName.isEmpty()) {
                    display = ci.coinName + " (" + ci.base + ")";
                } else {
                    display = ci.base;
                }
            } else {
                display = value != null ? value.toString() : "";
            }
            return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus);
        }
    }
}
