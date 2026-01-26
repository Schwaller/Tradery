module com.tradery.charts {
    // Exports - all packages
    exports com.tradery.charts.core;
    exports com.tradery.charts.util;
    exports com.tradery.charts.overlay;
    exports com.tradery.charts.chart;
    exports com.tradery.charts.renderer;

    // Required modules
    requires transitive com.tradery.core;
    requires transitive org.jfree.jfreechart;
    requires java.desktop;
    requires org.slf4j;
}
