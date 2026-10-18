package com.jejo.satchel.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jejo.satchel.model.EmailVerificationToken;
import com.jejo.satchel.model.User;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
	public Optional<EmailVerificationToken> findByToken(String token);

	public Optional<EmailVerificationToken> findFirstByUserOrderByCreatedAtDesc(User user);
}
