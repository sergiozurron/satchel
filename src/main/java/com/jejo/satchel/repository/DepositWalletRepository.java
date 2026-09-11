package com.jejo.satchel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jejo.satchel.model.DepositWallet;

public interface DepositWalletRepository extends JpaRepository<DepositWallet, Long> {

	public boolean existsByUserId(Long id);
	public Optional<DepositWallet> findByAddressAndAssetId(String address, String assetId);
	public Optional<DepositWallet> findByUserIdAndAssetId(Long userId, String assetId);
	public List<DepositWallet> findAllByUserId(Long userId);
	public List<DepositWallet> findByAssetId(String assetId);
}
