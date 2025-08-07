package com.jejo.satchel.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.jejo.satchel.dto.LoginRequest;
import com.jejo.satchel.dto.SignupRequest;
import com.jejo.satchel.exception.EmailTakenException;
import com.jejo.satchel.exception.EmailVerificationTokenExpiredException;
import com.jejo.satchel.exception.EmailVerificationTokenNotFoundException;
import com.jejo.satchel.exception.EmailVerificationTokenUsedException;
import com.jejo.satchel.exception.UserAlreadyVerifiedException;
import com.jejo.satchel.mapper.AuthMapper;
import com.jejo.satchel.model.AuthorizationToken;
import com.jejo.satchel.model.EmailVerificationToken;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.AuthorizationTokenRepository;
import com.jejo.satchel.repository.EmailVerificationTokenRepository;
import com.jejo.satchel.repository.UserRepository;
import com.jejo.satchel.util.JwtUtil;

import jakarta.transaction.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final AuthMapper authMapper;
	private final PasswordEncoder passwordEncoder;
	private final EmailVerificationTokenRepository emailVerificationTokenRepository;
	private final MailService mailService;
	private final AuthenticationManager authenticationManager;
	private final AuthorizationTokenRepository authorizationTokenRepository;
	private final JwtUtil jwtUtil;

	public AuthService(UserRepository userRepository, AuthMapper authMapper, PasswordEncoder passwordEncoder,
			EmailVerificationTokenRepository emailVerificationTokenRepository, MailService mailService, AuthenticationManager authenticationManager, AuthorizationTokenRepository authorizationTokenRepository, JwtUtil jwtUtil) {
		this.userRepository = userRepository;
		this.authMapper = authMapper;
		this.passwordEncoder = passwordEncoder;
		this.emailVerificationTokenRepository = emailVerificationTokenRepository;
		this.mailService = mailService;
		this.authenticationManager = authenticationManager;
		this.authorizationTokenRepository = authorizationTokenRepository;
		this.jwtUtil = jwtUtil;
	}

	@Transactional
	public void signup(SignupRequest signupRequest) {
		Optional<User> userOpt = userRepository.findByEmail(signupRequest.getEmail());
		if (userOpt.isPresent()) {
			if (userOpt.get().isVerified()) {
				throw new EmailTakenException(signupRequest.getEmail());
			}
			sendVerificationEmail(userOpt.get());
		} else {
			User user = authMapper.toUserEntity(signupRequest);
			user.setPassword(passwordEncoder.encode(user.getPassword()));
			userRepository.save(user);
			sendVerificationEmail(user);
		}
	}
	

	@Transactional
	public void verifyEmail(String token) {
		EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
				.orElseThrow(() -> new EmailVerificationTokenNotFoundException(token));
		if (verificationToken.isExpired()) {
			throw new EmailVerificationTokenExpiredException(token);
		}
		if (verificationToken.isUsed()) {
			throw new EmailVerificationTokenUsedException(token);
		}
		User user = verificationToken.getUser();
		if (user.isVerified()) {
			throw new UserAlreadyVerifiedException(token);
		}
		verificationToken.setUsed(true);
		emailVerificationTokenRepository.save(verificationToken);
		user.setVerified(true);
		userRepository.save(user);
	}
	
	public String login(LoginRequest loginRequest) {
		String username = loginRequest.getEmail();
		// Throws AuthenticationException
		authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, loginRequest.getPassword()));
		// So, the following code won't be executed if authentication fails
		User user = userRepository.findByEmail(username).get();
		return generateAuthorizationToken(user);
	}

	private void sendVerificationEmail(User user) {
		String token = generateEmailVerificationToken(user);
		mailService.sendActivationEmail(user.getFirstName(), user.getEmail(), token);
	}

	private String generateEmailVerificationToken(User user) {
		String token = UUID.randomUUID().toString();
		// Expires in 24 hours
		EmailVerificationToken verificationToken = EmailVerificationToken.builder().token(token).user(user)
				.expiresAt(LocalDateTime.now().plusHours(24)).build();
		emailVerificationTokenRepository.save(verificationToken);
		return token;
	}
	
	private String generateAuthorizationToken(User user) {
		authorizationTokenRepository.deleteByUser(user);
		String token = jwtUtil.generateToken(user);
		AuthorizationToken authorizationToken = AuthorizationToken.builder()
				.token(token)
				.user(user)
				.expiresAt(LocalDateTime.now().plusDays(7)) // Expires in 7 days
				.build();
		authorizationTokenRepository.save(authorizationToken);
		return token;
	}

}
