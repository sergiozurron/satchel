package com.jejo.satchel.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jejo.satchel.model.DepositAccount;

@Repository
public interface DepositAccountRepository extends JpaRepository<DepositAccount, Long> {
	
	public Optional<DepositAccount> findByAddress(String address);
	public Optional<DepositAccount> findByUserId(Long id);
}
