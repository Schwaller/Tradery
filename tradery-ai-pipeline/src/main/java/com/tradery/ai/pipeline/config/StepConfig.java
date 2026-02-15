package com.tradery.ai.pipeline.config;

import java.util.Map;

/**
 * Configuration for a single pipeline step.
 */
public class StepConfig {
    private String type;
    private String tier;
    private Map<String, Object> params;

    public StepConfig() {}

    public StepConfig(String type) {
        this.type = type;
    }

    public StepConfig(String type, String tier) {
        this.type = type;
        this.tier = tier;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }

    public int getIntParam(String key, int defaultValue) {
        if (params == null) return defaultValue;
        Object v = params.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    public double getDoubleParam(String key, double defaultValue) {
        if (params == null) return defaultValue;
        Object v = params.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }
}
