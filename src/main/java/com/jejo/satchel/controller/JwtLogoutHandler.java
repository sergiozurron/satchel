package com.jejo.satchel.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import com.jejo.satchel.repository.AuthTokenRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtLogoutHandler implements LogoutHandler {

	private final AuthTokenRepository authTokenRepository;
	
	public JwtLogoutHandler(AuthTokenRepository authTokenRepository) {
		this.authTokenRepository = authTokenRepository;
	}
	
	@Override
	public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			return;
		}
		String jwt = authHeader.substring(7);
		authTokenRepository.findByToken(jwt).ifPresent(token -> {
			token.setRevoked(true);
			authTokenRepository.save(token);
		});
	}

}
