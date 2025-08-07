package com.jejo.satchel.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jejo.satchel.model.AuthorizationToken;
import com.jejo.satchel.model.User;

@Repository
public interface AuthorizationTokenRepository extends JpaRepository<AuthorizationToken, Long> {
	
	public Optional<AuthorizationToken> findByToken(String token);
	public Optional<AuthorizationToken> deleteByUser(User user);
}
