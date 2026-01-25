package com.tradery.forge.ui.help;

import javax.swing.*;
import java.awt.*;

/**
 * Shared styling utilities for help dialogs.
 * Provides themed CSS generation using UIManager colors.
 */
public class HelpStyles {

    /**
     * Theme colors extracted from UIManager.
     */
    public static class ThemeColors {
        public final Color bg;
        public final Color fg;
        public final Color fgSecondary;
        public final Color accent;
        public final Color codeBg;
        public final Color tableBorder;

        public final String bgHex;
        public final String fgHex;
        public final String fgSecHex;
        public final String accentHex;
        public final String codeBgHex;
        public final String borderHex;

        private ThemeColors(Color bg, Color fg, Color fgSecondary, Color accent, Color codeBg, Color tableBorder) {
            this.bg = bg;
            this.fg = fg;
            this.fgSecondary = fgSecondary;
            this.accent = accent;
            this.codeBg = codeBg;
            this.tableBorder = tableBorder;

            this.bgHex = toHex(bg);
            this.fgHex = toHex(fg);
            this.fgSecHex = toHex(fgSecondary);
            this.accentHex = toHex(accent);
            this.codeBgHex = toHex(codeBg);
            this.borderHex = toHex(tableBorder);
        }
    }

    /**
     * Get current theme colors from UIManager with fallbacks.
     */
    public static ThemeColors getThemeColors() {
        Color bg = UIManager.getColor("Panel.background");
        Color fg = UIManager.getColor("Label.foreground");
        Color fgSecondary = UIManager.getColor("Label.disabledForeground");
        Color accent = UIManager.getColor("Component.accentColor");
        Color codeBg = UIManager.getColor("TextField.background");
        Color tableBorder = UIManager.getColor("Separator.foreground");

        if (bg == null) bg = Color.WHITE;
        if (fg == null) fg = Color.BLACK;
        if (fgSecondary == null) fgSecondary = Color.GRAY;
        if (accent == null) accent = new Color(70, 130, 180);
        if (codeBg == null) codeBg = new Color(240, 240, 240);
        if (tableBorder == null) tableBorder = new Color(200, 200, 200);

        return new ThemeColors(bg, fg, fgSecondary, accent, codeBg, tableBorder);
    }

    /**
     * Build CSS styles for help content using theme colors.
     */
    public static String buildCss(ThemeColors colors) {
        return String.format("""
            body {
                font-family: -apple-system, BlinkMacSystemFont, sans-serif;
                font-size: 11px;
                margin: 12px;
                background: %s;
                color: %s;
                line-height: 1.4;
            }
            h1 {
                color: %s;
                font-size: 15px;
                margin: 0 0 10px 0;
            }
            h2 {
                color: %s;
                font-size: 13px;
                margin: 18px 0 6px 0;
                border-bottom: 1px solid %s;
                padding-bottom: 3px;
            }
            h3 {
                color: %s;
                font-size: 11px;
                margin: 10px 0 4px 0;
                font-weight: 600;
            }
            h4 {
                color: %s;
                font-size: 11px;
                margin: 8px 0 3px 0;
                font-weight: normal;
                font-style: italic;
            }
            p {
                margin: 4px 0;
            }
            .box {
                background: %s;
                padding: 6px 10px;
                border-radius: 5px;
                margin: 6px 0;
            }
            .tip {
                background: %s;
                border-left: 3px solid %s;
                padding: 6px 10px;
                margin: 6px 0;
            }
            .example {
                background: %s;
                border-left: 3px solid %s;
                padding: 6px 10px;
                margin: 6px 0;
                font-family: monospace;
            }
            ul, ol {
                margin: 4px 0;
                padding-left: 18px;
            }
            li {
                margin: 2px 0;
            }
            table {
                border-collapse: collapse;
                width: 100%%;
                margin: 4px 0;
                font-size: 10px;
            }
            td, th {
                text-align: left;
                padding: 3px 6px;
                border-bottom: 1px solid %s;
            }
            th {
                background: %s;
                font-weight: 600;
            }
            code {
                background: %s;
                padding: 1px 4px;
                border-radius: 3px;
                font-family: monospace;
            }
            pre {
                background: %s;
                padding: 8px;
                border-radius: 5px;
                margin: 6px 0;
                overflow-x: auto;
                font-family: monospace;
                font-size: 10px;
            }
            pre code {
                background: transparent;
                padding: 0;
            }
            .flow {
                font-family: monospace;
                background: %s;
                padding: 8px;
                border-radius: 5px;
                margin: 6px 0;
                text-align: center;
                font-size: 10px;
            }
            .small {
                font-size: 10px;
                color: %s;
            }
            hr {
                border: none;
                border-top: 1px solid %s;
                margin: 12px 0;
            }
            """,
                colors.bgHex, colors.fgHex,                     // body
                colors.accentHex,                               // h1
                colors.accentHex, colors.fgSecHex,              // h2
                colors.fgSecHex,                                // h3
                colors.fgSecHex,                                // h4
                colors.codeBgHex,                               // .box
                colors.codeBgHex, colors.accentHex,             // .tip
                colors.codeBgHex, colors.accentHex,             // .example
                colors.borderHex, colors.codeBgHex,             // table
                colors.codeBgHex,                               // code
                colors.codeBgHex,                               // pre
                colors.codeBgHex,                               // .flow
                colors.fgSecHex,                                // .small
                colors.borderHex                                // hr
        );
    }

    /**
     * Convert a Color to hex string.
     */
    public static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
