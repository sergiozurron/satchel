package com.jejo.satchel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jejo.satchel.model.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

	public boolean existsByUserId(Long id);
	public Optional<Account> findByVaultAccountIdAndCoin(Long vaultAccountId, String assetId);
	public Optional<Account> findByAddressAndCoin(String address, String coin);
	public List<Account> findAllByUserId(Long userId);
}
