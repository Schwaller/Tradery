package com.tradery.sharing.identity;

import com.sun.net.httpserver.HttpServer;
import okhttp3.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handles OAuth2 Authorization Code + PKCE flow against Keycloak.
 * Opens the system browser for login, listens for the callback on a local HTTP server.
 */
public class OAuthLogin {

    private static final Logger log = LoggerFactory.getLogger(OAuthLogin.class);

    private static final String AUTH_SERVER = "https://plaiiin.com/auth/realms/Plaiiin/protocol/openid-connect";
    private static final String CLIENT_ID = "plaiiin-frontend";
    private static final String SCOPE = "openid email profile";
    private static final int LOGIN_TIMEOUT_SECONDS = 120;

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    private static final ObjectMapper json = new ObjectMapper();

    public record TokenResponse(String accessToken, String refreshToken, String idToken, int expiresIn) {}
    public record UserInfo(String userId, String email, String displayName) {}

    /**
     * Perform interactive browser-based login.
     * Opens the system browser, waits for callback, exchanges code for tokens.
     * @return TokenResponse on success, null if timed out or cancelled
     */
    public TokenResponse login() {
        try {
            // Generate PKCE values
            String codeVerifier = generateCodeVerifier();
            String codeChallenge = generateCodeChallenge(codeVerifier);
            String state = generateState();

            // Start local callback server
            CompletableFuture<String> codeFuture = new CompletableFuture<>();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            int port = server.getAddress().getPort();
            String redirectUri = "http://localhost:" + port + "/callback";

            server.createContext("/callback", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                String code = extractParam(query, "code");
                String returnedState = extractParam(query, "state");
                String error = extractParam(query, "error");

                String responseHtml;
                int statusCode;

                if (error != null) {
                    responseHtml = callbackPage(false, error);
                    statusCode = 400;
                    codeFuture.completeExceptionally(new IllegalStateException("OAuth error: " + error));
                } else if (code == null || !state.equals(returnedState)) {
                    responseHtml = callbackPage(false, "Invalid response");
                    statusCode = 400;
                    codeFuture.completeExceptionally(new IllegalStateException("Invalid callback: missing code or state mismatch"));
                } else {
                    responseHtml = callbackPage(true, null);
                    statusCode = 200;
                    codeFuture.complete(code);
                }

                byte[] responseBytes = responseHtml.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(statusCode, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            });

            server.start();
            log.info("OAuth callback server started on port {}", port);

            // Build authorization URL and open browser
            String authUrl = AUTH_SERVER + "/auth"
                    + "?client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
                    + "&response_type=code"
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                    + "&code_challenge=" + URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8)
                    + "&code_challenge_method=S256"
                    + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
                    + "&scope=" + URLEncoder.encode(SCOPE, StandardCharsets.UTF_8);

            Desktop.getDesktop().browse(URI.create(authUrl));
            log.info("Opened browser for Keycloak login");

            // Wait for the callback
            try {
                String code = codeFuture.get(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return exchangeCodeForTokens(code, redirectUri, codeVerifier);
            } catch (TimeoutException e) {
                log.warn("Login timed out after {} seconds", LOGIN_TIMEOUT_SECONDS);
                return null;
            } finally {
                server.stop(2); // delay 2s so the browser finishes rendering the callback page
            }
        } catch (Exception e) {
            log.error("OAuth login failed", e);
            return null;
        }
    }

    /**
     * Silent token refresh using a stored refresh token.
     * @return new TokenResponse, or null if refresh failed
     */
    public TokenResponse refresh(String refreshToken) {
        try {
            RequestBody body = new FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("client_id", CLIENT_ID)
                    .add("refresh_token", refreshToken)
                    .build();

            Request request = new Request.Builder()
                    .url(AUTH_SERVER + "/token")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("Token refresh failed with status {}", response.code());
                    return null;
                }
                return parseTokenResponse(response.body().string());
            }
        } catch (Exception e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse the id_token JWT to extract user info (no signature verification —
     * the token was received directly from Keycloak over HTTPS).
     */
    public UserInfo parseIdToken(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) throw new IllegalArgumentException("Invalid JWT");
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode claims = json.readTree(payload);
            String sub = claims.has("sub") ? claims.get("sub").asText() : null;
            String email = claims.has("email") ? claims.get("email").asText() : sub;
            String name = claims.has("name") ? claims.get("name").asText()
                    : claims.has("preferred_username") ? claims.get("preferred_username").asText() : email;
            return new UserInfo(sub, email, name);
        } catch (Exception e) {
            log.error("Failed to parse id_token", e);
            throw new IllegalStateException("Failed to parse id_token", e);
        }
    }

    private TokenResponse exchangeCodeForTokens(String code, String redirectUri, String codeVerifier) throws IOException {
        RequestBody body = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", CLIENT_ID)
                .add("code", code)
                .add("redirect_uri", redirectUri)
                .add("code_verifier", codeVerifier)
                .build();

        Request request = new Request.Builder()
                .url(AUTH_SERVER + "/token")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Token exchange failed (HTTP " + response.code() + "): " + responseBody);
            }
            return parseTokenResponse(response.body().string());
        }
    }

    private TokenResponse parseTokenResponse(String responseBody) throws IOException {
        JsonNode node = json.readTree(responseBody);
        return new TokenResponse(
                node.get("access_token").asText(),
                node.has("refresh_token") ? node.get("refresh_token").asText() : null,
                node.has("id_token") ? node.get("id_token").asText() : null,
                node.has("expires_in") ? node.get("expires_in").asInt() : 300
        );
    }

    private static String generateCodeVerifier() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String generateState() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final String CALLBACK_TEMPLATE;

    static {
        try (var is = OAuthLogin.class.getResourceAsStream("oauth-callback.html")) {
            CALLBACK_TEMPLATE = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static String callbackPage(boolean success, String error) {
        String icon = success
                ? "<svg width='64' height='64' viewBox='0 0 24 24' fill='none' stroke='#22c55e' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='10'/><path d='M8 12l3 3 5-5'/></svg>"
                : "<svg width='64' height='64' viewBox='0 0 24 24' fill='none' stroke='#ef4444' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='10'/><path d='M15 9l-6 6M9 9l6 6'/></svg>";
        String title = success ? "You're signed in" : "Sign in failed";
        String subtitle = success
                ? "You can close this tab and return to Plaiiin."
                : (error != null ? error : "Something went wrong.") + " Please try again.";
        String autoClose = success ? "<script>setTimeout(function(){window.close()},3000)</script>" : "";

        return CALLBACK_TEMPLATE
                .replace("{{ICON}}", icon)
                .replace("{{TITLE}}", title)
                .replace("{{SUBTITLE}}", subtitle)
                .replace("{{AUTO_CLOSE}}", autoClose);
    }

    private static String extractParam(String query, String name) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
