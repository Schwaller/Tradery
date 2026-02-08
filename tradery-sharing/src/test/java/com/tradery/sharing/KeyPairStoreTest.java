package com.tradery.sharing;

import com.tradery.sharing.identity.KeyPairStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

class KeyPairStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void loadOrGenerate_firstCall_generatesKeys() throws Exception {
        KeyPairStore store = new KeyPairStore(tempDir);
        KeyPair kp = store.loadOrGenerate();

        assertNotNull(kp);
        assertNotNull(kp.getPublic());
        assertNotNull(kp.getPrivate());
        assertTrue(kp.getPublic().getAlgorithm().contains("Ed"),
                "Expected Ed25519/EdDSA algorithm, got: " + kp.getPublic().getAlgorithm());
    }

    @Test
    void loadOrGenerate_secondCall_returnsSameKeys() throws Exception {
        KeyPairStore store = new KeyPairStore(tempDir);
        KeyPair first = store.loadOrGenerate();
        KeyPair second = store.loadOrGenerate();

        assertEquals(first.getPublic(), second.getPublic());
        assertEquals(first.getPrivate(), second.getPrivate());
    }

    @Test
    void newStoreInstance_reloadsSameKeys() throws Exception {
        KeyPairStore store1 = new KeyPairStore(tempDir);
        KeyPair original = store1.loadOrGenerate();

        KeyPairStore store2 = new KeyPairStore(tempDir);
        KeyPair reloaded = store2.loadOrGenerate();

        assertEquals(original.getPublic(), reloaded.getPublic());
        assertEquals(original.getPrivate(), reloaded.getPrivate());
    }
}
