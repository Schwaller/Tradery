package com.tradery.help;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemInfo;
import com.tradery.help.MarkdownHelpRenderer.TocEntry;
import com.tradery.ui.controls.BorderlessScrollPane;
import com.tradery.ui.controls.ThinSplitPane;
import com.tradery.ui.controls.ToolbarSearchField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.*;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable markdown-based help dialog.
 * Loads content from a classpath .md resource, renders it with theming,
 * and provides TOC sidebar with scroll sync and full-text search.
 */
public class MarkdownHelpDialog extends JDialog {

    private JList<TocEntry> tocList;
    private DefaultListModel<TocEntry> tocModel;
    private JEditorPane helpPane;
    private BorderlessScrollPane contentScrollPane;
    private boolean isScrollingFromToc = false;
    private boolean isUpdatingFromScroll = false;
    private List<TocEntry> tocEntries;

    // Search
    private ToolbarSearchField searchField;
    private List<int[]> searchMatches = new ArrayList<>();
    private int currentMatchIndex = -1;
    private Highlighter.HighlightPainter searchHighlightPainter;
    private Highlighter.HighlightPainter currentMatchPainter;

    /**
     * Create a new help dialog.
     *
     * @param owner        Parent window
     * @param title        Dialog title (shown in title bar)
     * @param resourcePath Classpath resource path to the .md file (e.g., "/help/dsl-reference.md")
     * @param size         Preferred size of the content area
     */
    public MarkdownHelpDialog(Window owner, String title, String resourcePath, Dimension size) {
        super(owner, title, ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Integrated title bar look (macOS)
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        getRootPane().putClientProperty(FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE);

        initComponents(title, resourcePath, size);
    }

    private void initComponents(String title, String resourcePath, Dimension size) {
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
        titleBar.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
        titleBar.add(rightWrapper, BorderLayout.EAST);
        titleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                UIManager.getColor("Separator.foreground")));

        // Load and render markdown content
        String markdown = MarkdownHelpRenderer.loadFromResource(resourcePath);
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

        // Create content pane
        helpPane = new JEditorPane("text/html", result.html);
        helpPane.setEditable(false);
        helpPane.setCaretPosition(0);
        helpPane.setBorder(new EmptyBorder(4, 4, 4, 4));
        helpPane.setBackground(UIManager.getColor("Panel.background"));

        contentScrollPane = new BorderlessScrollPane(helpPane);

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

        JPanel mainContent = new JPanel(new BorderLayout());
        mainContent.add(splitPane, BorderLayout.CENTER);
        mainContent.setPreferredSize(size);

        // Button panel with separator above
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(new JSeparator(), BorderLayout.NORTH);
        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        mainContent.add(bottomPanel, BorderLayout.SOUTH);

        contentPane.add(titleBar, BorderLayout.NORTH);
        contentPane.add(mainContent, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(getOwner());
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
}
