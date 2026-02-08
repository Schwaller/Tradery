module com.tradery.agent {
    // UI
    requires java.desktop;
    requires com.formdev.flatlaf;

    // AI profiles & detection
    requires transitive com.tradery.ai;

    // Terminal emulation - merged at runtime via jlink forceMerge
    requires static jediterm.merged;
    requires static pty4j;

    // Logging
    requires org.slf4j;

    // Export terminal components
    exports com.tradery.agent.terminal;
}
