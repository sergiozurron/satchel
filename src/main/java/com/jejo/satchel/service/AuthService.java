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
import com.jejo.satchel.model.AuthToken;
import com.jejo.satchel.model.EmailVerificationToken;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.AuthTokenRepository;
import com.jejo.satchel.repository.EmailVerificationTokenRepository;
import com.jejo.satchel.repository.UserRepository;
import com.jejo.satchel.util.JwtUtil;

import jakarta.transaction.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailVerificationTokenRepository emailVerificationTokenRepository;
	private final MailService mailService;
	private final AuthenticationManager authenticationManager;
	private final AuthTokenRepository authTokenRepository;
	private final JwtUtil jwtUtil;
	private final AssetCustodianService assetCustodianService;
	private final AuthMapper authMapper = AuthMapper.INSTANCE;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			EmailVerificationTokenRepository emailVerificationTokenRepository, MailService mailService,
			AuthenticationManager authenticationManager, AuthTokenRepository authorizationTokenRepository,
			JwtUtil jwtUtil, AssetCustodianService assetCustodianService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.emailVerificationTokenRepository = emailVerificationTokenRepository;
		this.mailService = mailService;
		this.authenticationManager = authenticationManager;
		this.authTokenRepository = authorizationTokenRepository;
		this.jwtUtil = jwtUtil;
		this.assetCustodianService = assetCustodianService;
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
		Long vaultId = assetCustodianService.createVaultAccount("deposit-" + user.getId());
		user.setVaultAccountId(vaultId);
		userRepository.save(user);
	}

	public String login(LoginRequest loginRequest) {
		String username = loginRequest.getEmail();
		// Throws AuthenticationException
		authenticationManager
				.authenticate(new UsernamePasswordAuthenticationToken(username, loginRequest.getPassword()));
		// So, the following code won't be executed if authentication fails
		User user = userRepository.findByEmail(username).get();
		authTokenRepository.findAllByUser(user).forEach(token -> {
			token.setRevoked(true);
			authTokenRepository.save(token);
		});
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
		String token = jwtUtil.generateToken(user);
		AuthToken authorizationToken = AuthToken.builder().token(token).user(user).build();
		authTokenRepository.save(authorizationToken);
		return token;
	}

}
