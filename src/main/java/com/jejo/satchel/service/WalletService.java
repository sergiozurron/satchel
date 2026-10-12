package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.jejo.satchel.exception.DepositWalletAlreadyExistsException;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.User;
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

    @Transactional
    public void createDepositWallet(String assetId) {
        User currentUser= currentUserProvider.getCurrentUser();
        if (depositWalletRepository.findByUserIdAndAssetId(currentUser.getId(), assetId).isPresent()) {
        	throw new DepositWalletAlreadyExistsException(assetId);
		}
        String walletAddress = assetCustodianService.createVaultWallet(currentUser.getVaultAccountId(), assetId);
        DepositWallet depositWallet = new DepositWallet();
        depositWallet.setAddress(walletAddress);
        depositWallet.setUser(currentUserProvider.getCurrentUser());
        depositWallet.setAssetId(assetId);
        depositWallet.setBalance(BigDecimal.ZERO);
        depositWallet.setLockedBalance(BigDecimal.ZERO);
        depositWallet.setAccruedInterest(BigDecimal.ZERO);
        depositWallet.setOpenedAt(LocalDateTime.now());
        depositWalletRepository.save(depositWallet);
    }

    @Transactional
    public List<DepositWallet> getAllUserWallets() {
        Long userId = currentUserProvider.getCurrentUser().getId();
        return depositWalletRepository.findAllByUserId(userId);
    }

}
