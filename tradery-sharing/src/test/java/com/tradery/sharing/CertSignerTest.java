package com.tradery.sharing;

import com.tradery.news.ui.FriendshipCertData;
import com.tradery.sharing.identity.CertSigner;
import com.tradery.sharing.identity.IdentityCert;
import com.tradery.sharing.sync.FactSigner;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

class CertSignerTest {

    @Test
    void createAndVerifyIdentityCert_succeeds() throws GeneralSecurityException {
        KeyPair kp = FactSigner.generateKeyPair();
        CertSigner signer = new CertSigner(kp);

        IdentityCert cert = signer.createIdentityCert("alice@example.com");

        assertEquals("alice@example.com", cert.email());
        assertEquals(signer.publicKeyBase64(), cert.publicKey());
        assertNotNull(cert.signature());
        assertTrue(cert.issuedAt() > 0);

        assertTrue(CertSigner.verifyIdentityCert(cert));
    }

    @Test
    void verifyIdentityCert_tampered_fails() throws GeneralSecurityException {
        KeyPair kp = FactSigner.generateKeyPair();
        CertSigner signer = new CertSigner(kp);

        IdentityCert original = signer.createIdentityCert("alice@example.com");

        // Tamper: change email
        IdentityCert tampered = new IdentityCert("eve@example.com", original.publicKey(),
                original.issuedAt(), original.signature());

        assertFalse(CertSigner.verifyIdentityCert(tampered));
    }

    @Test
    void verifyIdentityCert_wrongKey_fails() throws GeneralSecurityException {
        KeyPair kpA = FactSigner.generateKeyPair();
        KeyPair kpB = FactSigner.generateKeyPair();
        CertSigner signerA = new CertSigner(kpA);
        CertSigner signerB = new CertSigner(kpB);

        IdentityCert cert = signerA.createIdentityCert("alice@example.com");

        // Replace public key with B's key (signature was made by A)
        IdentityCert forged = new IdentityCert(cert.email(), signerB.publicKeyBase64(),
                cert.issuedAt(), cert.signature());

        assertFalse(CertSigner.verifyIdentityCert(forged));
    }

    @Test
    void createAndVerifyFriendshipCert_succeeds() throws GeneralSecurityException {
        KeyPair kp = FactSigner.generateKeyPair();
        CertSigner signer = new CertSigner(kp);

        FriendshipCertData cert = signer.createFriendshipCert("alice@example.com", "bob@example.com");

        assertEquals("alice@example.com", cert.issuerEmail());
        assertEquals(signer.publicKeyBase64(), cert.issuerPublicKey());
        assertEquals("bob@example.com", cert.subjectEmail());
        assertNotNull(cert.signature());

        assertTrue(CertSigner.verifyFriendshipCert(cert));
    }

    @Test
    void verifyFriendshipCert_tampered_fails() throws GeneralSecurityException {
        KeyPair kp = FactSigner.generateKeyPair();
        CertSigner signer = new CertSigner(kp);

        FriendshipCertData original = signer.createFriendshipCert("alice@example.com", "bob@example.com");

        // Tamper: change subject
        FriendshipCertData tampered = new FriendshipCertData(
                original.issuerEmail(), original.issuerPublicKey(),
                "eve@example.com", original.issuedAt(), original.signature());

        assertFalse(CertSigner.verifyFriendshipCert(tampered));
    }

    @Test
    void verifyFriendshipCert_wrongKey_fails() throws GeneralSecurityException {
        KeyPair kpA = FactSigner.generateKeyPair();
        KeyPair kpB = FactSigner.generateKeyPair();
        CertSigner signerA = new CertSigner(kpA);
        CertSigner signerB = new CertSigner(kpB);

        FriendshipCertData cert = signerA.createFriendshipCert("alice@example.com", "bob@example.com");

        // Replace issuer public key with B's (signature was made by A)
        FriendshipCertData forged = new FriendshipCertData(
                cert.issuerEmail(), signerB.publicKeyBase64(),
                cert.subjectEmail(), cert.issuedAt(), cert.signature());

        assertFalse(CertSigner.verifyFriendshipCert(forged));
    }
}
