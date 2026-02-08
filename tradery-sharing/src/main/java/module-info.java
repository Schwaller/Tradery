module com.tradery.sharing {
    requires com.tradery.news;
    requires com.tradery.documents;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;
    requires okhttp3;
    requires org.slf4j;

    exports com.tradery.sharing.sync;
    exports com.tradery.sharing.identity;
    exports com.tradery.sharing.upgrade;
    exports com.tradery.sharing.discovery;
    exports com.tradery.sharing.governance;

    opens com.tradery.sharing.sync to com.fasterxml.jackson.databind;
}
