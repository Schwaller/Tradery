module com.tradery.ui.common {
    requires java.desktop;
    requires jdk.httpserver;
    requires com.formdev.flatlaf;
    requires com.formdev.flatlaf.intellijthemes;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;

    exports com.tradery.ui;
    exports com.tradery.ui.controls;
    exports com.tradery.ui.controls.indicators;
    exports com.tradery.ui.status;
    exports com.tradery.ui.dashboard;
    exports com.tradery.ui.coverage;
    exports com.tradery.ui.settings;

    opens com.tradery.ui.controls to com.fasterxml.jackson.databind;
    opens com.tradery.ui.controls.indicators to com.fasterxml.jackson.databind;
}
