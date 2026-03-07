package com.tradery.guide;

import com.tradery.ui.ThemeHelper;

import javax.swing.*;

/**
 * Standalone entry point for the Trading Guide.
 */
public class TradingGuideApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ThemeHelper.applyCurrentTheme();
            TradingGuideDialog.show(null);
        });
    }
}
