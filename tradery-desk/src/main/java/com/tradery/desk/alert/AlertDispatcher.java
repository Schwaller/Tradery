package com.tradery.desk.alert;

import com.tradery.desk.DeskConfig;
import com.tradery.desk.signal.SignalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Routes signals to all configured alert outputs.
 */
public class AlertDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AlertDispatcher.class);

    private final List<AlertOutput> outputs = new ArrayList<>();
    private final ConsoleLogger consoleLogger;
    private final DesktopNotifier desktopNotifier;
    private final AudioAlert audioAlert;
    private final WebhookSender webhookSender;

    public AlertDispatcher(DeskConfig config) {
        DeskConfig.AlertSettings alerts = config.getAlerts();

        // Initialize outputs
        this.consoleLogger = new ConsoleLogger(alerts.isConsole());
        this.desktopNotifier = new DesktopNotifier(alerts.isDesktop());
        this.audioAlert = new AudioAlert(alerts.isAudio());
        this.webhookSender = new WebhookSender(
            alerts.getWebhook().getUrl(),
            alerts.getWebhook().isEnabled()
        );

        outputs.add(consoleLogger);
        outputs.add(desktopNotifier);
        outputs.add(audioAlert);
        outputs.add(webhookSender);

        log.info("Alert outputs: console={}, desktop={}, audio={}, webhook={}",
            consoleLogger.isEnabled(),
            desktopNotifier.isEnabled(),
            audioAlert.isEnabled(),
            webhookSender.isEnabled()
        );
    }

    /**
     * Dispatch a signal to all enabled outputs.
     */
    public void dispatch(SignalEvent signal) {
        log.debug("Dispatching signal: {}", signal.toSummary());

        for (AlertOutput output : outputs) {
            if (output.isEnabled()) {
                try {
                    output.send(signal);
                } catch (Exception e) {
                    log.error("Alert output {} failed: {}", output.getName(), e.getMessage());
                }
            }
        }
    }

    /**
     * Update configuration.
     */
    public void updateConfig(DeskConfig config) {
        DeskConfig.AlertSettings alerts = config.getAlerts();
        consoleLogger.setEnabled(alerts.isConsole());
        desktopNotifier.setEnabled(alerts.isDesktop());
        audioAlert.setEnabled(alerts.isAudio());
        webhookSender.setEnabled(alerts.getWebhook().isEnabled());
        webhookSender.setUrl(alerts.getWebhook().getUrl());
    }

    /**
     * Get all outputs.
     */
    public List<AlertOutput> getOutputs() {
        return outputs;
    }

    /**
     * Shutdown resources.
     */
    public void shutdown() {
        webhookSender.shutdown();
    }
}
