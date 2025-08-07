package com.jejo.satchel.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.jejo.satchel.dto.SignupRequest;
import com.jejo.satchel.exception.EmailTakenException;
import com.jejo.satchel.exception.EmailVerificationTokenExpiredException;
import com.jejo.satchel.exception.EmailVerificationTokenUsedException;
import com.jejo.satchel.exception.UserAlreadyVerifiedException;
import com.jejo.satchel.mapper.AuthMapper;
import com.jejo.satchel.model.EmailVerificationToken;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.EmailVerificationTokenRepository;
import com.jejo.satchel.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private AuthMapper authMapper;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private EmailVerificationTokenRepository emailVerificationTokenRepository;
	@Mock
	private MailService mailService;
	@InjectMocks
	private AuthService authService;

	@Test
	void signup_ShouldSaveUser_WhenEmailNotTaken() {
		// Given
		SignupRequest signupRequest = SignupRequest.builder().email("email").password("password").firstName("firstName")
				.lastName("lastName").build();

		when(userRepository.findByEmail("email")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
		when(authMapper.toUserEntity(signupRequest)).thenReturn(
				User.builder().firstName("firstName").lastName("lastName").email("email").password("password").build());
		when(userRepository.save(User.builder().firstName("firstName").lastName("lastName").email("email")
				.password("encodedPassword").build())).thenReturn(null); // Mocking save to return null as we don't care
																			// about the return value in this test
		when(emailVerificationTokenRepository.save(any())).thenReturn(null); // Mocking the save method to do nothing
		doNothing().when(mailService).sendActivationEmail(eq("firstName"), eq("email"), any(String.class));

		// When
		authService.signup(signupRequest);

		// Then
		verify(userRepository).save(any(User.class));
		verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
		verify(mailService).sendActivationEmail(eq("firstName"), eq("email"), any(String.class));
	}

	@Test
	void signup_ShouldThrowEmailTakenException_WhenEmailAlreadyTaken() {
		// Given
		SignupRequest signupRequest = SignupRequest.builder().email("email").password("password").firstName("firstName")
				.lastName("lastName").build();

		User existingUser = User.builder().firstName("existingFirstName").lastName("existingLastName").email("email")
				.password("encodedPassword").verified(true).build();
		when(userRepository.findByEmail("email")).thenReturn(Optional.of(existingUser));

		// When & Then
		assertThatThrownBy(() -> authService.signup(signupRequest)).isInstanceOf(EmailTakenException.class)
				.hasMessage("Email is already taken: email");

		verify(userRepository).findByEmail("email");
	}

	@Test
	void signup_ShouldSendVerificationEmail_WhenEmailNotVerified() {
		// Given
		SignupRequest signupRequest = SignupRequest.builder().email("email").password("password").firstName("firstName")
				.lastName("lastName").build();

		User existingUser = User.builder().firstName("firstName").lastName("lastName").email("email")
				.password("encodedPassword").verified(false).build();
		when(userRepository.findByEmail("email")).thenReturn(Optional.of(existingUser));

		doNothing().when(mailService).sendActivationEmail(eq("firstName"), eq("email"), any(String.class));

		// When
		authService.signup(signupRequest);

		// Then
		verify(mailService).sendActivationEmail(eq("firstName"), eq("email"), any(String.class));
	}

	@Test
	void verifyEmail_ShouldVerifyUser_WhenTokenIsValid() {
		// Given
		String token = "validToken";

		when(emailVerificationTokenRepository.findByToken(token)).thenReturn(
				Optional.of(EmailVerificationToken.builder().token(token).expiresAt(LocalDateTime.of(9999, 1, 1, 1, 1, 1))
						.used(false).user(User.builder().verified(false).build()).build()));
		when(emailVerificationTokenRepository
				.save(EmailVerificationToken.builder().token(token).expiresAt(LocalDateTime.of(9999, 1, 1, 1, 1, 1))
						.used(true).user(User.builder().verified(false).build()).build()))
				.thenReturn(null);
		when(userRepository.save(User.builder().verified(true).build())).thenReturn(null);
		
		// When
		authService.verifyEmail(token);
		
		// Then
		verify(emailVerificationTokenRepository).findByToken(token);
		verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
		verify(userRepository).save(any(User.class));
	}
	
	@Test
	void verifyEmail_ShouldThrowException_WhenTokenIsExpired() {
		// Given
		String token = "expiredToken";

		when(emailVerificationTokenRepository.findByToken(token)).thenReturn(
				Optional.of(EmailVerificationToken.builder().token(token).expiresAt(LocalDateTime.now().minusDays(1))
						.used(false).user(User.builder().verified(false).build()).build()));

		// When & Then
		assertThatThrownBy(() -> authService.verifyEmail(token))
				.isInstanceOf(EmailVerificationTokenExpiredException.class)
				.hasMessage("Email verification token expired: " + token);

		verify(emailVerificationTokenRepository).findByToken(token);
	}
	
	@Test
	void verifyEmail_ShouldThrowException_WhenTokenIsUsed() {
		// Given
		String token = "userToken";

		when(emailVerificationTokenRepository.findByToken(token)).thenReturn(
				Optional.of(EmailVerificationToken.builder().token(token).expiresAt(LocalDateTime.of(9999, 1, 1, 1, 1, 1))
						.used(true).user(User.builder().verified(false).build()).build()));
		
		// When & Then
		assertThatThrownBy(() -> authService.verifyEmail(token))
				.isInstanceOf(EmailVerificationTokenUsedException.class)
				.hasMessage("Email verification token has already been used: " + token);
		
		verify(emailVerificationTokenRepository).findByToken(token);
	}
	
	@Test
	void verifyEmail_ShouldThrowException_WhenUserAlreadyVerified() {
		// Given
		String token = "token";

		when(emailVerificationTokenRepository.findByToken(token)).thenReturn(
				Optional.of(EmailVerificationToken.builder().token(token).expiresAt(LocalDateTime.of(9999, 1, 1, 1, 1, 1))
						.used(false).user(User.builder().verified(true).build()).build()));
		
		// When & Then
		assertThatThrownBy(() -> authService.verifyEmail(token))
				.isInstanceOf(UserAlreadyVerifiedException.class)
				.hasMessage("User with token " + token + " is already verified.");
		
		verify(emailVerificationTokenRepository).findByToken(token);
	}
	
}
