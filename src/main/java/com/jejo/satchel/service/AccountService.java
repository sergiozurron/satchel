package com.jejo.satchel.service;

import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
		if (txDetails.getDestinationAddress().equals(assetCustodianService.omnibusAddress)
				|| !txDetails.getStatus().equals(assetCustodianService.transactionStatusCompleted)
				|| !txDetails.getSubStatus()
						.equals(assetCustodianService.transactionSubstatusConfirmed)
				|| txDetails.getDestination().getName().startsWith(repaymentPrefix)
				|| fundsTransferRepository.existsByTransactionIdAndIsCompleted(txDetails.getId(),
						true)) {
			return;
		}
		// Withdrawal from omnibus to external wallet
		if (txDetails.getSourceAddress().equals(assetCustodianService.omnibusAddress)) {
			fundsTransferRepository.findByTransactionId(txDetails.getId())
					.ifPresent(fundsTransfer -> {
						fundsTransfer.setIsCompleted(true);
						fundsTransferRepository.save(fundsTransfer);
						DepositWallet wallet = fundsTransfer.getAccount();
						wallet.deposit(fundsTransfer.getAmount());
						depositWalletRepository.save(wallet);
					});
		} else {
			// Deposit from external wallet
			Optional<DepositWallet> depositWalletOpt = depositWalletRepository
					.findByAddressAndAssetId(txDetails.getDestinationAddress(), txDetails.getAssetId());
			if (depositWalletOpt.isEmpty()) {
				depositWalletRepository.save(DepositWallet.builder()
						.address(txDetails.getDestinationAddress())
						.assetId(txDetails.getAssetId())
						.balance(new BigDecimal(txDetails.getAmountInfo().getAmount()))
						.user(currentUserProvider.getCurrentUser())
						.openedAt(LocalDateTime.now())
						.build());
			}
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
		DepositWallet depositWallet = depositWalletRepository.findByAddressAndAssetId(withdrawalRequest.getDestinationAddress(), withdrawalRequest.getAssetId())
				.orElseThrow(() -> new IllegalArgumentException(
						"Deposit wallet not found for address: " + withdrawalRequest.getDestinationAddress() + " and coin: " + withdrawalRequest.getAssetId()));
		if (withdrawalRequest.getAmount().compareTo(depositWallet.getBalance()) > 0) {
			throw new InsufficientFundsException();
		}
		depositWalletRepository
				.findByAddressAndAssetId(withdrawalRequest.getDestinationAddress(), depositCoin)
				.ifPresentOrElse(destinationAccount -> {
					if (destinationAccount.getUser().getId().equals(user.getId())) {
						throw new SelfTransferException();
					}
					depositWallet.deposit(withdrawalRequest.getAmount().negate());
					depositWalletRepository.save(depositWallet);
					fundsTransferRepository.save(FundsTransfer.builder().account(depositWallet)
							.counterpartyAddress(withdrawalRequest.getDestinationAddress())
							.amount(withdrawalRequest.getAmount().negate())
							.timestamp(LocalDateTime.now()).isCompleted(true).build());
					destinationAccount.deposit(withdrawalRequest.getAmount());
					depositWalletRepository.save(destinationAccount);
					fundsTransferRepository.save(FundsTransfer.builder().account(destinationAccount)
							.counterpartyAddress(depositWallet.getAddress())
							.amount(withdrawalRequest.getAmount()).timestamp(LocalDateTime.now())
							.isCompleted(true).build());
				}, () -> {
					String txId = assetCustodianService.createTransactionFromWithdrawal(depositCoin,
							withdrawalRequest.getDestinationAddress(),
							withdrawalRequest.getAmount());
					fundsTransferRepository.save(FundsTransfer.builder().account(depositWallet)
							.counterpartyAddress(withdrawalRequest.getDestinationAddress())
							.transactionId(txId).amount(withdrawalRequest.getAmount().negate())
							.timestamp(LocalDateTime.now()).isCompleted(false).build());
				});
	}

}
