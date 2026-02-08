package com.tradery.ui;

import com.tradery.ui.controls.*;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test window showing toolbar components rendered in multiple themes at once.
 * Each theme gets its own row — no switching needed.
 * <p>
 * Includes a tiny HTTP server for screenshot access:
 * <pre>
 *   GET /screenshot          → PNG of the full window content
 *   GET /screenshot?save=1   → saves to ~/.tradery/toolbar-test.png too
 * </pre>
 * Port is written to ~/.tradery/toolbar-test.port
 * <p>
 * Run: ./gradlew :tradery-ui-common:run
 */
public class ToolbarTestWindow extends JFrame {

    private static final String SCREENSHOT_PATH = System.getProperty("user.home")
            + "/.tradery/toolbar-test.png";
    private static final String PORT_PATH = System.getProperty("user.home")
            + "/.tradery/toolbar-test.port";

    private static final List<String> PREVIEW_THEMES = List.of(
            "Hiberbee Dark", "Flat Light", "Flat Dark", "macOS Dark",
            "Nord", "Dracula", "Solarized Light"
    );

    public ToolbarTestWindow() {
        super("Toolbar Components");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // macOS integrated title bar
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(new EmptyBorder(32, 0, 0, 0)); // title bar space

        for (String theme : PREVIEW_THEMES) {
            main.add(createThemeRow(theme));
        }

        // Screenshot button at bottom
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        JButton screenshotBtn = new JButton("Save Screenshot");
        screenshotBtn.setFont(ToolbarButton.TOOLBAR_FONT);
        screenshotBtn.addActionListener(e -> saveScreenshot());
        btnRow.add(screenshotBtn);
        main.add(btnRow);

        JScrollPane scroll = new JScrollPane(main);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        setContentPane(scroll);

        setSize(760, 600);
        setLocationRelativeTo(null);
    }

    /**
     * Creates one themed row by temporarily switching LAF, building the toolbar
     * panel, painting it to a BufferedImage, then restoring the original LAF.
     */
    private JPanel createThemeRow(String themeName) {
        String originalLaf = UIManager.getLookAndFeel().getClass().getName();

        // Temporarily apply the target theme
        ThemeHelper.applyTheme(themeName);

        // Build toolbar under this theme
        JPanel toolbar = buildToolbar();
        toolbar.setSize(720, 44);
        toolbar.doLayout();
        layoutRecursive(toolbar);

        // Paint to image
        BufferedImage img = new BufferedImage(720, 44, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        toolbar.paint(g2);
        g2.dispose();

        // Restore original theme
        try {
            UIManager.setLookAndFeel(originalLaf);
        } catch (Exception ignored) {}

        // Wrapper panel: theme label + rendered image
        JPanel row = new JPanel(new BorderLayout());
        row.setBorder(new EmptyBorder(4, 12, 4, 12));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

        JLabel label = new JLabel(themeName);
        label.setFont(new Font("SansSerif", Font.PLAIN, 11));
        label.setPreferredSize(new Dimension(120, 44));
        row.add(label, BorderLayout.WEST);

        JLabel imageLabel = new JLabel(new ImageIcon(img));
        imageLabel.setBorder(BorderFactory.createLineBorder(new Color(128, 128, 128, 60)));
        row.add(imageLabel, BorderLayout.CENTER);

        return row;
    }

    private JPanel buildToolbar() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(new EmptyBorder(6, 12, 6, 12));
        panel.setBackground(UIManager.getColor("Panel.background"));
        panel.setOpaque(true);

        panel.add(new SegmentedToggle("Charts", "Trades", "Summary"));
        panel.add(Box.createHorizontalStrut(12));
        panel.add(new ToolbarButton("Backtest"));
        panel.add(Box.createHorizontalStrut(4));
        panel.add(new ToolbarButton("Settings"));
        panel.add(Box.createHorizontalStrut(12));
        panel.add(new ToolbarComboBox<>(new String[]{"BTCUSDT", "ETHUSDT", "SOLUSDT"}));
        panel.add(Box.createHorizontalStrut(12));

        ToolbarSearchField search = new ToolbarSearchField(10);
        search.setMatchInfo(3, 15);
        panel.add(search);

        panel.add(Box.createHorizontalGlue());
        return panel;
    }

    private void layoutRecursive(Component c) {
        if (c instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutRecursive(child);
            }
        }
    }

    /** Capture content pane as PNG bytes (called on EDT). */
    private byte[] captureScreenshot() {
        JComponent content = (JComponent) ((JScrollPane) getContentPane()).getViewport().getView();
        BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        content.paint(image.getGraphics());
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveScreenshot() {
        try {
            byte[] png = captureScreenshot();
            File file = new File(SCREENSHOT_PATH);
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), png);
            System.out.println("Screenshot saved: " + file.getAbsolutePath());
        } catch (Exception ex) {
            System.err.println("Screenshot failed: " + ex.getMessage());
        }
    }

    /** Start HTTP server for screenshot endpoint. */
    private void startHttpServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            int port = server.getAddress().getPort();

            server.createContext("/screenshot", exchange -> {
                try {
                    // Capture on EDT and wait for result
                    AtomicReference<byte[]> result = new AtomicReference<>();
                    CountDownLatch latch = new CountDownLatch(1);
                    SwingUtilities.invokeLater(() -> {
                        result.set(captureScreenshot());
                        latch.countDown();
                    });
                    latch.await();

                    byte[] png = result.get();

                    // Optionally save to file too
                    String query = exchange.getRequestURI().getQuery();
                    if (query != null && query.contains("save=1")) {
                        File file = new File(SCREENSHOT_PATH);
                        file.getParentFile().mkdirs();
                        Files.write(file.toPath(), png);
                    }

                    exchange.getResponseHeaders().set("Content-Type", "image/png");
                    exchange.sendResponseHeaders(200, png.length);
                    exchange.getResponseBody().write(png);
                    exchange.getResponseBody().close();
                } catch (Exception e) {
                    byte[] err = e.getMessage().getBytes();
                    exchange.sendResponseHeaders(500, err.length);
                    exchange.getResponseBody().write(err);
                    exchange.getResponseBody().close();
                }
            });

            server.start();

            // Write port file
            Path portFile = Path.of(PORT_PATH);
            Files.createDirectories(portFile.getParent());
            Files.writeString(portFile, String.valueOf(port));

            System.out.println("Screenshot server: http://127.0.0.1:" + port + "/screenshot");
        } catch (IOException e) {
            System.err.println("Failed to start HTTP server: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "Toolbar Test");
        ThemeHelper.applyCurrentTheme();
        SwingUtilities.invokeLater(() -> {
            ToolbarTestWindow window = new ToolbarTestWindow();
            window.setVisible(true);
            window.startHttpServer();
        });
    }
}
