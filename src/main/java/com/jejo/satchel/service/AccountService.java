package com.jejo.satchel.service;

import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
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
import com.fireblocks.sdk.model.VaultAccount;
import com.jejo.satchel.dto.TransactionDetails;
import com.jejo.satchel.dto.WithdrawalRequest;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AccountService {

	@Value("${custodian.account.collateral.coin}")
	public String collateralCoin;
	@Value("${custodian.account.repayment.coin}")
	public String repaymentCoin;
	@Value("${custodian.account.deposit.coin}")
	public String depositCoin;

	@Value("${name.deposit.prefix}")
	public String depositPrefix;
	@Value("${name.collateral.prefix}")
	public String collateralPrefix;
	@Value("${name.repayment.prefix}")
	public String repaymentPrefix;

	@Value("${financial.deposit.sweep.min-amount}")
	public BigDecimal depositSweepMinAmount;
	@Value("${financial.interest.accrual-period}")
	public Integer interestAccrualPeriod;
	@Value("${financial.interest.apy}")
	public Double interestRate;
	
	@Value("${custodian.account.omnibus.address}")
	public String omnibusAddress;
	
	@Value("${custodian.transaction.status.completed}")
	public String transactionStatusCompleted;
	@Value("${custodian.transaction.substatus.confirmed}")
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
		if (txDetails.getDestinationAddress().equals(omnibusAddress)
				|| !txDetails.getStatus().equals(transactionStatusCompleted)
				|| !txDetails.getSubStatus()
						.equals(transactionSubstatusConfirmed)
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
						wallet.deposit(fundsTransfer.getAmount().negate());
						depositWalletRepository.save(wallet);
					});
		} else {
			// Deposit from external wallet
			DepositWallet depositWallet = depositWalletRepository
					.findByAddressAndAssetId(txDetails.getDestinationAddress(), txDetails.getAssetId()).get();
			depositWallet.deposit(new BigDecimal(txDetails.getAmountInfo().getAmount()));
			depositWalletRepository.save(depositWallet);
			fundsTransferRepository.save(FundsTransfer.builder()
					.transactionId(txDetails.getId()).depositWallet(depositWallet)
					.counterpartyAddress(txDetails.getSourceAddress())
					.amount(new BigDecimal(txDetails.getAmountInfo().getAmount()))
					.timestamp(LocalDateTime.now()).isCompleted(true).build());
		}
		
	}

	@Scheduled(initialDelayString = "${custodian.deposit.sweep.delay}", fixedDelayString = "${custodian.deposit.sweep.delay}")
	public void sweepDepositsToOmnibus() {
		log.info("Sweeping deposits to omnibus account");
		List<VaultAccount> depositAccounts = assetCustodianService
				.findAllVaultAccountsByPrefixAndMinAmountAndAsset(depositPrefix,
						depositSweepMinAmount, depositCoin);
		assetCustodianService.createTransactionsToOmnibus(depositAccounts);
	}

	public void initiateWithdrawal(WithdrawalRequest withdrawalRequest) {
		User user = currentUserProvider.getCurrentUser();
		DepositWallet depositWallet = depositWalletRepository.findByUserIdAndAssetId(user.getId(), withdrawalRequest.getAssetId())
				.orElseThrow(() -> new DepositWalletNotFoundByAssetIdException(withdrawalRequest.getAssetId()));
		if (withdrawalRequest.getAmount().compareTo(depositWallet.getAvailableBalance()) > 0) {
			throw new InsufficientFundsException();
		}
		depositWalletRepository
				.findByAddressAndAssetId(withdrawalRequest.getDestinationAddress(), depositCoin)
				.ifPresentOrElse(destinationWallet -> {
					if (destinationWallet.getUser().getId().equals(user.getId())) {
						throw new SelfTransferException();
					}
					// Transfer between two internal accounts: no blockchain transaction needed
					depositWallet.deposit(withdrawalRequest.getAmount().negate());
					depositWalletRepository.save(depositWallet);
					fundsTransferRepository.save(FundsTransfer.builder().depositWallet(depositWallet)
							.counterpartyAddress(withdrawalRequest.getDestinationAddress())
							.amount(withdrawalRequest.getAmount().negate())
							.timestamp(LocalDateTime.now()).isCompleted(true).build());
					destinationWallet.deposit(withdrawalRequest.getAmount());
					depositWalletRepository.save(destinationWallet);
					fundsTransferRepository.save(FundsTransfer.builder().depositWallet(destinationWallet)
							.counterpartyAddress(depositWallet.getAddress())
							.amount(withdrawalRequest.getAmount()).timestamp(LocalDateTime.now())
							.isCompleted(true).build());
				}, () -> {
					// Transfer to external account
					String txId = assetCustodianService.createTransactionFromOmnibus(depositCoin,
							withdrawalRequest.getDestinationAddress(),
							withdrawalRequest.getAmount());
					fundsTransferRepository.save(FundsTransfer.builder().depositWallet(depositWallet)
							.counterpartyAddress(withdrawalRequest.getDestinationAddress())
							.transactionId(txId).amount(withdrawalRequest.getAmount().negate())
							.timestamp(LocalDateTime.now()).isCompleted(false).build());
				});
	}

}
