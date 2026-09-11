package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.repository.DepositWalletRepository;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * DepositInterestService handles interest accruals for USDC deposits
 * Interest is accrued every 1 hour based on the configured APY
 */
@Slf4j
@Service
public class DepositInterestService {

	@Value("${custodian.account.deposit.coin}")
	private String depositCoin;

	@Value("${financial.interest.apy}")
	private BigDecimal interestRate;

	private final DepositWalletRepository depositWalletRepository;

	public DepositInterestService(DepositWalletRepository depositWalletRepository) {
		this.depositWalletRepository = depositWalletRepository;
	}

	/**
	 * Scheduled task to accrue interest on all active USDC deposits every 1 hour
	 * Interest is calculated as: (balance * hourly_rate)
	 * Hourly rate = APY / (365 * 24)
	 */
	@Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
	@Transactional
	public void accrueInterestOnDeposits() {
		log.info("Starting deposit interest accrual task");

		// Calculate hourly interest rate: APY / (365 days * 24 hours)
		BigDecimal hourlyInterestRate = interestRate.divide(BigDecimal.valueOf(365 * 24), 12, RoundingMode.HALF_UP);

		// Fetch all deposits for USDC coin with available balance > 0
		depositWalletRepository.findByAssetId(depositCoin).forEach(deposit -> {
			BigDecimal availableBalance = deposit.getAvailableBalance();

			// Only accrue interest on available balance (excluding locked collateral)
			if (availableBalance.compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal interestAccrual = availableBalance.multiply(hourlyInterestRate);
				deposit.setAccruedInterest(deposit.getAccruedInterest().add(interestAccrual));

				log.debug("Accrued interest for wallet ID {}: {} (available balance: {}, hourly rate: {})",
						deposit.getId(), interestAccrual, availableBalance, hourlyInterestRate);
			}
		});

		log.info("Completed deposit interest accrual task");
	}
}
