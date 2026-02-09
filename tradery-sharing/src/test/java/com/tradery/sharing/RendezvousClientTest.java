package com.tradery.sharing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.rendezvous.BackendKeyStore;
import com.tradery.rendezvous.KeycloakValidator;
import com.tradery.rendezvous.RendezvousServer;
import com.tradery.sharing.discovery.RendezvousClient;
import com.tradery.sharing.discovery.RendezvousClient.CredentialRejectedException;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests RendezvousClient error handling, specifically that 401/403 responses
 * from a rotated backend key throw CredentialRejectedException instead of
 * being silently swallowed.
 */
class RendezvousClientTest {

    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void announceThrowsCredentialRejectedOnKeyRotation() throws Exception {
        Path keyDir1 = Files.createTempDirectory("rdv-client-test-1-");
        Path keyDir2 = Files.createTempDirectory("rdv-client-test-2-");

        try {
            // Server 1: enroll and get credential
            BackendKeyStore keyStore1 = new BackendKeyStore(keyDir1);
            RendezvousServer server1 = new RendezvousServer(0, keyStore1,
                    token -> new KeycloakValidator.UserIdentity(token, token));
            String url1 = "http://localhost:" + server1.port();

            RendezvousClient client = new RendezvousClient(url1, http, mapper);
            var enrolled = client.enrollDevice("test-user", "dummyKey", "test");
            String credential = enrolled.deviceCredential();

            // Announce works with server 1
            assertDoesNotThrow(() ->
                    client.announce(credential, "peer-1", 9000, List.of("doc-1")));

            server1.stop();

            // Server 2: different key
            BackendKeyStore keyStore2 = new BackendKeyStore(keyDir2);
            RendezvousServer server2 = new RendezvousServer(0, keyStore2,
                    token -> new KeycloakValidator.UserIdentity(token, token));
            String url2 = "http://localhost:" + server2.port();

            RendezvousClient client2 = new RendezvousClient(url2, http, mapper);

            try {
                // Announce with old credential should throw CredentialRejectedException
                assertThrows(CredentialRejectedException.class, () ->
                        client2.announce(credential, "peer-1", 9000, List.of("doc-1")),
                        "announce() should throw CredentialRejectedException on 401");
            } finally {
                server2.stop();
            }
        } finally {
            deleteKeyDir(keyDir1);
            deleteKeyDir(keyDir2);
        }
    }

    @Test
    void discoverThrowsCredentialRejectedOnKeyRotation() throws Exception {
        Path keyDir1 = Files.createTempDirectory("rdv-client-test-3-");
        Path keyDir2 = Files.createTempDirectory("rdv-client-test-4-");

        try {
            // Server 1: enroll
            BackendKeyStore keyStore1 = new BackendKeyStore(keyDir1);
            RendezvousServer server1 = new RendezvousServer(0, keyStore1,
                    token -> new KeycloakValidator.UserIdentity(token, token));

            RendezvousClient client = new RendezvousClient(
                    "http://localhost:" + server1.port(), http, mapper);
            String credential = client.enrollDevice("test-user", "dummyKey", "test")
                    .deviceCredential();

            // Discover works with server 1
            assertDoesNotThrow(() -> client.discoverPeers(credential, "doc-1"));

            server1.stop();

            // Server 2: different key
            BackendKeyStore keyStore2 = new BackendKeyStore(keyDir2);
            RendezvousServer server2 = new RendezvousServer(0, keyStore2,
                    token -> new KeycloakValidator.UserIdentity(token, token));

            RendezvousClient client2 = new RendezvousClient(
                    "http://localhost:" + server2.port(), http, mapper);

            try {
                // Discover with old credential should throw CredentialRejectedException
                assertThrows(CredentialRejectedException.class, () ->
                        client2.discoverPeers(credential, "doc-1"),
                        "discoverPeers() should throw CredentialRejectedException on 401");
            } finally {
                server2.stop();
            }
        } finally {
            deleteKeyDir(keyDir1);
            deleteKeyDir(keyDir2);
        }
    }

    @Test
    void reEnrollmentAfterKeyRotationProducesValidCredential() throws Exception {
        Path keyDir1 = Files.createTempDirectory("rdv-client-test-5-");
        Path keyDir2 = Files.createTempDirectory("rdv-client-test-6-");

        try {
            // Server 1: enroll
            BackendKeyStore keyStore1 = new BackendKeyStore(keyDir1);
            RendezvousServer server1 = new RendezvousServer(0, keyStore1,
                    token -> new KeycloakValidator.UserIdentity(token, token));

            RendezvousClient client1 = new RendezvousClient(
                    "http://localhost:" + server1.port(), http, mapper);
            String oldCredential = client1.enrollDevice("test-user", "dummyKey", "test")
                    .deviceCredential();

            server1.stop();

            // Server 2: different key — old credential is stale
            BackendKeyStore keyStore2 = new BackendKeyStore(keyDir2);
            RendezvousServer server2 = new RendezvousServer(0, keyStore2,
                    token -> new KeycloakValidator.UserIdentity(token, token));

            RendezvousClient client2 = new RendezvousClient(
                    "http://localhost:" + server2.port(), http, mapper);

            try {
                // Old credential should be rejected
                assertThrows(CredentialRejectedException.class, () ->
                        client2.announce(oldCredential, "peer-1", 9000, List.of("doc-1")));

                // Re-enroll with server 2
                String newCredential = client2.enrollDevice("test-user", "dummyKey", "test")
                        .deviceCredential();
                assertNotNull(newCredential);
                assertNotEquals(oldCredential, newCredential);

                // New credential works
                assertDoesNotThrow(() ->
                        client2.announce(newCredential, "peer-1", 9000, List.of("doc-1")),
                        "Fresh credential should work after re-enrollment");
                assertDoesNotThrow(() ->
                        client2.discoverPeers(newCredential, "doc-1"),
                        "Fresh credential should work for discover after re-enrollment");
            } finally {
                server2.stop();
            }
        } finally {
            deleteKeyDir(keyDir1);
            deleteKeyDir(keyDir2);
        }
    }

    private static void deleteKeyDir(Path dir) {
        try {
            Files.deleteIfExists(dir.resolve("backend.key"));
            Files.deleteIfExists(dir.resolve("backend.pub"));
            Files.deleteIfExists(dir);
        } catch (Exception e) {
            // test cleanup, ignore
        }
    }
}
