package com.jejo.satchel.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jejo.satchel.dto.LoginRequest;
import com.jejo.satchel.dto.SignupRequest;
import com.jejo.satchel.model.EmailVerificationToken;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.AuthTokenRepository;
import com.jejo.satchel.repository.EmailVerificationTokenRepository;
import com.jejo.satchel.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerIntTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private EmailVerificationTokenRepository emailVerificationTokenRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private AuthTokenRepository authorizationTokenRepository;

	@BeforeEach
	void setup() {
		// Clear the user repository before running tests
		emailVerificationTokenRepository.deleteAll();
		authorizationTokenRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void signup_shouldReturnOk_whenValidRequest() throws Exception {
		SignupRequest signupRequest = SignupRequest.builder().firstName("John").lastName("Doe").password("password123")
				.email("email@email.com").build();
		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(signupRequest))).andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Signup successful. Please check your email for verification."));
	}

	@Test
	void signup_shouldReturnBadRequest_whenInvalidEmail() throws Exception {
		SignupRequest signupRequest = SignupRequest.builder().firstName("John").lastName("Doe").password("password123")
				.email("invalid-email").build();

		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(signupRequest))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$[0].error").value("Email"));
	}

	@Test
	void signup_shouldReturnConflict_whenEmailAlreadyTaken() throws Exception {
		SignupRequest signupRequest = SignupRequest.builder().firstName("John").lastName("Doe").password("password123")
				.email("email@email.com").build();
		userRepository.save(User.builder().firstName("Juan").lastName("Doe").email("email@email.com")
				.password("password123").verified(true).build());

		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(signupRequest))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_EMAIL_TAKEN));
	}

	@Test
	void signup_shouldReturnOk_whenEmailAlreadyTakenButUnverified() throws Exception {
		SignupRequest signupRequest = SignupRequest.builder().firstName("John").lastName("Doe").password("password123")
				.email("email@email.com").build();
		userRepository.save(User.builder().firstName("Juan").lastName("Doe").email("email@email.com")
				.password("password123").verified(false).build());

		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(signupRequest))).andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Signup successful. Please check your email for verification."));
	}

	@Test
	void verifyEmail_shouldReturnOk_whenValidToken() throws Exception {
		String token = "valid-token";
		emailVerificationTokenRepository.save(EmailVerificationToken
				.builder().expiresAt(LocalDateTime.of(3000, 1, 1, 1, 1)).token(token).user(User.builder()
						.firstName("firstName").lastName("lastName").email("email").password("password").build())
				.build());
		mockMvc.perform(get("/api/v1/auth/email-verification").param("token", token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Email verification successful. You can now log in."));
	}

	@Test
	void verifyEmail_shouldReturnNotFound_whenTokenNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/auth/email-verification").param("token", "non-existing-token"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_EMAIL_VERIFICATION_TOKEN_NOT_FOUND));
	}

	@Test
	void verifiyEmail_shouldReturnBadRequest_whenExpiredToken() throws Exception {
		String token = "expired-token";
		emailVerificationTokenRepository.save(
				EmailVerificationToken.builder().expiresAt(LocalDateTime.of(2000, 1, 1, 1, 1)).token(token).build());

		mockMvc.perform(get("/api/v1/auth/email-verification").param("token", token)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_EMAIL_VERIFICATION_TOKEN_EXPIRED));
	}

	@Test
	void verifyEmail_shouldReturnBadRequest_whenUsedToken() throws Exception {
		String token = "used-token";
		emailVerificationTokenRepository.save(EmailVerificationToken.builder()
				.expiresAt(LocalDateTime.of(3000, 1, 1, 1, 1)).token(token).used(true).build());
	}

	@Test
	void verifyEmail_shouldReturnBadRequest_whenUserAlreadyVerified() throws Exception {
		String token = "token-already-verified";
		emailVerificationTokenRepository.save(EmailVerificationToken.builder()
				.expiresAt(LocalDateTime.of(3000, 1, 1, 1, 1)).token(token).user(User.builder().firstName("firstName")
						.lastName("lastName").email("email").password("password").verified(true).build())
				.build());
		mockMvc.perform(get("/api/v1/auth/email-verification").param("token", token)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_USER_ALREADY_VERIFIED));
	}

	@Test
	void login_shouldReturnOk_whenValidCredentials() throws Exception {
		String email = "email@email.com";
		String password = "password123";
		userRepository.save(User.builder().firstName("John").lastName("Doe").email(email)
				.password(passwordEncoder.encode(password)).verified(true).build());
		LoginRequest loginRequest = LoginRequest.builder().email(email).password(password).build();

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(loginRequest))).andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Login successful."))
				.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	void login_shouldReturnUnauthorized_whenInvalidCredentials() throws Exception {
		mockMvc.perform(
				post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(LoginRequest.builder().email("email@email")
								.password(passwordEncoder.encode("password")).build())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_UNAUTHORIZED));
	}
	
	@Test
	void login_shouldReturnUnauthorized_whenUserNotVerified() throws Exception {
		String email = "email@email.com";
		String password = "password123";
		userRepository.save(User.builder().firstName("John").lastName("Doe").email(email)
				.password(passwordEncoder.encode(password)).verified(false).build());
		LoginRequest loginRequest = LoginRequest.builder().email(email).password(password).build();
		
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(loginRequest))).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_UNAUTHORIZED));
	}
	
}
