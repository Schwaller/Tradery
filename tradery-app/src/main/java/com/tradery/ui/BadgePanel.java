package com.tradery.ui;

import javax.swing.*;
import java.awt.*;

/**
 * A styled panel that provides a subtle "card" or "badge" appearance
 * that adapts to the current theme (dark or light).
 *
 * - Dark themes: slight white tint background with lighter border
 * - Light themes: slight black tint background with darker border
 */
public class BadgePanel extends JPanel {

    private static final float BG_TINT_DARK = 0.06f;   // 6% white tint for dark themes
    private static final float BG_TINT_LIGHT = 0.04f;  // 4% black tint for light themes
    private static final float BORDER_TINT_DARK = 0.12f;  // 12% white for dark theme borders
    private static final float BORDER_TINT_LIGHT = 0.10f; // 10% black for light theme borders

    private boolean styleApplied = false;
    private java.beans.PropertyChangeListener lafListener;

    public BadgePanel() {
        this(new BorderLayout(0, 0));
    }

    public BadgePanel(LayoutManager layout) {
        super(layout);
        setOpaque(true);
        // Don't apply style here - wait until component is added to hierarchy
    }

    @Override
    public void addNotify() {
        super.addNotify();
        // Apply style when component is added to the UI hierarchy
        if (!styleApplied) {
            applyBadgeStyle();
            styleApplied = true;
        }
        // Listen for LAF changes to update badge style
        if (lafListener == null) {
            lafListener = evt -> {
                if ("lookAndFeel".equals(evt.getPropertyName())) {
                    SwingUtilities.invokeLater(this::applyBadgeStyle);
                }
            };
            UIManager.addPropertyChangeListener(lafListener);
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (lafListener != null) {
            UIManager.removePropertyChangeListener(lafListener);
            lafListener = null;
        }
    }

    /**
     * Apply the badge styling based on current theme.
     * Call this method after theme changes to update appearance.
     */
    public void applyBadgeStyle() {
        // Use UIManager to get the current theme's panel background
        Color baseColor = UIManager.getColor("Panel.background");
        if (baseColor == null) {
            baseColor = new Color(60, 63, 65); // Fallback dark gray
        }

        boolean isDark = isDarkTheme(baseColor);
        Color adjustedBg = isDark
                ? blendWithWhite(baseColor, BG_TINT_DARK)
                : blendWithBlack(baseColor, BG_TINT_LIGHT);
        Color borderColor = isDark
                ? blendWithWhite(baseColor, BORDER_TINT_DARK)
                : blendWithBlack(baseColor, BORDER_TINT_LIGHT);

        setBackground(adjustedBg);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1, true),
                BorderFactory.createEmptyBorder(8, 10, 10, 10)
        ));
    }

    /**
     * Create a badge panel with custom padding.
     */
    public static BadgePanel withPadding(int top, int left, int bottom, int right) {
        BadgePanel panel = new BadgePanel();
        Color baseColor = UIManager.getColor("Panel.background");
        if (baseColor == null) baseColor = panel.getBackground();

        boolean isDark = isDarkTheme(baseColor);
        Color borderColor = isDark
                ? blendWithWhite(baseColor, BORDER_TINT_DARK)
                : blendWithBlack(baseColor, BORDER_TINT_LIGHT);

        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1, true),
                BorderFactory.createEmptyBorder(top, left, bottom, right)
        ));
        return panel;
    }

    // ===== Static utility methods for use elsewhere =====

    /**
     * Check if the current theme is dark based on panel background.
     */
    public static boolean isDarkTheme() {
        Color bg = UIManager.getColor("Panel.background");
        return isDarkTheme(bg);
    }

    private static boolean isDarkTheme(Color bg) {
        if (bg == null) return true;
        // Calculate perceived brightness using standard formula
        double brightness = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255.0;
        return brightness < 0.5;
    }

    /**
     * Blend a color with white by the given factor (0.0 - 1.0).
     */
    public static Color blendWithWhite(Color c, float factor) {
        int r = (int) (c.getRed() + (255 - c.getRed()) * factor);
        int g = (int) (c.getGreen() + (255 - c.getGreen()) * factor);
        int b = (int) (c.getBlue() + (255 - c.getBlue()) * factor);
        return new Color(r, g, b);
    }

    /**
     * Blend a color with black by the given factor (0.0 - 1.0).
     */
    public static Color blendWithBlack(Color c, float factor) {
        int r = (int) (c.getRed() * (1 - factor));
        int g = (int) (c.getGreen() * (1 - factor));
        int b = (int) (c.getBlue() * (1 - factor));
        return new Color(r, g, b);
    }

    /**
     * Get the standard badge background color for the current theme.
     */
    public static Color getBadgeBackground() {
        Color baseColor = UIManager.getColor("Panel.background");
        if (baseColor == null) baseColor = Color.GRAY;
        return isDarkTheme(baseColor)
                ? blendWithWhite(baseColor, BG_TINT_DARK)
                : blendWithBlack(baseColor, BG_TINT_LIGHT);
    }

    /**
     * Get the standard badge border color for the current theme.
     */
    public static Color getBadgeBorderColor() {
        Color baseColor = UIManager.getColor("Panel.background");
        if (baseColor == null) baseColor = Color.GRAY;
        return isDarkTheme(baseColor)
                ? blendWithWhite(baseColor, BORDER_TINT_DARK)
                : blendWithBlack(baseColor, BORDER_TINT_LIGHT);
    }
}
