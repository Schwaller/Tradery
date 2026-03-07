module com.tradery.charts {
    // Exports - all packages
    exports com.tradery.charts.core;
    exports com.tradery.charts.util;
    exports com.tradery.charts.overlay;
    exports com.tradery.charts.overlay.footprint;
    exports com.tradery.charts.chart;
    exports com.tradery.charts.renderer;
    exports com.tradery.charts.indicator;
    exports com.tradery.charts.indicator.impl;

    // Required modules
    requires transitive com.tradery.core;
    requires transitive com.tradery.data;
    requires transitive org.jfree.jfreechart;
    requires com.tradery.ui.common;
    requires java.desktop;
    requires org.slf4j;
}
