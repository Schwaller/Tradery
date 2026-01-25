package com.tradery.desk.alert;

import com.tradery.desk.signal.SignalEvent;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Webhook alert sender - POSTs signal data as JSON to configured URL.
 */
public class WebhookSender implements AlertOutput {

    private static final Logger log = LoggerFactory.getLogger(WebhookSender.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private String url;
    private boolean enabled = false;

    public WebhookSender() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    }

    public WebhookSender(String url, boolean enabled) {
        this();
        this.url = url;
        this.enabled = enabled;
    }

    @Override
    public void send(SignalEvent signal) {
        if (!enabled || url == null || url.isBlank()) {
            return;
        }

        String json = signal.toJson();
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "TraderyDesk/1.0")
            .build();

        // Send asynchronously to not block
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Webhook failed: {}", e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    log.debug("Webhook sent: {} -> {}", signal.strategyId(), response.code());
                } else {
                    log.warn("Webhook error: {} -> {}", signal.strategyId(), response.code());
                }
                response.close();
            }
        });
    }

    @Override
    public String getName() {
        return "Webhook";
    }

    @Override
    public boolean isEnabled() {
        return enabled && url != null && !url.isBlank();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Shutdown the HTTP client.
     */
    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
