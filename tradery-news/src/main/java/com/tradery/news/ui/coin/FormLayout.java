package com.tradery.news.ui.coin;

import java.util.ArrayList;
import java.util.List;

/**
 * A named form layout for an entity type. Controls which attributes appear
 * and how they are grouped into rows.
 */
public class FormLayout {

    private String name;
    private List<FormLayoutField> fields;

    public FormLayout() {
        this.fields = new ArrayList<>();
    }

    public FormLayout(String name, List<FormLayoutField> fields) {
        this.name = name;
        this.fields = fields != null ? fields : new ArrayList<>();
    }

    public String name() { return name; }
    public void setName(String name) { this.name = name; }

    public List<FormLayoutField> fields() { return fields; }
    public void setFields(List<FormLayoutField> fields) { this.fields = fields != null ? fields : new ArrayList<>(); }

    /**
     * A single field reference within a form layout.
     */
    public static class FormLayoutField {
        private String attributeName;
        private String group; // null = own row; same string = share a row

        public FormLayoutField() {}

        public FormLayoutField(String attributeName, String group) {
            this.attributeName = attributeName;
            this.group = group;
        }

        public String attributeName() { return attributeName; }
        public void setAttributeName(String attributeName) { this.attributeName = attributeName; }

        public String group() { return group; }
        public void setGroup(String group) { this.group = group; }
    }
}
