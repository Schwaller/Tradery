package com.tradery.ui;

import com.tradery.data.DataType;
import com.tradery.data.PageState;
import com.tradery.data.page.DataPageManager;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Tree panel showing all active data pages organized by:
 * DataType -> Symbol -> Timeframe -> Page
 */
public class PageManagerTreePanel extends JPanel {

    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final BiConsumer<String, DataType> onPageSelected;

    // Track expansion state
    private final Set<String> expandedPaths = new HashSet<>();

    // Colors for status
    private static final Color COLOR_READY = new Color(60, 140, 60);
    private static final Color COLOR_LOADING = new Color(100, 100, 180);
    private static final Color COLOR_UPDATING = new Color(140, 140, 60);
    private static final Color COLOR_ERROR = new Color(180, 80, 80);
    private static final Color COLOR_EMPTY = new Color(100, 100, 100);

    public PageManagerTreePanel(BiConsumer<String, DataType> onPageSelected) {
        this.onPageSelected = onPageSelected;

        setLayout(new BorderLayout());

        // Create tree model
        rootNode = new DefaultMutableTreeNode("Data Pages");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);

        // Configure tree
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new PageTreeCellRenderer());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        // Selection listener
        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof PageNodeData data) {
                onPageSelected.accept(data.pageKey, data.dataType);
            }
        });

        // Track expansion state
        tree.addTreeExpansionListener(new javax.swing.event.TreeExpansionListener() {
            @Override
            public void treeExpanded(javax.swing.event.TreeExpansionEvent event) {
                expandedPaths.add(getPathString(event.getPath()));
            }

            @Override
            public void treeCollapsed(javax.swing.event.TreeExpansionEvent event) {
                expandedPaths.remove(getPathString(event.getPath()));
            }
        });

        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    /**
     * Update the tree with current page data.
     */
    public void update(List<DataPageManager.PageInfo> pages) {
        // Remember selected path
        TreePath selectedPath = tree.getSelectionPath();
        String selectedKey = null;
        if (selectedPath != null) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
            if (node.getUserObject() instanceof PageNodeData data) {
                selectedKey = data.pageKey;
            }
        }

        // Build tree structure: DataType -> Symbol -> Timeframe -> Page
        rootNode.removeAllChildren();

        // Group pages by data type
        Map<DataType, List<DataPageManager.PageInfo>> byDataType = new LinkedHashMap<>();
        for (DataType dt : DataType.values()) {
            byDataType.put(dt, new ArrayList<>());
        }
        for (DataPageManager.PageInfo page : pages) {
            byDataType.get(page.dataType()).add(page);
        }

        // Build tree nodes
        for (Map.Entry<DataType, List<DataPageManager.PageInfo>> entry : byDataType.entrySet()) {
            DataType dataType = entry.getKey();
            List<DataPageManager.PageInfo> typePages = entry.getValue();

            if (typePages.isEmpty()) continue;

            // Data type node
            DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(
                new CategoryNodeData(dataType.getDisplayName(), typePages.size(), getOverallState(typePages)));
            rootNode.add(typeNode);

            // Group by symbol
            Map<String, List<DataPageManager.PageInfo>> bySymbol = new LinkedHashMap<>();
            for (DataPageManager.PageInfo page : typePages) {
                bySymbol.computeIfAbsent(page.symbol(), k -> new ArrayList<>()).add(page);
            }

            for (Map.Entry<String, List<DataPageManager.PageInfo>> symbolEntry : bySymbol.entrySet()) {
                String symbol = symbolEntry.getKey();
                List<DataPageManager.PageInfo> symbolPages = symbolEntry.getValue();

                if (symbolPages.size() == 1) {
                    // Single page, add directly under type
                    DataPageManager.PageInfo page = symbolPages.get(0);
                    String label = page.timeframe() != null
                        ? symbol + "/" + page.timeframe()
                        : symbol;
                    DefaultMutableTreeNode pageNode = new DefaultMutableTreeNode(
                        new PageNodeData(label, page.key(), dataType, page.state(),
                            page.recordCount(), page.listenerCount(), page.consumers()));
                    typeNode.add(pageNode);
                } else {
                    // Multiple pages, group by symbol
                    DefaultMutableTreeNode symbolNode = new DefaultMutableTreeNode(
                        new CategoryNodeData(symbol, symbolPages.size(), getOverallState(symbolPages)));
                    typeNode.add(symbolNode);

                    for (DataPageManager.PageInfo page : symbolPages) {
                        String label = page.timeframe() != null ? page.timeframe() : "default";
                        DefaultMutableTreeNode pageNode = new DefaultMutableTreeNode(
                            new PageNodeData(label, page.key(), dataType, page.state(),
                                page.recordCount(), page.listenerCount(), page.consumers()));
                        symbolNode.add(pageNode);
                    }
                }
            }
        }

        treeModel.reload();

        // Restore expansion state
        restoreExpansion(rootNode, new TreePath(rootNode));

        // Restore selection
        if (selectedKey != null) {
            selectPageByKey(selectedKey);
        }
    }

    private PageState getOverallState(List<DataPageManager.PageInfo> pages) {
        boolean anyLoading = false;
        boolean anyError = false;
        boolean anyUpdating = false;

        for (DataPageManager.PageInfo page : pages) {
            switch (page.state()) {
                case LOADING -> anyLoading = true;
                case UPDATING -> anyUpdating = true;
                case ERROR -> anyError = true;
            }
        }

        if (anyLoading) return PageState.LOADING;
        if (anyError) return PageState.ERROR;
        if (anyUpdating) return PageState.UPDATING;
        return PageState.READY;
    }

    private String getPathString(TreePath path) {
        StringBuilder sb = new StringBuilder();
        for (Object comp : path.getPath()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) comp;
            Object userObj = node.getUserObject();
            if (userObj instanceof CategoryNodeData cat) {
                sb.append("/").append(cat.name);
            } else if (userObj instanceof PageNodeData page) {
                sb.append("/").append(page.label);
            } else {
                sb.append("/").append(userObj);
            }
        }
        return sb.toString();
    }

    private void restoreExpansion(DefaultMutableTreeNode node, TreePath path) {
        String pathStr = getPathString(path);
        if (expandedPaths.contains(pathStr) || expandedPaths.isEmpty()) {
            tree.expandPath(path);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            restoreExpansion(child, path.pathByAddingChild(child));
        }
    }

    private void selectPageByKey(String pageKey) {
        selectPageByKey(rootNode, pageKey, new TreePath(rootNode));
    }

    private boolean selectPageByKey(DefaultMutableTreeNode node, String pageKey, TreePath path) {
        if (node.getUserObject() instanceof PageNodeData data && data.pageKey.equals(pageKey)) {
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
            return true;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            if (selectPageByKey(child, pageKey, path.pathByAddingChild(child))) {
                return true;
            }
        }
        return false;
    }

    // ========== Node Data Classes ==========

    record CategoryNodeData(String name, int count, PageState overallState) {}

    record PageNodeData(String label, String pageKey, DataType dataType, PageState state,
                        int recordCount, int listenerCount, List<String> consumers) {}

    // ========== Cell Renderer ==========

    private class PageTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean sel, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObj = node.getUserObject();

            if (userObj instanceof CategoryNodeData cat) {
                setText(cat.name + " (" + cat.count + ")");
                setIcon(null);
                setForeground(sel ? getTextSelectionColor() : getColorForState(cat.overallState));
            } else if (userObj instanceof PageNodeData page) {
                String text = page.label;
                if (page.recordCount > 0) {
                    text += " [" + formatNumber(page.recordCount) + "]";
                }
                if (page.state == PageState.LOADING || page.state == PageState.UPDATING) {
                    text += " " + getStatusDot(page.state);
                } else if (page.state == PageState.ERROR) {
                    text += " " + getStatusDot(page.state);
                }
                setText(text);
                setIcon(null);
                setForeground(sel ? getTextSelectionColor() : getColorForState(page.state));
            }

            return this;
        }

        private Color getColorForState(PageState state) {
            return switch (state) {
                case EMPTY -> COLOR_EMPTY;
                case LOADING -> COLOR_LOADING;
                case READY -> COLOR_READY;
                case UPDATING -> COLOR_UPDATING;
                case ERROR -> COLOR_ERROR;
            };
        }

        private String getStatusDot(PageState state) {
            return switch (state) {
                case EMPTY -> "\u25CB"; // empty circle
                case LOADING -> "\u25CE"; // bullseye
                case READY -> "\u25CF"; // filled circle
                case UPDATING -> "\u25D4"; // half circle
                case ERROR -> "\u2717"; // x mark
            };
        }

        private String formatNumber(int n) {
            if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
            if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
            return String.valueOf(n);
        }
    }
}
