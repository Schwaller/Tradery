module com.tradery.license {
    exports com.tradery.license;

    requires java.desktop;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires com.fasterxml.jackson.annotation;
    requires okhttp3;
    requires com.formdev.flatlaf;

    opens com.tradery.license to com.fasterxml.jackson.databind;
}
