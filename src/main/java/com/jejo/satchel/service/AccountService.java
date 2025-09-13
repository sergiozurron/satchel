package com.jejo.satchel.service;

import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jejo.satchel.exception.AccountsAlreadyCreatedException;
import com.jejo.satchel.exception.InsufficientFundsException;
import com.jejo.satchel.exception.SelfTransferException;
import com.jejo.satchel.model.Account;
import com.jejo.satchel.model.AccountType;
import com.jejo.satchel.model.FundsTransfer;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.FundsTransferRepository;
import com.jejo.satchel.repository.AccountRepository;
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

	@Value("${financial.deposit.sweep.min-amount}")
	public BigDecimal depositSweepMinAmount;
	@Value("${financial.interest.accrual-period}")
	public Integer interestAccrualPeriod;
	@Value("${financial.interest.apy}")
	public Double interestRate;

	private final CurrentUserProvider currentUserProvider;
	private final AssetCustodianService assetCustodianService;
	private final AccountRepository accountRepository;
	private final FundsTransferRepository fundsTransferRepository;

	public AccountService(CurrentUserProvider currentUserProvider,
			AssetCustodianService assetCustodianService, AccountRepository accountRepository,
			FundsTransferRepository fundsTransferRepository) {
		this.currentUserProvider = currentUserProvider;
		this.assetCustodianService = assetCustodianService;
		this.accountRepository = accountRepository;
		this.fundsTransferRepository = fundsTransferRepository;
	}

	@Transactional
	public void createUserAccounts() {
		User user = currentUserProvider.getCurrentUser();
		if (accountRepository.existsByUserId(user.getId())) {
			throw new AccountsAlreadyCreatedException(user.getId());
		}
		// Create collateral vault with BTC wallet
		createVaultWithWallet(collateralPrefix, collateralCoin, user, AccountType.COLLATERAL);
		// Create deposit vault with USDC wallet
		createVaultWithWallet(depositPrefix, depositCoin, user, AccountType.DEPOSIT);
	}

	@Async
	@Transactional
	public void processTransactionUpdate(TransactionDetails txDetails) {
		if (txDetails.getDestinationAddress().equals(assetCustodianService.omnibusAddress)
				|| !txDetails.getStatus().equals(assetCustodianService.transactionStatusCompleted)
				|| !txDetails.getSubStatus()
						.equals(assetCustodianService.transactionSubstatusConfirmed)
				|| fundsTransferRepository.existsByTransactionIdAndIsCompleted(txDetails.getId(), true)) {
			return;
		}
		if (txDetails.getSourceAddress().equals(assetCustodianService.withdrawalAddress)) {
			fundsTransferRepository.findByTransactionId(txDetails.getId())
					.ifPresent(fundsTransfer -> {
						fundsTransfer.setIsCompleted(true);
						fundsTransferRepository.save(fundsTransfer);
						Account account = fundsTransfer.getAccount();
						account.deposit(fundsTransfer.getAmount());
						accountRepository.save(account);
					});
		}
		accountRepository
				.findByAddressAndCoin(txDetails.getDestinationAddress(), txDetails.getAssetId())
				.ifPresent(account -> {
					fundsTransferRepository.save(FundsTransfer.builder()
							.transactionId(txDetails.getId()).account(account)
							.counterpartyAddress(txDetails.getSourceAddress())
							.amount(new BigDecimal(txDetails.getAmountInfo().getAmount()))
							.timestamp(LocalDateTime.now()).isCompleted(true).build());
					account.deposit(new BigDecimal(txDetails.getAmountInfo().getAmount()));
					accountRepository.save(account);
				});
	}

	private void createVaultWithWallet(String namePrefix, String coin, User user,
			AccountType type) {
		String accountName = namePrefix + user.getEmail(); // TODO Use user ID instead of email
		Long accountId = assetCustodianService.createVaultAccount(accountName);
		String address = assetCustodianService.createWallet(accountId.toString(), coin);
		accountRepository
				.save(Account.builder().address(address).coin(coin).balance(BigDecimal.ZERO)
						.lockedBalance(BigDecimal.ZERO).openedAt(LocalDateTime.now())
						.vaultAccountId(accountId).type(type).user(user).build());
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
		Account depositsAccount = accountRepository
				.findByUserIdAndType(user.getId(), AccountType.DEPOSIT).get();
		if (withdrawalRequest.getAmount().compareTo(depositsAccount.getBalance()) > 0) {
			throw new InsufficientFundsException();
		}
		accountRepository
				.findByAddressAndCoin(withdrawalRequest.getDestinationAddress(), depositCoin)
				.ifPresentOrElse(destinationAccount -> {
					if (destinationAccount.getUser().getId().equals(user.getId())) {
						throw new SelfTransferException();
					}
					depositsAccount.deposit(withdrawalRequest.getAmount().negate());
					accountRepository.save(depositsAccount);
					fundsTransferRepository.save(FundsTransfer.builder().account(depositsAccount)
							.counterpartyAddress(withdrawalRequest.getDestinationAddress())
							.amount(withdrawalRequest.getAmount().negate())
							.timestamp(LocalDateTime.now()).isCompleted(true).build());
					destinationAccount.deposit(withdrawalRequest.getAmount());
					accountRepository.save(destinationAccount);
					fundsTransferRepository.save(FundsTransfer.builder().account(destinationAccount)
							.counterpartyAddress(depositsAccount.getAddress())
							.amount(withdrawalRequest.getAmount()).timestamp(LocalDateTime.now())
							.isCompleted(true).build());
				}, () -> {
					String txId = assetCustodianService.createTransactionFromWithdrawal(depositCoin,
							withdrawalRequest.getDestinationAddress(),
							withdrawalRequest.getAmount());
					fundsTransferRepository.save(FundsTransfer.builder().account(depositsAccount)
							.counterpartyAddress(withdrawalRequest.getDestinationAddress())
							.transactionId(txId).amount(withdrawalRequest.getAmount().negate())
							.timestamp(LocalDateTime.now()).isCompleted(false).build());
				});
	}
	
	

}
