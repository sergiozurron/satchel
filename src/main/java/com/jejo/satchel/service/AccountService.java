package com.jejo.satchel.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jejo.satchel.repository.AccountRepository;

@Service
public class AccountService {

	@Value("${financial.interest.accrual-period}")
	private Integer interestAccrualPeriod;
	@Value("${financial.interest.apy}")
	private Double interestRate;
	
	private final AccountRepository accountRepository;
	
	public AccountService(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}
	
	@Scheduled(cron = "0 0 0 * * ?") // Runs daily at midnight
	public void updateAccounts() {
		// Update average balances and apply interest
		accountRepository.findAll().forEach(account -> {
			// Calculate interest based on the number of days since the account was opened
			Long daysSinceLastInterest = account.daysSinceOpened() % interestAccrualPeriod;
			double newAverageBalance = (account.getBalance() + daysSinceLastInterest * account.getAverageBalance()) / (daysSinceLastInterest + 1);
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
