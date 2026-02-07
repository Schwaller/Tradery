package com.tradery.symbols.ui;

import com.tradery.symbols.model.SymbolEntry;
import com.tradery.symbols.service.SymbolService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Symbol picker: 3 cascading JComboBoxes (Exchange → Market → Pair) + browse button.
 */
public class SymbolComboBox extends JPanel {

    private final SymbolService service;
    private final JComboBox<String> exchangeCombo;
    private final JComboBox<String> marketCombo;
    private final JComboBox<String> pairCombo;
    private final JButton browseButton;
    private final List<Runnable> changeListeners = new ArrayList<>();

    private boolean suppressEvents = false;

    private String exchange = "binance";
    private String symbolMarket = "spot";
    private String selectedSymbol = "BTCUSDT";

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

        pairCombo = new JComboBox<>();
        pairCombo.setEditable(true);
        pairCombo.addActionListener(e -> onPairChanged());

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
            add(pairCombo);
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

            // Row 2: Pair + browse
            gbc.gridy = 2; gbc.gridwidth = 1;
            add(pairCombo, gbc);
            gbc.gridx = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
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
        return selectedSymbol;
    }

    /**
     * Set all three fields programmatically.
     */
    public void setSelection(String exchange, String symbolMarket, String symbol) {
        if (exchange != null) this.exchange = exchange;
        if (symbolMarket != null) this.symbolMarket = symbolMarket;
        if (symbol != null) this.selectedSymbol = symbol;
        syncCombosToState();
    }

    /**
     * Set just the symbol (backward compatibility).
     */
    public void setSelectedSymbol(String symbol) {
        if (symbol == null) return;
        this.selectedSymbol = symbol;
        suppressEvents = true;
        try {
            pairCombo.setSelectedItem(symbol);
        } finally {
            suppressEvents = false;
        }
    }

    /**
     * Apply compact toolbar styling to internal combos and browse button.
     * Matches ToolbarButton height (32px) and font (11pt SansSerif).
     */
    public void setToolbarMode() {
        Font toolbarFont = new Font("SansSerif", Font.PLAIN, 11);
        int height = 32;
        applyToolbarStyle(exchangeCombo, toolbarFont, height);
        applyToolbarStyle(marketCombo, toolbarFont, height);
        applyToolbarStyle(pairCombo, toolbarFont, height);
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
        pairCombo.setEnabled(enabled);
        browseButton.setEnabled(enabled);
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }

    private void populateExchanges() {
        suppressEvents = true;
        try {
            List<String> exchanges = service.getExchanges();
            exchangeCombo.removeAllItems();
            for (String ex : exchanges) exchangeCombo.addItem(ex);
            if (exchanges.contains(exchange)) {
                exchangeCombo.setSelectedItem(exchange);
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
            for (String m : markets) marketCombo.addItem(m);
            if (markets.contains(symbolMarket)) {
                marketCombo.setSelectedItem(symbolMarket);
            } else if (!markets.isEmpty()) {
                marketCombo.setSelectedIndex(0);
                symbolMarket = markets.get(0);
            }
            populatePairs();
        } finally {
            suppressEvents = false;
        }
    }

    private void populatePairs() {
        suppressEvents = true;
        try {
            List<String> symbols = service.getSymbols(exchange, symbolMarket, 500);
            pairCombo.removeAllItems();
            for (String s : symbols) pairCombo.addItem(s);
            if (symbols.contains(selectedSymbol)) {
                pairCombo.setSelectedItem(selectedSymbol);
            } else if (!symbols.isEmpty()) {
                pairCombo.setSelectedIndex(0);
                selectedSymbol = symbols.get(0);
            }
        } finally {
            suppressEvents = false;
        }
    }

    private void syncCombosToState() {
        suppressEvents = true;
        try {
            // Ensure exchange list is loaded
            if (exchangeCombo.getItemCount() == 0) {
                populateExchanges();
                return;
            }
            exchangeCombo.setSelectedItem(exchange);
            populateMarkets();
        } finally {
            suppressEvents = false;
        }
    }

    private void onExchangeChanged() {
        if (suppressEvents) return;
        String sel = (String) exchangeCombo.getSelectedItem();
        if (sel == null || sel.equals(exchange)) return;
        exchange = sel;
        suppressEvents = true;
        try {
            populateMarkets();
        } finally {
            suppressEvents = false;
        }
        fireChange();
    }

    private void onMarketChanged() {
        if (suppressEvents) return;
        String sel = (String) marketCombo.getSelectedItem();
        if (sel == null || sel.equals(symbolMarket)) return;
        symbolMarket = sel;
        suppressEvents = true;
        try {
            populatePairs();
        } finally {
            suppressEvents = false;
        }
        fireChange();
    }

    private void onPairChanged() {
        if (suppressEvents) return;
        Object sel = pairCombo.getSelectedItem();
        if (sel == null) return;
        String symbol = sel.toString().trim().toUpperCase();
        if (symbol.isEmpty() || symbol.equals(selectedSymbol)) return;
        selectedSymbol = symbol;
        fireChange();
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
            selectedSymbol = entry.symbol();
            syncCombosToState();
            fireChange();
        }
    }
}
