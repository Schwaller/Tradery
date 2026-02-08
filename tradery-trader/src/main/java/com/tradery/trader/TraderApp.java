package com.tradery.trader;

import com.formdev.flatlaf.FlatDarkLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

public class TraderApp {

    private static final Logger log = LoggerFactory.getLogger(TraderApp.class);

    public static void main(String[] args) {
        log.info("Starting Tradery Trader...");

        // Set up FlatLaf dark theme
        FlatDarkLaf.setup();

        SwingUtilities.invokeLater(() -> {
            TraderFrame frame = new TraderFrame();
            frame.setVisible(true);
        });
    }
}
