package com.tradery.forge.ui.help;

import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.ext.tables.TablesExtension;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for rendering markdown files to themed HTML for help dialogs.
 * Supports:
 * - Standard markdown (headers, tables, code blocks, lists)
 * - TOC extraction from h2/h3 headings
 * - Themed HTML output using UIManager colors
 * - Special blockquote syntax for tips and examples
 */
public class MarkdownHelpRenderer {

    /**
     * Represents a table of contents entry extracted from markdown.
     */
    public static class TocEntry {
        public final String id;      // HTML anchor id (e.g., "toc-0")
        public final String title;   // Display text
        public final int level;      // 2 for h2, 3 for h3
        public int yPosition;        // Calculated after render (for scroll sync)

        public TocEntry(String id, String title, int level) {
            this.id = id;
            this.title = title;
            this.level = level;
            this.yPosition = 0;
        }
    }

    /**
     * Result of rendering markdown, containing both HTML and TOC.
     */
    public static class RenderResult {
        public final String html;
        public final List<TocEntry> tocEntries;

        public RenderResult(String html, List<TocEntry> tocEntries) {
            this.html = html;
            this.tocEntries = tocEntries;
        }
    }

    /**
     * Load markdown content from a resource path.
     *
     * @param resourcePath Path to the resource (e.g., "/help/strategy-guide.md")
     * @return The markdown content as a string, or null if not found
     */
    public static String loadFromResource(String resourcePath) {
        try (InputStream is = MarkdownHelpRenderer.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("Resource not found: " + resourcePath);
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            System.err.println("Failed to load resource: " + resourcePath + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Render markdown to themed HTML with TOC extraction.
     *
     * @param markdown The markdown content
     * @param title    The document title (displayed as h1)
     * @return RenderResult containing HTML and TOC entries
     */
    public static RenderResult render(String markdown, String title) {
        if (markdown == null || markdown.isEmpty()) {
            return new RenderResult("<html><body>No content available.</body></html>", new ArrayList<>());
        }

        // Get theme colors
        HelpStyles.ThemeColors colors = HelpStyles.getThemeColors();

        // Parse markdown
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()));
        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();

        Document document = parser.parse(markdown);

        // Extract TOC entries from h2 and h3 headings
        List<TocEntry> tocEntries = extractTocEntries(document);

        // Render markdown to HTML
        String bodyContent = renderer.render(document);

        // Post-process: add anchor IDs to headings
        bodyContent = addAnchorIds(bodyContent, tocEntries);

        // Post-process: convert blockquotes with markers to styled boxes
        bodyContent = processBlockquotes(bodyContent, colors.codeBgHex, colors.accentHex);

        // Post-process: style the flow diagrams (monospace centered)
        bodyContent = processFlowDiagrams(bodyContent, colors.codeBgHex);

        // Build complete HTML
        String css = HelpStyles.buildCss(colors);
        String html = String.format("""
            <html>
            <head>
            <style>
            %s
            </style>
            </head>
            <body>
            <h1>%s</h1>
            %s
            </body>
            </html>
            """, css, title, bodyContent);

        return new RenderResult(html, tocEntries);
    }

    /**
     * Extract TOC entries from h2 and h3 headings in the parsed document.
     */
    private static List<TocEntry> extractTocEntries(Document document) {
        List<TocEntry> entries = new ArrayList<>();
        int tocIndex = 0;

        for (Node node : document.getChildren()) {
            if (node instanceof Heading heading) {
                int level = heading.getLevel();
                if (level == 2 || level == 3) {
                    String text = heading.getText().toString();
                    entries.add(new TocEntry("toc-" + tocIndex++, text, level));
                }
            }
        }

        return entries;
    }

    /**
     * Add anchor IDs to h2/h3 headings in the rendered HTML.
     */
    private static String addAnchorIds(String html, List<TocEntry> tocEntries) {
        int entryIndex = 0;

        // Match h2 and h3 tags
        Pattern pattern = Pattern.compile("<h([23])>([^<]+)</h[23]>");
        Matcher matcher = pattern.matcher(html);

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            if (entryIndex < tocEntries.size()) {
                TocEntry entry = tocEntries.get(entryIndex);
                String replacement = String.format("<h%s id=\"%s\">%s</h%s>",
                        matcher.group(1), entry.id, matcher.group(2), matcher.group(1));
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                entryIndex++;
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Process blockquotes to convert tip/example markers to styled boxes.
     * Recognizes:
     * - > **Tip:** ... -> styled tip box
     * - > **Example:** ... -> styled example box
     * - > **Note:** ... -> styled note box
     * - Other blockquotes -> styled box
     */
    private static String processBlockquotes(String html, String boxBgHex, String accentHex) {
        // Pattern to match blockquotes
        Pattern blockquotePattern = Pattern.compile("<blockquote>\\s*(.*?)\\s*</blockquote>", Pattern.DOTALL);
        Matcher matcher = blockquotePattern.matcher(html);

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String content = matcher.group(1);

            // Check for tip marker
            if (content.contains("<strong>Tip:</strong>") || content.contains("<strong>Tip</strong>:")) {
                // Remove the marker and create tip box
                content = content.replaceFirst("<p><strong>Tip:?</strong>:?\\s*", "<p><b>Tip:</b> ");
                String replacement = String.format("<div class=\"tip\">%s</div>", content);
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            } else if (content.contains("<strong>Example:</strong>") || content.contains("<strong>Example</strong>:")) {
                content = content.replaceFirst("<p><strong>Example:?</strong>:?\\s*", "<p><b>Example:</b> ");
                String replacement = String.format("<div class=\"example\">%s</div>", content);
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            } else if (content.contains("<strong>Note:</strong>") || content.contains("<strong>Note</strong>:")) {
                content = content.replaceFirst("<p><strong>Note:?</strong>:?\\s*", "<p><b>Note:</b> ");
                String replacement = String.format("<div class=\"box\">%s</div>", content);
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            } else {
                // Generic box
                String replacement = String.format("<div class=\"box\">%s</div>", content);
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Process flow diagrams (lines with arrows) to be centered and styled.
     */
    private static String processFlowDiagrams(String html, String codeBgHex) {
        // Match lines that look like flow diagrams (contain →)
        Pattern flowPattern = Pattern.compile("<p>([^<]*→[^<]*)</p>");
        Matcher matcher = flowPattern.matcher(html);

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String content = matcher.group(1);
            String replacement = String.format("<div class=\"flow\">%s</div>", content);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
