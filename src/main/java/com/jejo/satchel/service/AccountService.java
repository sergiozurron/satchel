package com.jejo.satchel.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jejo.satchel.exception.AccountsAlreadyCreatedException;
import com.jejo.satchel.model.CollateralAccount;
import com.jejo.satchel.model.DepositAccount;
import com.jejo.satchel.model.RepaymentAccount;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.DepositAccountRepository;
import com.jejo.satchel.repository.RepaymentAccountRepository;
import com.jejo.satchel.repository.CollateralAccountRepository;
import com.jejo.satchel.util.CurrentUserProvider;

import jakarta.transaction.Transactional;

@Service
public class AccountService {

	@Value("${financial.interest.accrual-period}")
	private Integer interestAccrualPeriod;
	@Value("${financial.interest.apy}")
	private Double interestRate;

	private final DepositAccountRepository accountRepository;
	private final CurrentUserProvider currentUserProvider;
	private final AssetCustodianService assetCustodianService;
	private final CollateralAccountRepository collateralAccountRepository;
	private final RepaymentAccountRepository repaymentAccountRepository;
	private final DepositAccountRepository depositAccountRepository;

	public AccountService(DepositAccountRepository accountRepository, CurrentUserProvider currentUserProvider,
			AssetCustodianService assetCustodianService, CollateralAccountRepository collateralAccountRepository,
			RepaymentAccountRepository repaymentAccountRepository, DepositAccountRepository depositAccountRepository) {
		this.accountRepository = accountRepository;
		this.currentUserProvider = currentUserProvider;
		this.assetCustodianService = assetCustodianService;
		this.collateralAccountRepository = collateralAccountRepository;
		this.repaymentAccountRepository = repaymentAccountRepository;
		this.depositAccountRepository = depositAccountRepository;
	}

	@Transactional
	public void createUserAccounts() {
		User user = currentUserProvider.getCurrentUser();
		collateralAccountRepository.findByUserId(user.getId()).ifPresent(account -> {
			throw new AccountsAlreadyCreatedException(user.getId());
		});
		// Create collateral vault with BTC wallet
		Long collateralAccountId = assetCustodianService
				.createCollateralVaultAccount(user.getEmail());
		String collateralAddress = assetCustodianService.createBTCWallet(collateralAccountId.toString());
		collateralAccountRepository.save(CollateralAccount.builder().address(collateralAddress).coin("BTC").balance(0.0)
				.lockedBalance(0.0).user(user).build());

		// Create repayments vault with USDC wallet
		Long repaymentAccountId = assetCustodianService
				.createRepaymentVaultAccount(user.getEmail());
		String repaymentAddress = assetCustodianService.createUSDCWallet(repaymentAccountId.toString());
		repaymentAccountRepository.save(
				RepaymentAccount.builder().address(repaymentAddress).coin("USDC").balance(0.0).user(user).build());

		// Create deposit vault with USDC wallet
		Long depositAccountId = assetCustodianService
				.createDepositVaultAccount(user.getEmail());
		String depositAddress = assetCustodianService.createUSDCWallet(depositAccountId.toString());
		depositAccountRepository.save(DepositAccount.builder().address(depositAddress).coin("USDC").balance(0.0)
				.averageBalance(0.0).openedAt(LocalDateTime.now()).user(user).build());
	}

	@Scheduled(cron = "0 0 0 * * ?") // Runs daily at midnight
	public void updateAccounts() {
		// Update average balances and apply interest
		accountRepository.findAll().forEach(account -> {
			// Calculate interest based on the number of days since the account was opened
			Long daysSinceLastInterest = account.daysSinceOpened() % interestAccrualPeriod;
			double newAverageBalance = (account.getBalance() + daysSinceLastInterest * account.getAverageBalance())
					/ (daysSinceLastInterest + 1);
			account.setAverageBalance(newAverageBalance);
			if (daysSinceLastInterest == 0) {
				// Apply interest if it's the accrual period
				double interest = newAverageBalance * (interestRate / 12);
				account.deposit(interest);
			}
			accountRepository.save(account);
		});

	}

}
