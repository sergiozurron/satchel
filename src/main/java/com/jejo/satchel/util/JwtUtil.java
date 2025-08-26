package com.jejo.satchel.util;

import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

	@Value("${jwt.secret}")
	private String secretKey;
	@Value("${jwt.expiration-time-ms}")
	private long jwtExpiration;

	public String generateToken(UserDetails user) {
		return Jwts.builder().subject(user.getUsername()) // Set the username as the subject
				.claim("roles", user.getAuthorities()) // Add user roles/authorities
				.issuedAt(new Date()) // Token issuance time
				.expiration(new Date(System.currentTimeMillis() + jwtExpiration)) // Token expiration
				.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey)), Jwts.SIG.HS256) // Sign with a secret key
				.compact();
	}

	public boolean isTokenExpired(String token) {
		return extractExpiration(token).before(new Date());
	}

	public String extractUsername(String token) {
		return extractClaim(token, "sub", String.class);
	}

	public Date extractExpiration(String token) {
		return extractClaim(token, "exp", Date.class);
	}

	private <T> T extractClaim(String token, String claim, Class<T> claimType) {
		return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey))).build().parseSignedClaims(token)
				.getPayload().get(claim, claimType);
	}

}
