package com.tradery.sharing;

import com.tradery.news.ui.coin.FactStore;
import com.tradery.sharing.identity.KeyPairStore;
import com.tradery.sharing.sync.FactSigner;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.*;

class FactSignerTest {

    private static FactStore.Fact testFact() {
        return new FactStore.Fact(
                "fact-001", "entity-1", "name", "Bitcoin",
                "user", "peer-A", 42, 1700000000000L, "commit-1"
        );
    }

    @Test
    void signAndVerify_validFact_succeeds() throws GeneralSecurityException {
        KeyPair kp = FactSigner.generateKeyPair();
        FactSigner signer = new FactSigner(kp);

        FactStore.Fact fact = testFact();
        String signature = signer.sign(fact);

        assertTrue(FactSigner.verify(fact, signature, kp.getPublic()));
    }

    @Test
    void verify_tamperedFact_fails() throws GeneralSecurityException {
        KeyPair kp = FactSigner.generateKeyPair();
        FactSigner signer = new FactSigner(kp);

        FactStore.Fact original = testFact();
        String signature = signer.sign(original);

        // Tamper: change value
        FactStore.Fact tampered = new FactStore.Fact(
                original.id(), original.entityId(), original.attribute(), "Ethereum",
                original.source(), original.peerId(), original.lclock(), original.wallClock(),
                original.commitId()
        );

        assertFalse(FactSigner.verify(tampered, signature, kp.getPublic()));
    }

    @Test
    void verify_wrongKey_fails() throws GeneralSecurityException {
        KeyPair kpA = FactSigner.generateKeyPair();
        KeyPair kpB = FactSigner.generateKeyPair();
        FactSigner signerA = new FactSigner(kpA);

        FactStore.Fact fact = testFact();
        String signature = signerA.sign(fact);

        assertFalse(FactSigner.verify(fact, signature, kpB.getPublic()));
    }

    @Test
    void signFact_withNullFields_succeeds() throws GeneralSecurityException {
        KeyPair kp = FactSigner.generateKeyPair();
        FactSigner signer = new FactSigner(kp);

        FactStore.Fact fact = new FactStore.Fact(
                "fact-002", "entity-1", "description", null,
                "user", "peer-A", 1, 1700000000000L, null
        );

        String signature = signer.sign(fact);
        assertNotNull(signature);
        assertTrue(FactSigner.verify(fact, signature, kp.getPublic()));
    }

    @Test
    void publicKeyBase64_roundTrips() throws GeneralSecurityException {
        KeyPair kp = FactSigner.generateKeyPair();
        FactSigner signer = new FactSigner(kp);

        String base64 = signer.publicKeyBase64();
        assertNotNull(base64);
        assertFalse(base64.isEmpty());

        PublicKey decoded = KeyPairStore.decodePublicKey(base64);
        assertEquals(kp.getPublic(), decoded);
    }
}
