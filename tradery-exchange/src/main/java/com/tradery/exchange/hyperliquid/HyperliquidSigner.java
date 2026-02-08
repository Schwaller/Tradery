package com.tradery.exchange.hyperliquid;

import com.tradery.exchange.exception.AuthenticationException;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * EIP-712 typed data signing for Hyperliquid exchange API.
 * Uses secp256k1 ECDSA via BouncyCastle.
 */
public class HyperliquidSigner {

    private static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
    private static final ECDomainParameters DOMAIN = new ECDomainParameters(
            CURVE_PARAMS.getCurve(), CURVE_PARAMS.getG(), CURVE_PARAMS.getN(), CURVE_PARAMS.getH());

    // EIP-712 domain separator for Hyperliquid
    // keccak256("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)")
    private static final byte[] DOMAIN_SEPARATOR_TYPEHASH = keccak256(
            "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)".getBytes(StandardCharsets.UTF_8));

    // Hyperliquid mainnet chain ID = 1337
    private static final long MAINNET_CHAIN_ID = 1337;
    // Hyperliquid testnet chain ID = 421614
    private static final long TESTNET_CHAIN_ID = 421614;

    // Agent typehash: keccak256("Agent(address source,address connectionId)")
    // For L1 actions, Hyperliquid uses a phantom agent pattern
    private static final byte[] AGENT_TYPEHASH = keccak256(
            "Agent(address source,address connectionId)".getBytes(StandardCharsets.UTF_8));

    private final BigInteger privateKey;
    private final String address;
    private final boolean testnet;

    public HyperliquidSigner(String privateKeyHex, boolean testnet) throws AuthenticationException {
        try {
            String cleaned = privateKeyHex.startsWith("0x") ? privateKeyHex.substring(2) : privateKeyHex;
            this.privateKey = new BigInteger(cleaned, 16);
            this.address = deriveAddress(this.privateKey);
            this.testnet = testnet;
        } catch (Exception e) {
            throw new AuthenticationException("Invalid private key: " + e.getMessage(), e);
        }
    }

    public HyperliquidSigner(String privateKeyHex, String address, boolean testnet) throws AuthenticationException {
        try {
            String cleaned = privateKeyHex.startsWith("0x") ? privateKeyHex.substring(2) : privateKeyHex;
            this.privateKey = new BigInteger(cleaned, 16);
            this.address = address.toLowerCase();
            this.testnet = testnet;
        } catch (Exception e) {
            throw new AuthenticationException("Invalid private key: " + e.getMessage(), e);
        }
    }

    public String getAddress() {
        return address;
    }

    /**
     * Sign an action hash using EIP-712 structured data signing.
     * Returns the signature as {r, s, v} concatenated hex string.
     */
    public String signL1Action(byte[] connectionId, long nonce, long vaultAddress) {
        // Build the EIP-712 struct hash for the action
        byte[] domainSeparator = buildDomainSeparator();

        // Encode the agent struct: Agent(source=connectionId, connectionId=address)
        // The phantom agent pattern uses source + connectionId
        byte[] structHash = buildActionStructHash(connectionId);

        // EIP-712: keccak256("\x19\x01" || domainSeparator || structHash)
        byte[] digest = new byte[2 + 32 + 32];
        digest[0] = 0x19;
        digest[1] = 0x01;
        System.arraycopy(domainSeparator, 0, digest, 2, 32);
        System.arraycopy(structHash, 0, digest, 34, 32);

        byte[] hash = keccak256(digest);
        return sign(hash);
    }

    /**
     * Sign a raw hash (already keccak256'd).
     */
    public String sign(byte[] hash) {
        ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
        ECPrivateKeyParameters privParams = new ECPrivateKeyParameters(privateKey, DOMAIN);
        signer.init(true, privParams);

        BigInteger[] sig = signer.generateSignature(hash);
        BigInteger r = sig[0];
        BigInteger s = sig[1];

        // Normalize s to lower half of curve order (EIP-2)
        BigInteger halfN = DOMAIN.getN().shiftRight(1);
        if (s.compareTo(halfN) > 0) {
            s = DOMAIN.getN().subtract(s);
        }

        // Calculate recovery id (v)
        int v = calculateRecoveryId(hash, r, s);

        // Encode as 65-byte signature: r (32) + s (32) + v (1)
        byte[] rBytes = toBytes32(r);
        byte[] sBytes = toBytes32(s);
        byte[] sigBytes = new byte[65];
        System.arraycopy(rBytes, 0, sigBytes, 0, 32);
        System.arraycopy(sBytes, 0, sigBytes, 32, 32);
        sigBytes[64] = (byte) (v + 27); // Ethereum uses v = 27 or 28

        return "0x" + bytesToHex(sigBytes);
    }

    private byte[] buildDomainSeparator() {
        long chainId = testnet ? TESTNET_CHAIN_ID : MAINNET_CHAIN_ID;
        // Encode: keccak256(abi.encode(DOMAIN_SEPARATOR_TYPEHASH, keccak256("Exchange"), keccak256("1"), chainId, 0x0))
        byte[] nameHash = keccak256("Exchange".getBytes(StandardCharsets.UTF_8));
        byte[] versionHash = keccak256("1".getBytes(StandardCharsets.UTF_8));
        byte[] chainIdBytes = toBytes32(BigInteger.valueOf(chainId));
        byte[] verifyingContract = new byte[32]; // address(0)

        byte[] encoded = new byte[32 * 5];
        System.arraycopy(DOMAIN_SEPARATOR_TYPEHASH, 0, encoded, 0, 32);
        System.arraycopy(nameHash, 0, encoded, 32, 32);
        System.arraycopy(versionHash, 0, encoded, 64, 32);
        System.arraycopy(chainIdBytes, 0, encoded, 96, 32);
        System.arraycopy(verifyingContract, 0, encoded, 128, 32);

        return keccak256(encoded);
    }

    private byte[] buildActionStructHash(byte[] connectionId) {
        // Agent(source, connectionId) where source is the signing address padded to 32 bytes
        byte[] sourceBytes = new byte[32];
        byte[] addrBytes = hexToBytes(address.startsWith("0x") ? address.substring(2) : address);
        System.arraycopy(addrBytes, 0, sourceBytes, 32 - addrBytes.length, addrBytes.length);

        byte[] connIdPadded = new byte[32];
        if (connectionId != null) {
            int offset = Math.max(0, 32 - connectionId.length);
            int len = Math.min(connectionId.length, 32);
            System.arraycopy(connectionId, connectionId.length - len, connIdPadded, offset, len);
        }

        byte[] encoded = new byte[32 * 3];
        System.arraycopy(AGENT_TYPEHASH, 0, encoded, 0, 32);
        System.arraycopy(sourceBytes, 0, encoded, 32, 32);
        System.arraycopy(connIdPadded, 0, encoded, 64, 32);

        return keccak256(encoded);
    }

    private int calculateRecoveryId(byte[] hash, BigInteger r, BigInteger s) {
        ECPoint pubKey = new FixedPointCombMultiplier().multiply(DOMAIN.getG(), privateKey);
        byte[] pubKeyEncoded = pubKey.getEncoded(false);

        for (int v = 0; v < 4; v++) {
            try {
                ECPoint recovered = recoverPublicKey(hash, r, s, v);
                if (recovered != null && Arrays.equals(recovered.getEncoded(false), pubKeyEncoded)) {
                    return v;
                }
            } catch (Exception ignored) {
                // Try next v
            }
        }
        return 0;
    }

    private ECPoint recoverPublicKey(byte[] hash, BigInteger r, BigInteger s, int recId) {
        BigInteger n = DOMAIN.getN();
        BigInteger i = BigInteger.valueOf(recId / 2);
        BigInteger x = r.add(i.multiply(n));

        if (x.compareTo(CURVE_PARAMS.getCurve().getField().getCharacteristic()) >= 0) {
            return null;
        }

        ECPoint R = decompressKey(x, (recId & 1) == 1);
        if (!R.multiply(n).isInfinity()) {
            return null;
        }

        BigInteger e = new BigInteger(1, hash);
        BigInteger eInv = BigInteger.ZERO.subtract(e).mod(n);
        BigInteger rInv = r.modInverse(n);
        BigInteger srInv = rInv.multiply(s).mod(n);
        BigInteger eInvrInv = rInv.multiply(eInv).mod(n);

        return DOMAIN.getG().multiply(eInvrInv).add(R.multiply(srInv));
    }

    private ECPoint decompressKey(BigInteger xBN, boolean yBit) {
        byte[] compEnc = new byte[33];
        compEnc[0] = (byte) (yBit ? 0x03 : 0x02);
        byte[] xBytes = toBytes32(xBN);
        System.arraycopy(xBytes, 0, compEnc, 1, 32);
        return CURVE_PARAMS.getCurve().decodePoint(compEnc);
    }

    private static String deriveAddress(BigInteger privateKey) {
        ECPoint pubKey = new FixedPointCombMultiplier().multiply(DOMAIN.getG(), privateKey);
        byte[] pubKeyEncoded = pubKey.getEncoded(false);
        // Skip the 0x04 prefix byte
        byte[] pubKeyNoPrefix = new byte[64];
        System.arraycopy(pubKeyEncoded, 1, pubKeyNoPrefix, 0, 64);
        byte[] hash = keccak256(pubKeyNoPrefix);
        // Address is last 20 bytes of keccak256(pubKey)
        byte[] addressBytes = new byte[20];
        System.arraycopy(hash, 12, addressBytes, 0, 20);
        return "0x" + bytesToHex(addressBytes);
    }

    static byte[] keccak256(byte[] input) {
        try {
            org.bouncycastle.jcajce.provider.digest.Keccak.DigestKeccak256 digest =
                    new org.bouncycastle.jcajce.provider.digest.Keccak.DigestKeccak256();
            return digest.digest(input);
        } catch (Exception e) {
            throw new RuntimeException("Keccak-256 not available", e);
        }
    }

    private static byte[] toBytes32(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] result = new byte[32];
        if (raw.length <= 32) {
            System.arraycopy(raw, 0, result, 32 - raw.length, raw.length);
        } else {
            // Strip leading zero byte
            System.arraycopy(raw, raw.length - 32, result, 0, 32);
        }
        return result;
    }

    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    static byte[] hexToBytes(String hex) {
        String cleaned = hex.startsWith("0x") ? hex.substring(2) : hex;
        int len = cleaned.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(cleaned.charAt(i), 16) << 4)
                    + Character.digit(cleaned.charAt(i + 1), 16));
        }
        return data;
    }
}
