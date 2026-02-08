package com.tradery.news.ui.coin;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * An attribute definition within a schema type (entity or relationship type).
 * Supports rich formatting (currency, dates, units) and multi-language labels.
 */
public class SchemaAttribute {

    // Existing types
    public static final String TEXT = "TEXT";
    public static final String NUMBER = "NUMBER";
    public static final String LIST = "LIST";
    public static final String BOOLEAN = "BOOLEAN";

    // New types
    public static final String DATE = "DATE";
    public static final String TIME = "TIME";
    public static final String DATETIME = "DATETIME";
    public static final String DATETIME_TZ = "DATETIME_TZ";
    public static final String CURRENCY = "CURRENCY";
    public static final String URL = "URL";
    public static final String ENUM = "ENUM";
    public static final String PERCENTAGE = "PERCENTAGE";

    /** All types for UI dropdowns, ordered by frequency of use. */
    public static final List<String> ALL_TYPES = List.of(
        TEXT, NUMBER, CURRENCY, PERCENTAGE, BOOLEAN,
        DATE, TIME, DATETIME, DATETIME_TZ, URL, ENUM, LIST);

    public enum Mutability { SOURCE, DERIVED, MANUAL }

    private final String name;
    private final String dataType;
    private final boolean required;
    private final int displayOrder;
    private final Map<String, String> labels;    // locale tag -> display name
    private final Map<String, Object> config;    // type-specific formatting config
    private final Mutability mutability;

    /** Backwards-compatible constructor (labels=null, config=null, mutability=MANUAL). */
    public SchemaAttribute(String name, String dataType, boolean required, int displayOrder) {
        this(name, dataType, required, displayOrder, null, null, Mutability.MANUAL);
    }

    /** Constructor with labels and config (mutability=MANUAL). */
    public SchemaAttribute(String name, String dataType, boolean required, int displayOrder,
                           Map<String, String> labels, Map<String, Object> config) {
        this(name, dataType, required, displayOrder, labels, config, Mutability.MANUAL);
    }

    /** Full constructor with labels, config, and mutability. */
    public SchemaAttribute(String name, String dataType, boolean required, int displayOrder,
                           Map<String, String> labels, Map<String, Object> config, Mutability mutability) {
        this.name = name;
        this.dataType = dataType;
        this.required = required;
        this.displayOrder = displayOrder;
        this.labels = labels != null ? new LinkedHashMap<>(labels) : null;
        this.config = config != null ? new LinkedHashMap<>(config) : null;
        this.mutability = mutability;
    }

    public String name() { return name; }
    public String dataType() { return dataType; }
    public boolean required() { return required; }
    public int displayOrder() { return displayOrder; }
    public Map<String, String> labels() { return labels; }
    public Map<String, Object> config() { return config; }
    public Mutability mutability() { return mutability; }
    public boolean isSource() { return mutability == Mutability.SOURCE; }
    public boolean isDerived() { return mutability == Mutability.DERIVED; }

    /**
     * Locale-aware display name with fallback chain:
     * exact tag (de-CH) -> language (de) -> english (en) -> programmatic name.
     */
    public String displayName(Locale locale) {
        if (labels == null || labels.isEmpty()) return name;

        // Exact tag match (e.g. "de-CH")
        String tag = locale.toLanguageTag();
        String result = labels.get(tag);
        if (result != null) return result;

        // Language-only match (e.g. "de")
        String lang = locale.getLanguage();
        result = labels.get(lang);
        if (result != null) return result;

        // English fallback
        result = labels.get("en");
        if (result != null) return result;

        return name;
    }

    /** Get a config value, or null if config is absent or key missing. */
    @SuppressWarnings("unchecked")
    public <T> T configValue(String key) {
        if (config == null) return null;
        return (T) config.get(key);
    }

    /** Get a config value with default. */
    @SuppressWarnings("unchecked")
    public <T> T configValue(String key, T defaultValue) {
        if (config == null) return defaultValue;
        Object v = config.get(key);
        return v != null ? (T) v : defaultValue;
    }

    /**
     * Format a raw value string for display using this attribute's config.
     * Returns the raw value if formatting fails or no config applies.
     */
    public String formatValue(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) return "";

        try {
            return switch (dataType) {
                case NUMBER -> formatNumber(rawValue);
                case CURRENCY -> formatCurrency(rawValue);
                case PERCENTAGE -> formatPercentage(rawValue);
                case DATE -> formatDate(rawValue);
                case TIME -> formatTime(rawValue);
                case DATETIME -> formatDateTime(rawValue);
                case DATETIME_TZ -> formatDateTimeTz(rawValue);
                case BOOLEAN -> "true".equalsIgnoreCase(rawValue) ? "Yes" : "No";
                case URL -> rawValue;
                case ENUM -> rawValue;
                case LIST -> rawValue;
                default -> rawValue;
            };
        } catch (Exception e) {
            return rawValue;
        }
    }

    private String formatNumber(String rawValue) {
        double val = Double.parseDouble(rawValue);
        int decimals = configValue("decimalPlaces", 2);
        boolean thousands = configValue("thousandsSeparator", false);
        String unit = configValue("unit");

        DecimalFormat df = (DecimalFormat) NumberFormat.getInstance(Locale.US);
        df.setMinimumFractionDigits(decimals);
        df.setMaximumFractionDigits(decimals);
        df.setGroupingUsed(thousands);

        String formatted = df.format(val);
        if (unit != null && !unit.isEmpty()) {
            formatted += " " + unit;
        }
        return formatted;
    }

    private String formatCurrency(String rawValue) {
        double val = Double.parseDouble(rawValue);
        int decimals = configValue("decimalPlaces", 2);
        String symbol = configValue("currencySymbol", "$");
        String position = configValue("symbolPosition", "prefix");

        DecimalFormat df = (DecimalFormat) NumberFormat.getInstance(Locale.US);
        df.setMinimumFractionDigits(decimals);
        df.setMaximumFractionDigits(decimals);
        df.setGroupingUsed(true);

        String formatted = df.format(val);
        return "suffix".equals(position) ? formatted + symbol : symbol + formatted;
    }

    private String formatPercentage(String rawValue) {
        double val = Double.parseDouble(rawValue) * 100;
        int decimals = configValue("decimalPlaces", 1);

        DecimalFormat df = (DecimalFormat) NumberFormat.getInstance(Locale.US);
        df.setMinimumFractionDigits(decimals);
        df.setMaximumFractionDigits(decimals);

        return df.format(val) + "%";
    }

    private String formatDate(String rawValue) {
        String format = configValue("format", "yyyy-MM-dd");
        LocalDate date = LocalDate.parse(rawValue);
        return date.format(DateTimeFormatter.ofPattern(format));
    }

    private String formatTime(String rawValue) {
        String format = configValue("format", "HH:mm");
        LocalTime time = LocalTime.parse(rawValue);
        return time.format(DateTimeFormatter.ofPattern(format));
    }

    private String formatDateTime(String rawValue) {
        String format = configValue("format", "yyyy-MM-dd HH:mm:ss");
        long epochMs = Long.parseLong(rawValue);
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
        return dt.format(DateTimeFormatter.ofPattern(format));
    }

    private String formatDateTimeTz(String rawValue) {
        String format = configValue("format", "yyyy-MM-dd HH:mm:ss z");
        ZonedDateTime zdt = ZonedDateTime.parse(rawValue);
        // Display in user's local timezone
        ZonedDateTime local = zdt.withZoneSameInstant(ZoneId.systemDefault());
        return local.format(DateTimeFormatter.ofPattern(format));
    }

    /** Short summary of config for display in tables. */
    public String configSummary() {
        if (config == null || config.isEmpty()) return "";
        return switch (dataType) {
            case NUMBER -> {
                String unit = configValue("unit");
                yield unit != null ? unit : "";
            }
            case CURRENCY -> {
                String code = configValue("currencyCode", "");
                String symbol = configValue("currencySymbol", "");
                yield symbol.isEmpty() ? code : symbol + " " + code;
            }
            case PERCENTAGE -> configValue("decimalPlaces", 1) + " dp";
            case DATE, TIME, DATETIME -> {
                String fmt = configValue("format");
                yield fmt != null ? fmt : "";
            }
            case DATETIME_TZ -> {
                String tz = configValue("timezone", "");
                yield tz;
            }
            case ENUM -> {
                List<?> values = configValue("values");
                yield values != null ? values.size() + " values" : "";
            }
            default -> "";
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SchemaAttribute that)) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name + ": " + dataType;
    }
}
