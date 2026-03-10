package com.tradery.news.ui.challenges;

import javax.swing.*;
import java.awt.*;

/**
 * Theme-aware colors and utilities shared across challenge UI components.
 */
public final class ChallengeTheme {

    private ChallengeTheme() {}

    public static Color bgMain() { return UIManager.getColor("Panel.background"); }

    public static Color bgCard() { return UIManager.getColor("Panel.background"); }

    public static Color textPrimary() { return UIManager.getColor("Label.foreground"); }

    public static Color textSecondary() { return UIManager.getColor("Label.disabledForeground"); }

    public static Color textMuted() {
        Color fg = UIManager.getColor("Label.disabledForeground");
        return new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 180);
    }

    public static Color linkColor() {
        Color c = UIManager.getColor("Component.linkColor");
        return c != null ? c : new Color(88, 157, 246);
    }

    public static Color darker(Color c, float factor) {
        return new Color(
            Math.max(0, (int)(c.getRed() * (1 - factor))),
            Math.max(0, (int)(c.getGreen() * (1 - factor))),
            Math.max(0, (int)(c.getBlue() * (1 - factor)))
        );
    }

    public static Color brighter(Color c, float factor) {
        return new Color(
            Math.min(255, (int)(c.getRed() + (255 - c.getRed()) * factor)),
            Math.min(255, (int)(c.getGreen() + (255 - c.getGreen()) * factor)),
            Math.min(255, (int)(c.getBlue() + (255 - c.getBlue()) * factor))
        );
    }

    public static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
