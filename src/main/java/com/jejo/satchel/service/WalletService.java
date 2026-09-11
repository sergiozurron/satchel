package com.jejo.satchel.service;

import java.math.BigDecimal;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.util.CurrentUserProvider;

import jakarta.transaction.Transactional;

/**
 * WalletService
 */
@Service
public class WalletService {

    public final CurrentUserProvider currentUserProvider;
    public final AssetCustodianService assetCustodianService;
    public final DepositWalletRepository depositWalletRepository;

    public WalletService(AssetCustodianService assetCustodianService, CurrentUserProvider currentUserProvider,
            DepositWalletRepository depositWalletRepository) {
        this.assetCustodianService = assetCustodianService;
        this.currentUserProvider = currentUserProvider;
        this.depositWalletRepository = depositWalletRepository;
    }

    @Async
    @Transactional
    public void createDepositWallet(String assetId) {
        Long currentUserVaultId = currentUserProvider.getCurrentUser().getVaultAccountId();
        String walletAddress = assetCustodianService.createVaultWallet(currentUserVaultId, assetId);
        DepositWallet depositWallet = new DepositWallet();
        depositWallet.setAddress(walletAddress);
        depositWallet.setUser(currentUserProvider.getCurrentUser());
        depositWallet.setAssetId(assetId);
        depositWallet.setBalance(BigDecimal.ZERO);
        depositWallet.setLockedBalance(BigDecimal.ZERO);
        depositWallet.setOpenedAt(java.time.LocalDateTime.now());
        depositWalletRepository.save(depositWallet);
    }

}
