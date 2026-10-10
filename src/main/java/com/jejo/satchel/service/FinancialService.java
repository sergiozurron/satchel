package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jejo.satchel.model.LoanStatus;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.LoanRepository;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FinancialService {

	@Value("#{${satchel.financial.assets.interest-rate}}")
	private Map<String, BigDecimal> assetInterestRates;

	@Value("#{${satchel.financial.assets.fin-margin}}")
	private Map<String, BigDecimal> assetFinMargins;
	
	@Value("${satchel.financial.accrual-period}")
	private Integer accrualPeriod;

	@Value("{satchel.name.deposit.prefix}")
	private String depositPrefix;
	
	private static final Double MILIS_IN_YEAR = 31_557_600_000.0;

	private final DepositWalletRepository depositWalletRepository;
	private final LoanRepository loanRepository;
	private final AssetCustodianService assetCustodianService;

	public FinancialService(DepositWalletRepository depositWalletRepository,
			LoanRepository loanRepository, AssetCustodianService assetCustodianService) {
		this.depositWalletRepository = depositWalletRepository;
		this.loanRepository = loanRepository;
		this.assetCustodianService = assetCustodianService;
	}

	@Scheduled(fixedRateString = "${satchel.financial.accrual-period}")
	@Transactional
	public void accrueInterestOnDeposits() {
		depositWalletRepository.findAll().forEach(deposit -> {
			BigDecimal assetDepositRate = assetInterestRates.get(deposit.getAssetId())
					.multiply(BigDecimal.ONE.subtract(assetFinMargins.get(deposit.getAssetId())));
			// 31,557,600,000 milliseconds in a year (365.25 days)
			BigDecimal periodicInterestRate = assetDepositRate
					.divide(BigDecimal.valueOf(MILIS_IN_YEAR / accrualPeriod), 12, RoundingMode.HALF_UP);
			BigDecimal balance = deposit.getBalance();

			// Only accrue interest on total balance
			if (balance.compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal interestAccrual = balance.multiply(periodicInterestRate);
				deposit.setAccruedInterest(deposit.getAccruedInterest().add(interestAccrual));
			}
		});

		log.info("Completed deposit interest accrual task");
	}

	@Scheduled(fixedRateString = "${satchel.financial.accrual-period}")
	@Transactional
	public void accrueInterestOnActiveLoans() {
		loanRepository.findAllByStatus(LoanStatus.ACTIVE).forEach(loan -> {
			BigDecimal periodicInterestRate = assetInterestRates.get(loan.getLoanAssetId())
					.divide(BigDecimal.valueOf(MILIS_IN_YEAR), 12, RoundingMode.HALF_UP);
			BigDecimal interest = loan.getAmount().multiply(periodicInterestRate);
			loan.setAccruedInterest(loan.getAccruedInterest().add(interest));
		});
		
		log.info("Completed loan interest accrual task");
	}

	@Scheduled(initialDelayString = "${satchel.custodian.deposit.sweep.delay}", fixedDelayString = "${satchel.custodian.deposit.sweep.delay}")
	public void sweepDepositsToOmnibus() {
		log.info("Sweeping deposits to omnibus account");
		assetCustodianService.sweepDepositsToOmnibus();
	}
	
	

}
