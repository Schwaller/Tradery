module com.tradery.help {
    requires java.desktop;
    requires com.formdev.flatlaf;
    requires com.tradery.ui.common;

    // Markdown - flexmark
    requires com.vladsch.flexmark;
    requires com.vladsch.flexmark.util;
    requires com.vladsch.flexmark.util.ast;
    requires com.vladsch.flexmark.util.data;
    requires com.vladsch.flexmark.util.misc;
    requires com.vladsch.flexmark.util.sequence;
    requires com.vladsch.flexmark.util.html;
    requires com.vladsch.flexmark.ext.tables;

    exports com.tradery.help;
}
