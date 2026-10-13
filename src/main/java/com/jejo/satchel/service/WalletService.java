package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.jejo.satchel.exception.DepositWalletAlreadyExistsException;
import com.jejo.satchel.exception.DepositWalletNotFoundException;
import com.jejo.satchel.exception.UnableToGetAddressException;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.util.CurrentUserProvider;

import jakarta.transaction.Transactional;

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
    public DepositWallet createDepositWallet(String assetId) {
        Long currentUser= currentUserProvider.getCurrentUser().getId();
        if (depositWalletRepository.findByUserIdAndAssetId(currentUser, assetId).isPresent()) {
        	throw new DepositWalletAlreadyExistsException(assetId);
		}
        String walletAddress = assetCustodianService.getDepositAddress(currentUser, assetId);
        if (walletAddress == null) {
        	throw new UnableToGetAddressException();
        }
        DepositWallet depositWallet = new DepositWallet();
        depositWallet.setAddress(walletAddress);
        depositWallet.setUser(currentUserProvider.getCurrentUser());
        depositWallet.setAssetId(assetId);
        depositWallet.setBalance(BigDecimal.ZERO);
        depositWallet.setLockedBalance(BigDecimal.ZERO);
        depositWallet.setAccruedInterest(BigDecimal.ZERO);
        depositWallet.setOpenedAt(LocalDateTime.now());
        return depositWalletRepository.save(depositWallet);
    }

    @Transactional
    public List<DepositWallet> getAllUserWallets() {
        Long userId = currentUserProvider.getCurrentUser().getId();
        return depositWalletRepository.findAllByUserId(userId);
    }

    @Transactional
    public DepositWallet getUserWallet(Long id) {
        Long userId = currentUserProvider.getCurrentUser().getId();
        return depositWalletRepository.findByIdAndUserId(id, userId)
                .orElseThrow(DepositWalletNotFoundException::new);
    }

}
