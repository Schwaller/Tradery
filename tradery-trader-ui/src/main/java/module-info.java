module com.tradery.trader.ui {
    // Exports
    exports com.tradery.trader.ui;

    // Dependencies
    requires transitive com.tradery.core;
    requires transitive com.tradery.exchange;
    requires transitive com.tradery.execution;
    requires com.tradery.ui.common;
    requires java.desktop;
    requires com.formdev.flatlaf;
    requires org.slf4j;
}
