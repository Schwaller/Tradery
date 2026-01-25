module com.tradery.terminal {
    // UI
    requires java.desktop;

    // Terminal emulation - merged at runtime via jlink forceMerge
    requires static jediterm.merged;
    requires static pty4j;

    // Logging
    requires org.slf4j;

    // Export terminal components
    exports com.tradery.terminal;
}
