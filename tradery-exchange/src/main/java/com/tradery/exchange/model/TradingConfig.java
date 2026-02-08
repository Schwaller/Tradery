package com.tradery.exchange.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TradingConfig {

    private Map<String, VenueConfig> venues = Map.of();
    private String activeVenue = "hyperliquid";
    private RiskConfig risk = new RiskConfig();
    private PaperConfig paperTrading = new PaperConfig();

    public Map<String, VenueConfig> getVenues() { return venues; }
    public void setVenues(Map<String, VenueConfig> venues) { this.venues = venues; }

    public String getActiveVenue() { return activeVenue; }
    public void setActiveVenue(String activeVenue) { this.activeVenue = activeVenue; }

    public RiskConfig getRisk() { return risk; }
    public void setRisk(RiskConfig risk) { this.risk = risk; }

    public PaperConfig getPaperTrading() { return paperTrading; }
    public void setPaperTrading(PaperConfig paperTrading) { this.paperTrading = paperTrading; }

    public VenueConfig getActiveVenueConfig() {
        return venues.get(activeVenue);
    }

    public static TradingConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new TradingConfig();
        }
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(path.toFile(), TradingConfig.class);
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.writeValue(path.toFile(), this);
    }

    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), ".tradery", "trading.yaml");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VenueConfig {
        private boolean enabled;
        private boolean testnet;
        private String address;
        private String privateKey;
        private String apiKey;
        private String apiSecret;
        private String keypairPath;
        private int defaultLeverage = 5;
        private MarginMode defaultMarginMode = MarginMode.CROSS;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isTestnet() { return testnet; }
        public void setTestnet(boolean testnet) { this.testnet = testnet; }

        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }

        public String getPrivateKey() { return privateKey; }
        public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getApiSecret() { return apiSecret; }
        public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }

        public String getKeypairPath() { return keypairPath; }
        public void setKeypairPath(String keypairPath) { this.keypairPath = keypairPath; }

        public int getDefaultLeverage() { return defaultLeverage; }
        public void setDefaultLeverage(int defaultLeverage) { this.defaultLeverage = defaultLeverage; }

        public MarginMode getDefaultMarginMode() { return defaultMarginMode; }
        public void setDefaultMarginMode(MarginMode defaultMarginMode) { this.defaultMarginMode = defaultMarginMode; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RiskConfig {
        private double maxPositionSizeUsd = 10000;
        private int maxOpenPositions = 5;
        private double maxDailyLossPercent = 5.0;
        private double maxDrawdownPercent = 15.0;
        private int maxOrdersPerMinute = 10;
        private List<String> allowedSymbols = List.of();

        public double getMaxPositionSizeUsd() { return maxPositionSizeUsd; }
        public void setMaxPositionSizeUsd(double v) { this.maxPositionSizeUsd = v; }

        public int getMaxOpenPositions() { return maxOpenPositions; }
        public void setMaxOpenPositions(int v) { this.maxOpenPositions = v; }

        public double getMaxDailyLossPercent() { return maxDailyLossPercent; }
        public void setMaxDailyLossPercent(double v) { this.maxDailyLossPercent = v; }

        public double getMaxDrawdownPercent() { return maxDrawdownPercent; }
        public void setMaxDrawdownPercent(double v) { this.maxDrawdownPercent = v; }

        public int getMaxOrdersPerMinute() { return maxOrdersPerMinute; }
        public void setMaxOrdersPerMinute(int v) { this.maxOrdersPerMinute = v; }

        public List<String> getAllowedSymbols() { return allowedSymbols; }
        public void setAllowedSymbols(List<String> v) { this.allowedSymbols = v; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaperConfig {
        private boolean enabled;
        private double initialBalance = 10000.0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public double getInitialBalance() { return initialBalance; }
        public void setInitialBalance(double initialBalance) { this.initialBalance = initialBalance; }
    }
}
