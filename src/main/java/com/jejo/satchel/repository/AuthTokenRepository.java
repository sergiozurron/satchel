package com.jejo.satchel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jejo.satchel.model.AuthToken;
import com.jejo.satchel.model.User;

@Repository
public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
	
	public Optional<AuthToken> findByToken(String token);
	public List<AuthToken> findAllByUser(User user);
}
