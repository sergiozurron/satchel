package com.jejo.satchel.service;

import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.jejo.satchel.exception.DepositWalletNotFoundByAssetIdException;
import com.jejo.satchel.exception.InsufficientFundsException;
import com.jejo.satchel.exception.SelfTransferException;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.FundsTransfer;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.FundsTransferRepository;
import com.jejo.satchel.repository.LoanRepository;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.util.CurrentUserProvider;
import com.jejo.satchel.dto.TransactionDetails;
import com.jejo.satchel.dto.WithdrawalRequest;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AccountService {

	@Value("${satchel.financial.assets.supported}")
	public List<String> supportedAssets;

	@Value("${satchel.name.deposit.prefix}")
	public String depositPrefix;

	@Value("${satchel.custodian.account.omnibus.address}")
	public String omnibusAddress;
	@Value("${satchel.custodian.account.gas-station.address}")
	public String gasStationAddress;

	@Value("${satchel.custodian.transaction.status.completed}")
	public String transactionStatusCompleted;
	@Value("${satchel.custodian.transaction.substatus.confirmed}")
	public String transactionSubstatusConfirmed;

	private final CurrentUserProvider currentUserProvider;
	private final AssetCustodianService assetCustodianService;
	private final DepositWalletRepository depositWalletRepository;
	private final FundsTransferRepository fundsTransferRepository;

	public AccountService(CurrentUserProvider currentUserProvider,
			AssetCustodianService assetCustodianService, DepositWalletRepository accountRepository,
			FundsTransferRepository fundsTransferRepository, LoanRepository loanRepository) {
		this.currentUserProvider = currentUserProvider;
		this.assetCustodianService = assetCustodianService;
		this.depositWalletRepository = accountRepository;
		this.fundsTransferRepository = fundsTransferRepository;
	}

	@Async
	@Transactional
	public void processTransactionUpdate(TransactionDetails txDetails) {
		log.info("Processing transaction update for txId: {} with status {}", txDetails.getId(),
				txDetails.getStatus());
		if (txDetails.getDestinationAddress().equals(omnibusAddress)
				|| txDetails.getSourceAddress().equals(gasStationAddress)
				|| !txDetails.getStatus().equals(transactionStatusCompleted)
				|| !txDetails.getSubStatus().equals(transactionSubstatusConfirmed)
				|| fundsTransferRepository.existsByTransactionIdAndIsCompleted(txDetails.getId(),
						true)) {
			return;
		}
		if (txDetails.getSourceAddress().equals(omnibusAddress)) {
			// Withdrawal from omnibus to external wallet
			fundsTransferRepository.findByTransactionId(txDetails.getId())
					.ifPresent(fundsTransfer -> {
						fundsTransfer.setIsCompleted(true);
						fundsTransferRepository.save(fundsTransfer);
						DepositWallet wallet = fundsTransfer.getDepositWallet();
						wallet.deposit(fundsTransfer.getAmount());
						depositWalletRepository.save(wallet);
						log.info("Withdrawal completed for txId: {}. Updated wallet balance: {}",
								txDetails.getId(), wallet.getBalance());
					});
		} else {
			// Deposit from external wallet
			DepositWallet depositWallet = depositWalletRepository.findByAddressAndAssetId(
					txDetails.getDestinationAddress(), txDetails.getAssetId()).get();
			depositWallet.deposit(new BigDecimal(txDetails.getAmountInfo().getAmount()));
			depositWalletRepository.save(depositWallet);
			fundsTransferRepository.save(FundsTransfer.builder().transactionId(txDetails.getId())
					.depositWallet(depositWallet).counterpartyAddress(txDetails.getSourceAddress())
					.amount(new BigDecimal(txDetails.getAmountInfo().getAmount()))
					.timestamp(LocalDateTime.now()).isCompleted(true).build());
			log.info("Deposit completed for txId: {}. Updated wallet balance: {}",
					txDetails.getId(), depositWallet.getBalance());
		}

	}

	@Transactional
	public void initiateWithdrawal(WithdrawalRequest withdrawalRequest) {
		User user = currentUserProvider.getCurrentUser();
		String assetId = withdrawalRequest.getAssetId();
		DepositWallet depositWallet = depositWalletRepository
				.findByUserIdAndAssetId(user.getId(), assetId)
				.orElseThrow(() -> new DepositWalletNotFoundByAssetIdException(
						withdrawalRequest.getAssetId()));
		if (withdrawalRequest.getAmount().compareTo(depositWallet.getAvailableBalance()) >= 0) {
			throw new InsufficientFundsException();
		}
		// Detect transfers between two internal accounts and handle them without
		// blockchain transactions
		depositWalletRepository
				.findByAddressAndAssetId(withdrawalRequest.getDestinationAddress(), assetId)
				.ifPresentOrElse(destinationWallet -> {
					if (destinationWallet.getUser().getId().equals(user.getId())) {
						throw new SelfTransferException();
					}
					// Transfer between two internal accounts: no blockchain transaction needed
					depositWallet.deposit(withdrawalRequest.getAmount().negate());
					depositWalletRepository.save(depositWallet);
					fundsTransferRepository
							.save(FundsTransfer.builder().depositWallet(depositWallet)
									.counterpartyAddress(withdrawalRequest.getDestinationAddress())
									.amount(withdrawalRequest.getAmount().negate())
									.timestamp(LocalDateTime.now()).isCompleted(true).build());
					destinationWallet.deposit(withdrawalRequest.getAmount());
					depositWalletRepository.save(destinationWallet);
					fundsTransferRepository
							.save(FundsTransfer.builder().depositWallet(destinationWallet)
									.counterpartyAddress(depositWallet.getAddress())
									.amount(withdrawalRequest.getAmount())
									.timestamp(LocalDateTime.now()).isCompleted(true).build());
				}, () -> {
					// Transfer to external account
					String txId = assetCustodianService.createTransactionFromOmnibus(assetId,
							withdrawalRequest.getDestinationAddress(),
							withdrawalRequest.getAmount());
					// isCompleted is false because we will wait for the webhook to confirm the
					// transaction and credit the user's account
					fundsTransferRepository.save(FundsTransfer.builder()
							.depositWallet(depositWallet)
							.counterpartyAddress(withdrawalRequest.getDestinationAddress())
							.transactionId(txId).amount(withdrawalRequest.getAmount().negate())
							.timestamp(LocalDateTime.now()).isCompleted(false).build());
				});
	}

}
