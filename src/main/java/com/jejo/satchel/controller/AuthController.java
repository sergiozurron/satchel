package com.jejo.satchel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jejo.satchel.dto.LoginRequest;
import com.jejo.satchel.dto.LoginResponse;
import com.jejo.satchel.dto.Response;
import com.jejo.satchel.dto.SignupRequest;
import com.jejo.satchel.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
	
	private final AuthService authService;
	
	public AuthController(AuthService authService) {
		this.authService = authService;
	}
	
	@PostMapping("/signup")
	public ResponseEntity<Response> signup(@Valid @RequestBody SignupRequest signupRequest) {
		authService.signup(signupRequest);
		return ResponseEntity.ok(new Response("Signup successful. Please check your email for verification."));
	}
	
	@GetMapping("/email-verification")
	public ResponseEntity<Response> emailVerification(@RequestParam String token) {
		authService.verifyEmail(token);
		return ResponseEntity.ok(new Response("Email verification successful. You can now log in."));
	}
	
	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
		String jwt = authService.login(loginRequest);
		return ResponseEntity.ok(LoginResponse.builder()
				.message("Login successful.")
				.token(jwt)
				.build());
	}

}
