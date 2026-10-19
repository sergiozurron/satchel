package com.jejo.satchel.service;

import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.HexFormat;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import io.jsonwebtoken.Jwts;

final class CoinbaseJwt {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final long TOKEN_LIFETIME_MS = 120_000L;

	private final String keyName;
	private final ECPrivateKey privateKey;

	CoinbaseJwt(String keyName, String pem) {
		if (keyName == null || keyName.isBlank()) {
			throw new IllegalArgumentException("Coinbase API key name is required");
		}
		this.keyName = keyName;
		this.privateKey = readKey(pem);
	}

	String sign(String method, String host, String path) {
		Date now = new Date();
		String uri = method + " " + host + path;
		String nonce = HexFormat.of().formatHex(RANDOM.generateSeed(16));
		return Jwts.builder()
				.header().keyId(keyName).add("nonce", nonce).and()
				.issuer("cdp")
				.subject(keyName)
				.claim("uri", uri)
				.notBefore(now)
				.expiration(new Date(now.getTime() + TOKEN_LIFETIME_MS))
				.signWith(privateKey, Jwts.SIG.ES256)
				.compact();
	}

	private static ECPrivateKey readKey(String pem) {
		if (pem == null || pem.isBlank()) {
			throw new IllegalArgumentException("Coinbase API secret is required");
		}
		String normalized = pem.trim().replace("\\n", "\n");
		try {
			if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
				Security.addProvider(new BouncyCastleProvider());
			}
			PrivateKey parsed = parsePem(normalized);
			KeyFactory keyFactory = KeyFactory.getInstance("EC");
			return (ECPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(parsed.getEncoded()));
		} catch (IOException | GeneralSecurityException | IllegalArgumentException ex) {
			throw new IllegalArgumentException("Coinbase API secret could not be read as an EC private key", ex);
		}
	}

	private static PrivateKey parsePem(String pem) throws IOException {
		try (PEMParser parser = new PEMParser(new StringReader(pem))) {
			Object object = parser.readObject();
			JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
			if (object instanceof PEMKeyPair pair) {
				return converter.getPrivateKey(pair.getPrivateKeyInfo());
			}
			if (object instanceof PrivateKeyInfo info) {
				return converter.getPrivateKey(info);
			}
			if (object instanceof PrivateKey key) {
				return key;
			}
			throw new IllegalArgumentException("Unexpected Coinbase private key format");
		}
	}

}
