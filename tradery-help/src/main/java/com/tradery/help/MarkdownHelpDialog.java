package com.tradery.help;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemInfo;
import com.tradery.help.MarkdownHelpRenderer.TocEntry;
import com.tradery.ui.controls.BorderlessScrollPane;
import com.tradery.ui.controls.SegmentedToggle;
import com.tradery.ui.controls.ThinSplitPane;
import com.tradery.ui.controls.ToolbarSearchField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.*;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Reusable markdown-based help dialog.
 * Loads content from a classpath .md resource, renders it with theming,
 * and provides TOC sidebar with scroll sync and full-text search.
 */
public class MarkdownHelpDialog extends JDialog {

    /** A tab definition for multi-file help dialogs. */
    public record Tab(String label, String resourcePath) {}

    private JList<TocEntry> tocList;
    private DefaultListModel<TocEntry> tocModel;
    private JEditorPane helpPane;
    private BorderlessScrollPane contentScrollPane;
    private boolean isScrollingFromToc = false;
    private boolean isUpdatingFromScroll = false;
    private List<TocEntry> tocEntries;

    // Tabs
    private Tab[] tabs;
    private Class<?> resourceClass;
    private String dialogTitle;
    private SegmentedToggle tabToggle;

    // Search
    private ToolbarSearchField searchField;
    private List<int[]> searchMatches = new ArrayList<>();
    private int currentMatchIndex = -1;
    private Highlighter.HighlightPainter searchHighlightPainter;
    private Highlighter.HighlightPainter currentMatchPainter;
    private JPanel crossTabHintPanel;

    /**
     * Create a new help dialog.
     *
     * @param owner        Parent window
     * @param title        Dialog title (shown in title bar)
     * @param resourcePath Classpath resource path to the .md file (e.g., "/help/dsl-reference.md")
     * @param size         Preferred size of the content area
     */
    public MarkdownHelpDialog(Window owner, String title, String resourcePath, Dimension size) {
        this(owner, title, resourcePath, size, MarkdownHelpRenderer.class);
    }

    /**
     * Create a new help dialog loading resources from a specific module.
     *
     * @param owner         Parent window
     * @param title         Dialog title (shown in title bar)
     * @param resourcePath  Classpath resource path to the .md file
     * @param size          Preferred size of the content area
     * @param resourceClass Class whose module/classloader is used to load the resource
     */
    public MarkdownHelpDialog(Window owner, String title, String resourcePath, Dimension size, Class<?> resourceClass) {
        this(owner, title, resourcePath, size, resourceClass, null);
    }

    /**
     * Create a help dialog with tabbed navigation.
     * When tabs are provided, a segmented toggle appears in the title bar.
     * The first tab is selected by default.
     *
     * @param owner         Parent window
     * @param title         Dialog title
     * @param resourcePath  Initial resource path (used for first tab if tabs is null)
     * @param size          Preferred content size
     * @param resourceClass Class for resource loading
     * @param tabs          Tab definitions (label + resource path), or null for single-file mode
     */
    public MarkdownHelpDialog(Window owner, String title, String resourcePath, Dimension size,
                              Class<?> resourceClass, Tab[] tabs) {
        super(owner, title, ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        this.tabs = tabs;
        this.resourceClass = resourceClass;
        this.dialogTitle = title;

        // Integrated title bar look (macOS)
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        String initialResource = (tabs != null && tabs.length > 0) ? tabs[0].resourcePath : resourcePath;
        initComponents(title, initialResource, size, resourceClass);
    }

    private void initComponents(String title, String resourcePath, Dimension size, Class<?> resourceClass) {
        JPanel contentPane = new JPanel(new BorderLayout());
        setContentPane(contentPane);

        // Initialize search highlight painters
        Color highlightColor = new Color(255, 255, 0, 100); // Yellow with transparency
        Color currentMatchColor = new Color(255, 150, 0, 150); // Orange for current match
        searchHighlightPainter = new DefaultHighlighter.DefaultHighlightPainter(highlightColor);
        currentMatchPainter = new DefaultHighlighter.DefaultHighlightPainter(currentMatchColor);

        // Title bar area (52px — matching ProjectWindow)
        int barHeight = 52;
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setPreferredSize(new Dimension(0, barHeight));

        // Left: traffic light placeholder + title
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftPanel.setOpaque(false);
        if (SystemInfo.isMacOS) {
            JPanel buttonsPlaceholder = new JPanel();
            buttonsPlaceholder.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER, "mac");
            buttonsPlaceholder.setOpaque(false);
            leftPanel.add(buttonsPlaceholder);
        }

        // Title styled like ProjectWindow
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        titleLabel.setForeground(new Color(160, 160, 170));
        leftPanel.add(titleLabel);

        // Search field on right (toolbar-height, 8px right margin)
        searchField = new ToolbarSearchField(14);
        searchField.setSearchListener(text -> performSearch());
        searchField.setNextMatchAction(this::goToNextMatch);
        searchField.setPrevMatchAction(this::goToPreviousMatch);

        // Vertically center both sides using GridBagLayout
        JPanel leftWrapper = new JPanel(new GridBagLayout());
        leftWrapper.setOpaque(false);
        leftWrapper.add(leftPanel);

        JPanel rightWrapper = new JPanel(new GridBagLayout());
        rightWrapper.setOpaque(false);
        rightWrapper.setBorder(new EmptyBorder(0, 0, 0, 8));
        rightWrapper.add(searchField);

        titleBar.add(leftWrapper, BorderLayout.WEST);

        // Tab toggle (center of title bar) when tabs are provided
        if (tabs != null && tabs.length > 1) {
            String[] labels = new String[tabs.length];
            for (int i = 0; i < tabs.length; i++) labels[i] = tabs[i].label;
            tabToggle = new SegmentedToggle(labels);
            tabToggle.setOnSelectionChanged(index -> {
                loadContent(tabs[index].resourcePath, resourceClass);
                // Persist selected tab
                Preferences prefs = Preferences.userNodeForPackage(MarkdownHelpDialog.class);
                prefs.putInt("tab." + dialogTitle, index);
            });

            JPanel centerWrapper = new JPanel(new GridBagLayout());
            centerWrapper.setOpaque(false);
            centerWrapper.add(tabToggle);
            titleBar.add(centerWrapper, BorderLayout.CENTER);
        } else {
            titleBar.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
        }

        titleBar.add(rightWrapper, BorderLayout.EAST);

        // Load and render markdown content
        String markdown = MarkdownHelpRenderer.loadFromResource(resourcePath, resourceClass);
        MarkdownHelpRenderer.RenderResult result = MarkdownHelpRenderer.render(markdown, title);
        tocEntries = result.tocEntries;

        // Create TOC list
        tocModel = new DefaultListModel<>();
        for (TocEntry entry : tocEntries) {
            tocModel.addElement(entry);
        }
        tocList = new JList<>(tocModel);
        tocList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tocList.setCellRenderer(new TocCellRenderer());
        tocList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !isUpdatingFromScroll) {
                scrollToSelectedSection();
            }
        });

        BorderlessScrollPane tocScrollPane = new BorderlessScrollPane(tocList);
        tocScrollPane.setPreferredSize(new Dimension(180, 0));

        // Create content pane with SVG support
        helpPane = new JEditorPane();
        HelpEditorKit editorKit = new HelpEditorKit();
        helpPane.setEditorKit(editorKit);
        HTMLDocument doc = (HTMLDocument) editorKit.createDefaultDocument();
        String basePath = resourcePath.substring(0, resourcePath.lastIndexOf('/') + 1);
        doc.putProperty("resourceBasePath", basePath);
        doc.putProperty("resourceClass", resourceClass);
        helpPane.setDocument(doc);
        helpPane.setText(result.html);
        helpPane.setEditable(false);
        helpPane.setCaretPosition(0);
        helpPane.getCaret().setVisible(false);
        helpPane.putClientProperty("caretWidth", 0);
        helpPane.setBorder(new EmptyBorder(4, 4, 4, 4));
        helpPane.setBackground(UIManager.getColor("Panel.background"));

        // Hyperlink listener for cross-tab links (tab:Label#anchor) and external URLs
        helpPane.addHyperlinkListener(e -> {
            if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
            String desc = e.getDescription();
            if (desc != null && desc.startsWith("tab:")) {
                handleCrossTabLink(desc.substring(4));
            } else if (e.getURL() != null) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ex) {
                    // Ignore
                }
            }
        });

        contentScrollPane = new BorderlessScrollPane(helpPane);
        contentScrollPane.getVerticalScrollBar().setUnitIncrement(1);

        // Override mouse wheel to dampen macOS trackpad/scroll acceleration
        contentScrollPane.setWheelScrollingEnabled(false);
        contentScrollPane.addMouseWheelListener(e -> {
            JScrollBar vbar = contentScrollPane.getVerticalScrollBar();
            // Use precise rotation for smooth trackpad, fallback to click count
            double rotation = e.getPreciseWheelRotation();
            int pixels = (int) Math.round(rotation * 8); // 8px per unit of rotation
            vbar.setValue(vbar.getValue() + pixels);
        });

        // Track scroll position to update TOC selection
        contentScrollPane.getViewport().addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (!isScrollingFromToc) {
                    updateTocSelectionFromScroll();
                }
            }
        });

        // Calculate positions after HTML is rendered
        SwingUtilities.invokeLater(() -> {
            SwingUtilities.invokeLater(this::calculateTocPositions);
        });

        // Create split pane
        ThinSplitPane splitPane = new ThinSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(tocScrollPane);
        splitPane.setRightComponent(contentScrollPane);
        splitPane.setDividerLocation(180);
        splitPane.setResizeWeight(0);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(closeButton);

        // Cross-tab search hint panel (hidden by default)
        crossTabHintPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        crossTabHintPanel.setVisible(false);
        crossTabHintPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(1, 8, 1, 8)));

        JPanel mainContent = new JPanel(new BorderLayout());
        mainContent.add(crossTabHintPanel, BorderLayout.NORTH);
        mainContent.add(splitPane, BorderLayout.CENTER);
        mainContent.setPreferredSize(size);

        // Button panel with separator above
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(new JSeparator(), BorderLayout.NORTH);
        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        mainContent.add(bottomPanel, BorderLayout.SOUTH);

        JPanel titleBarWrapper = new JPanel(new BorderLayout());
        titleBarWrapper.add(titleBar, BorderLayout.CENTER);
        titleBarWrapper.add(new JSeparator(), BorderLayout.SOUTH);
        contentPane.add(titleBarWrapper, BorderLayout.NORTH);
        contentPane.add(mainContent, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(getOwner());

        // Restore persisted tab selection
        if (tabs != null && tabs.length > 1 && tabToggle != null) {
            Preferences prefs = Preferences.userNodeForPackage(MarkdownHelpDialog.class);
            int storedIndex = prefs.getInt("tab." + dialogTitle, 0);
            if (storedIndex > 0 && storedIndex < tabs.length) {
                tabToggle.setSelectedIndex(storedIndex);
                loadContent(tabs[storedIndex].resourcePath, resourceClass);
            }
        }
    }

    private void calculateTocPositions() {
        if (helpPane.getDocument() instanceof HTMLDocument doc) {
            for (TocEntry entry : tocEntries) {
                Element element = doc.getElement(entry.id);
                if (element != null) {
                    try {
                        Rectangle rect = helpPane.modelToView(element.getStartOffset());
                        if (rect != null) {
                            entry.yPosition = rect.y;
                        }
                    } catch (BadLocationException e) {
                        // Ignore
                    }
                }
            }
        }
    }

    /**
     * Loads new markdown content into the dialog, replacing the current content and TOC.
     * Used by tab switching.
     */
    private void loadContent(String resourcePath, Class<?> resClass) {
        String markdown = MarkdownHelpRenderer.loadFromResource(resourcePath, resClass);
        MarkdownHelpRenderer.RenderResult result = MarkdownHelpRenderer.render(markdown, dialogTitle);
        tocEntries = result.tocEntries;

        // Update TOC
        tocModel.clear();
        for (TocEntry entry : tocEntries) {
            tocModel.addElement(entry);
        }

        // Update content with SVG support
        HelpEditorKit editorKit = new HelpEditorKit();
        helpPane.setEditorKit(editorKit);
        HTMLDocument doc = (HTMLDocument) editorKit.createDefaultDocument();
        String basePath = resourcePath.substring(0, resourcePath.lastIndexOf('/') + 1);
        doc.putProperty("resourceBasePath", basePath);
        doc.putProperty("resourceClass", resClass);
        helpPane.setDocument(doc);
        helpPane.setText(result.html);
        helpPane.setCaretPosition(0);
        helpPane.getCaret().setVisible(false);

        // Re-run search on new tab content if search is active
        searchMatches.clear();
        currentMatchIndex = -1;

        // Recalculate TOC positions after layout, then re-run search if active
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
            calculateTocPositions();
            String searchText = searchField.getText().trim();
            if (!searchText.isEmpty()) {
                performSearch();
            } else {
                searchField.clearMatchInfo();
                crossTabHintPanel.setVisible(false);
            }
        }));
    }

    private void scrollToSelectedSection() {
        TocEntry selected = tocList.getSelectedValue();
        if (selected == null) return;

        isScrollingFromToc = true;
        try {
            if (helpPane.getDocument() instanceof HTMLDocument doc) {
                Element element = doc.getElement(selected.id);
                if (element != null) {
                    try {
                        Rectangle rect = helpPane.modelToView(element.getStartOffset());
                        if (rect != null) {
                            // Scroll to position with a small offset from top
                            rect.y = Math.max(0, rect.y - 10);
                            rect.height = contentScrollPane.getViewport().getHeight();
                            helpPane.scrollRectToVisible(rect);
                        }
                    } catch (BadLocationException e) {
                        // Ignore
                    }
                }
            }
        } finally {
            // Reset flag after a short delay to allow scroll to complete
            Timer timer = new Timer(100, e -> isScrollingFromToc = false);
            timer.setRepeats(false);
            timer.start();
        }
    }

    private void updateTocSelectionFromScroll() {
        if (tocEntries.isEmpty()) return;

        Rectangle viewRect = contentScrollPane.getViewport().getViewRect();
        int targetY = viewRect.y + viewRect.height / 3; // Upper third of view

        TocEntry bestMatch = tocEntries.get(0);
        for (TocEntry entry : tocEntries) {
            if (entry.yPosition <= targetY) {
                bestMatch = entry;
            } else {
                break;
            }
        }

        isUpdatingFromScroll = true;
        try {
            tocList.setSelectedValue(bestMatch, true);
        } finally {
            isUpdatingFromScroll = false;
        }
    }

    /**
     * Custom renderer for TOC entries with indentation based on heading level.
     */
    private class TocCellRenderer extends JPanel implements ListCellRenderer<TocEntry> {
        private final JLabel label;

        public TocCellRenderer() {
            setLayout(new BorderLayout());
            label = new JLabel();
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends TocEntry> list,
                TocEntry value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            // Indentation: 8px for h2, 20px for h3
            int leftPadding = value.level == 2 ? 8 : 20;
            setBorder(BorderFactory.createEmptyBorder(3, leftPadding, 3, 8));

            label.setText(value.title);
            label.setFont(label.getFont().deriveFont(value.level == 2 ? Font.BOLD : Font.PLAIN, 11f));

            if (isSelected) {
                setBackground(UIManager.getColor("List.selectionBackground"));
                label.setForeground(UIManager.getColor("List.selectionForeground"));
                setOpaque(true);
            } else {
                setBackground(list.getBackground());
                label.setForeground(value.level == 2
                    ? UIManager.getColor("Label.foreground")
                    : UIManager.getColor("Label.disabledForeground"));
                setOpaque(false);
            }

            return this;
        }
    }

    private void performSearch() {
        clearSearchHighlights();
        searchMatches.clear();
        currentMatchIndex = -1;

        String searchText = searchField.getText().toLowerCase().trim();
        if (searchText.isEmpty()) {
            searchField.clearMatchInfo();
            crossTabHintPanel.setVisible(false);
            crossTabHintPanel.revalidate();
            return;
        }

        try {
            Document doc = helpPane.getDocument();
            String text = doc.getText(0, doc.getLength()).toLowerCase();

            int index = 0;
            while ((index = text.indexOf(searchText, index)) != -1) {
                searchMatches.add(new int[]{index, index + searchText.length()});
                index += searchText.length();
            }

            if (searchMatches.isEmpty()) {
                searchField.setMatchInfo(0, 0);
            } else {
                // Highlight all matches
                Highlighter highlighter = helpPane.getHighlighter();
                for (int[] match : searchMatches) {
                    highlighter.addHighlight(match[0], match[1], searchHighlightPainter);
                }
                // Go to first match
                currentMatchIndex = 0;
                highlightCurrentMatch();
                updateSearchResultLabel();
            }
        } catch (BadLocationException e) {
            // Ignore
        }

        // Search other tabs and show cross-tab hints
        updateCrossTabHint(searchText);
    }

    private void highlightCurrentMatch() {
        if (searchMatches.isEmpty() || currentMatchIndex < 0) return;

        try {
            int[] match = searchMatches.get(currentMatchIndex);

            // Remove previous current highlight and re-add all with normal color
            Highlighter highlighter = helpPane.getHighlighter();
            highlighter.removeAllHighlights();
            for (int i = 0; i < searchMatches.size(); i++) {
                int[] m = searchMatches.get(i);
                Highlighter.HighlightPainter painter = (i == currentMatchIndex) ? currentMatchPainter : searchHighlightPainter;
                highlighter.addHighlight(m[0], m[1], painter);
            }

            // Scroll to current match
            Rectangle rect = helpPane.modelToView(match[0]);
            if (rect != null) {
                rect.y = Math.max(0, rect.y - 50);
                rect.height = 100;
                helpPane.scrollRectToVisible(rect);
            }
        } catch (BadLocationException e) {
            // Ignore
        }
    }

    private void goToNextMatch() {
        if (searchMatches.isEmpty()) return;
        currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size();
        highlightCurrentMatch();
        updateSearchResultLabel();
    }

    private void goToPreviousMatch() {
        if (searchMatches.isEmpty()) return;
        currentMatchIndex = (currentMatchIndex - 1 + searchMatches.size()) % searchMatches.size();
        highlightCurrentMatch();
        updateSearchResultLabel();
    }

    private void updateSearchResultLabel() {
        if (searchMatches.isEmpty()) {
            searchField.setMatchInfo(0, 0);
        } else {
            searchField.setMatchInfo(currentMatchIndex + 1, searchMatches.size());
        }
    }

    private void clearSearchHighlights() {
        helpPane.getHighlighter().removeAllHighlights();
    }

    /**
     * Search other tabs for the given search term and return match counts.
     */
    private Map<String, Integer> searchOtherTabs(String searchText) {
        Map<String, Integer> results = new LinkedHashMap<>();
        if (tabs == null || tabs.length <= 1 || searchText.isEmpty()) return results;

        int currentTabIndex = tabToggle != null ? tabToggle.getSelectedIndex() : 0;
        String lowerSearch = searchText.toLowerCase();

        for (int i = 0; i < tabs.length; i++) {
            if (i == currentTabIndex) continue;
            try (InputStream is = resourceClass.getResourceAsStream(tabs[i].resourcePath())) {
                if (is == null) continue;
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
                int count = 0;
                int idx = 0;
                while ((idx = content.indexOf(lowerSearch, idx)) != -1) {
                    count++;
                    idx += lowerSearch.length();
                }
                if (count > 0) {
                    results.put(tabs[i].label(), count);
                }
            } catch (Exception e) {
                // Skip this tab
            }
        }
        return results;
    }

    /**
     * Update the cross-tab hint panel to show which other tabs contain the search term.
     */
    private void updateCrossTabHint(String searchText) {
        crossTabHintPanel.removeAll();

        if (tabs == null || tabs.length <= 1 || searchText.isEmpty()) {
            crossTabHintPanel.setVisible(false);
            crossTabHintPanel.revalidate();
            return;
        }

        Map<String, Integer> otherTabResults = searchOtherTabs(searchText);
        if (otherTabResults.isEmpty()) {
            crossTabHintPanel.setVisible(false);
            crossTabHintPanel.revalidate();
            return;
        }

        // Build hint text
        Color dimColor = UIManager.getColor("Label.disabledForeground");
        Color accentColor = UIManager.getColor("Component.accentColor");
        if (accentColor == null) accentColor = new Color(120, 160, 255);

        String prefix = searchMatches.isEmpty() ? "No results. Found in:" : "Also in:";
        JLabel prefixLabel = new JLabel(prefix);
        prefixLabel.setFont(prefixLabel.getFont().deriveFont(11f));
        prefixLabel.setForeground(dimColor);
        crossTabHintPanel.add(prefixLabel);

        boolean first = true;
        for (var entry : otherTabResults.entrySet()) {
            if (!first) {
                JLabel separator = new JLabel(" · ");
                separator.setFont(separator.getFont().deriveFont(11f));
                separator.setForeground(dimColor);
                crossTabHintPanel.add(separator);
            }
            first = false;

            String tabName = entry.getKey();
            int count = entry.getValue();
            JLabel tabLabel = new JLabel(tabName + " (" + count + ")");
            tabLabel.setFont(tabLabel.getFont().deriveFont(11f));
            tabLabel.setForeground(accentColor);
            tabLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // Underline on hover
            tabLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    tabLabel.setText("<html><u>" + tabName + " (" + count + ")</u></html>");
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    tabLabel.setText(tabName + " (" + count + ")");
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    handleCrossTabLink(tabName);
                }
            });
            crossTabHintPanel.add(tabLabel);
        }

        crossTabHintPanel.setVisible(true);
        crossTabHintPanel.revalidate();
        crossTabHintPanel.repaint();
    }

    /**
     * Handle a cross-tab link in the format "TabLabel#anchor-slug" or just "TabLabel".
     */
    private void handleCrossTabLink(String link) {
        if (tabs == null || tabToggle == null) return;

        String tabLabel;
        String anchorSlug = null;
        int hashIndex = link.indexOf('#');
        if (hashIndex >= 0) {
            tabLabel = link.substring(0, hashIndex);
            anchorSlug = link.substring(hashIndex + 1);
        } else {
            tabLabel = link;
        }

        // Find matching tab
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i].label().equalsIgnoreCase(tabLabel)) {
                int currentIndex = tabToggle.getSelectedIndex();
                if (i != currentIndex) {
                    tabToggle.setSelectedIndex(i);
                    loadContent(tabs[i].resourcePath(), resourceClass);
                    // Persist tab change
                    Preferences prefs = Preferences.userNodeForPackage(MarkdownHelpDialog.class);
                    prefs.putInt("tab." + dialogTitle, i);
                }
                if (anchorSlug != null) {
                    scrollToAnchor(anchorSlug);
                }
                return;
            }
        }
    }

    /**
     * Scroll to an anchor element by its ID. Deferred to allow layout to complete after tab switch.
     */
    private void scrollToAnchor(String anchorId) {
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
            if (helpPane.getDocument() instanceof HTMLDocument doc) {
                Element element = doc.getElement(anchorId);
                if (element != null) {
                    try {
                        Rectangle rect = helpPane.modelToView(element.getStartOffset());
                        if (rect != null) {
                            rect.y = Math.max(0, rect.y - 10);
                            rect.height = contentScrollPane.getViewport().getHeight();
                            helpPane.scrollRectToVisible(rect);
                        }
                    } catch (BadLocationException e) {
                        // Ignore
                    }
                }
            }
        }));
    }
}
