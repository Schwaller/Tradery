module com.tradery.documents {
    requires com.tradery.news;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires com.fasterxml.jackson.annotation;

    exports com.tradery.documents;

    opens com.tradery.documents to com.fasterxml.jackson.databind;
}
