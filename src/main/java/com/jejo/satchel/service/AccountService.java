package com.jejo.satchel.service;

import java.math.BigDecimal;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.jejo.satchel.exception.AccountsAlreadyCreatedException;
import com.jejo.satchel.model.Account;
import com.jejo.satchel.model.AccountType;
import com.jejo.satchel.model.FundsTransfer;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.FundsTransferRepository;
import com.jejo.satchel.repository.AccountRepository;
import com.jejo.satchel.util.CurrentUserProvider;
import com.jejo.satchel.dto.TransactionDetails;

import jakarta.transaction.Transactional;

@Service
public class AccountService {

	@Value("${custodian.account.omnibus.address}")
	public String omnibusAddress;
	@Value("${custodian.account.omnibus.coin}")
	public String omnibusCoin;

	@Value("${custodian.webhook.transaction.created}")
	public String webhookTransactionCreated;
	@Value("${custodian.webhook.transaction.updated}")
	public String webhookTransactionStatusUpdated;
	@Value("${custodian.webhook.balance.updated}")
	public String webhookBalanceUpdate;

	@Value("${custodian.transaction.status.completed}")
	public String transactionStatusCompleted;
	@Value("${custodian.transaction.substatus.confirmed}")
	public String transactionSubstatusConfirmed;

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

	@Value("${financial.interest.accrual-period}")
	public Integer interestAccrualPeriod;
	@Value("${financial.interest.apy}")
	public Double interestRate;

	private final CurrentUserProvider currentUserProvider;
	private final AssetCustodianService assetCustodianService;
	private final AccountRepository accountRepository;
	private final FundsTransferRepository fundsTransferRepository;

	public AccountService(CurrentUserProvider currentUserProvider, AssetCustodianService assetCustodianService,
			AccountRepository accountRepository, FundsTransferRepository fundsTransferRepository) {
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
		if (txDetails.getDestinationAddress().equals(omnibusAddress)
				|| !txDetails.getStatus().equals(transactionStatusCompleted)
				|| !txDetails.getSubStatus().equals(transactionSubstatusConfirmed)
				|| fundsTransferRepository.existsByTransactionId(txDetails.getId())) {
			return;
		}
		accountRepository.findByAddressAndCoin(txDetails.getDestinationAddress(), txDetails.getAssetId())
				.ifPresent(account -> {
					fundsTransferRepository.save(FundsTransfer.builder().transactionId(txDetails.getId())
							.account(account).counterpartyAddress(txDetails.getSourceAddress())
							.amount(new BigDecimal(txDetails.getAmountInfo().getAmount()))
							.timestamp(LocalDateTime.now()).isConfirmed(true).isCredited(true).build());
					account.deposit(new BigDecimal(txDetails.getAmountInfo().getAmount()));
					accountRepository.save(account);
				});
		accountRepository.findByAddressAndCoin(txDetails.getSourceAddress(), txDetails.getAssetId())
				.ifPresent(account -> {
					fundsTransferRepository.save(FundsTransfer.builder().transactionId(txDetails.getId())
							.account(account).counterpartyAddress(txDetails.getDestinationAddress())
							.amount(new BigDecimal(txDetails.getAmountInfo().getAmount()).negate())
							.timestamp(LocalDateTime.now()).isConfirmed(true).isCredited(true).build());
					account.deposit(new BigDecimal(txDetails.getAmountInfo().getAmount()).negate());
					accountRepository.save(account);
				});
	}

	private void createVaultWithWallet(String namePrefix, String coin, User user, AccountType type) {
		String accountName = namePrefix + user.getEmail(); // TODO Use user ID instead of email
		Long accountId = assetCustodianService.createVaultAccount(accountName);
		String address = assetCustodianService.createWallet(accountId.toString(), coin);
		accountRepository.save(
				Account.builder().address(address).coin(coin).balance(BigDecimal.ZERO).lockedBalance(BigDecimal.ZERO)
						.openedAt(LocalDateTime.now()).vaultAccountId(accountId).type(type).user(user).build());
	}

}
