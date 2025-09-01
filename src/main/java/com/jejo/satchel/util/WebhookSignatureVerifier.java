package com.jejo.satchel.util;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class WebhookSignatureVerifier {

    // Example public key for Sandbox (replace with the correct key for your environment)
    private static final String FIREBLOCKS_PUBLIC_KEY = """
        -----BEGIN PUBLIC KEY-----
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAw+fZuC+0vDYTf8fYnCN6
        71iHg98lPHBmafmqZqb+TUexn9sH6qNIBZ5SgYFxFK6dYXIuJ5uoORzihREvZVZP
        8DphdeKOMUrMr6b+Cchb2qS8qz8WS7xtyLU9GnBn6M5mWfjkjQr1jbilH15Zvcpz
        ECC8aPUAy2EbHpnr10if2IHkIAWLYD+0khpCjpWtsfuX+LxqzlqQVW9xc6z7tshK
        eCSEa6Oh8+ia7Zlu0b+2xmy2Arb6xGl+s+Rnof4lsq9tZS6f03huc+XVTmd6H2We
        WxFMfGyDCX2akEg2aAvx7231/6S0vBFGiX0C+3GbXlieHDplLGoODHUt5hxbPJnK
        IwIDAQAB
        -----END PUBLIC KEY-----
        """;

    public boolean verifySignature(String signature, String payload) {
        try {
            // Clean and decode the public key
            String cleanedPublicKey = FIREBLOCKS_PUBLIC_KEY
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
            byte[] publicKeyBytes = Base64.getDecoder().decode(cleanedPublicKey);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            // Initialize the signature verifier
            Signature verifier = Signature.getInstance("SHA512withRSA");
            verifier.initVerify(publicKey);
            verifier.update(payload.getBytes("UTF-8"));

            // Decode the signature and verify
            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            return verifier.verify(signatureBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}