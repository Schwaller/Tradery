package com.tradery.news.ui.coin;

/**
 * An attribute definition within a schema type (entity or relationship type).
 */
public record SchemaAttribute(String name, String dataType, boolean required, int displayOrder) {

    public static final String TEXT = "TEXT";
    public static final String NUMBER = "NUMBER";
    public static final String LIST = "LIST";
    public static final String BOOLEAN = "BOOLEAN";
}
