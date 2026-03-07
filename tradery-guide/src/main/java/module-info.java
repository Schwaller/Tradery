module com.tradery.guide {
    requires java.desktop;
    requires com.tradery.help;
    requires com.tradery.ui.common;
    requires com.formdev.flatlaf;
    requires com.formdev.flatlaf.intellijthemes;

    exports com.tradery.guide;

    // Allow tradery-help to load markdown and SVG resources from this module
    opens guide to com.tradery.help;
    opens guide.images to com.tradery.help;
}
