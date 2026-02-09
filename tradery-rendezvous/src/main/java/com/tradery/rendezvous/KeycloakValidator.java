package com.tradery.rendezvous;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Validates Keycloak access tokens during device enrollment.
 * Production impl calls the Keycloak userinfo endpoint.
 */
@FunctionalInterface
public interface KeycloakValidator {

    record UserIdentity(String userId, String email) {}

    /** Validate a Keycloak access token and return the user identity, or null if invalid. */
    UserIdentity validate(String accessToken);

    /**
     * Production validator that calls Keycloak's userinfo endpoint.
     * Expected to receive the raw access token (not "Bearer " prefixed).
     */
    static KeycloakValidator keycloak(String issuerUrl, String realm) {
        Logger log = LoggerFactory.getLogger(KeycloakValidator.class);
        String userinfoUrl = issuerUrl + "/realms/" + realm + "/protocol/openid-connect/userinfo";
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        return accessToken -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(userinfoUrl))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    log.warn("Keycloak userinfo returned {}", resp.statusCode());
                    return null;
                }
                JsonNode json = mapper.readTree(resp.body());
                String sub = json.has("sub") ? json.get("sub").asText() : null;
                String email = json.has("email") ? json.get("email").asText() : sub;
                if (sub == null) return null;
                return new UserIdentity(sub, email);
            } catch (Exception e) {
                log.warn("Keycloak validation failed: {}", e.getMessage());
                return null;
            }
        };
    }
}
