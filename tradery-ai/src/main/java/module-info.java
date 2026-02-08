module com.tradery.ai {
    requires java.desktop;
    requires com.formdev.flatlaf;
    requires okhttp3;
    requires transitive com.fasterxml.jackson.databind;
    requires transitive com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires static org.jsoup;
    requires org.slf4j;

    exports com.tradery.ai;
    opens com.tradery.ai to com.fasterxml.jackson.databind;
}
