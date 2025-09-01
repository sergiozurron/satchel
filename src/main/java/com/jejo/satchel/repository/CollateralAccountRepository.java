package com.jejo.satchel.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jejo.satchel.model.CollateralAccount;

@Repository
public interface CollateralAccountRepository extends JpaRepository<CollateralAccount, Long> {

	public Optional<CollateralAccount> findByAddress(String address);
	public Optional<CollateralAccount> findByUserId(Long userId);
}
