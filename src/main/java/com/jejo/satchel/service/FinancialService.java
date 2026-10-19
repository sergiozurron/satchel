package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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

	@Value("#{${satchel.financial.assets.ltv}}")
	private Map<String, BigDecimal> assetLtv;

	@Value("#{${satchel.financial.assets.liquidation-ltv}}")
	private Map<String, BigDecimal> assetLiquidationLtv;

	@Value("${satchel.financial.accrual-period}")
	private Integer accrualPeriod;
	
	private static final Double MILIS_IN_YEAR = 31_557_600_000.0;
	private static final int COLLATERAL_SCALE = 7;
	private static final int LTV_SCALE = 8;

	private final DepositWalletRepository depositWalletRepository;
	private final LoanRepository loanRepository;
	private final AssetTradingService conversionRateService;

	public FinancialService(DepositWalletRepository depositWalletRepository,
			LoanRepository loanRepository, AssetTradingService conversionRateService) {
		this.depositWalletRepository = depositWalletRepository;
		this.loanRepository = loanRepository;
		this.conversionRateService = conversionRateService;
	}

	@Scheduled(fixedRateString = "${satchel.financial.accrual-period}")
	@Transactional
	public void accrueInterestOnDeposits() {
		depositWalletRepository.findAll().forEach(deposit -> {
			BigDecimal assetDepositRate = assetInterestRates.get(deposit.getAssetId())
					.multiply(BigDecimal.ONE.subtract(assetFinMargins.get(deposit.getAssetId())));
			// 31,557,600,000 milliseconds in a year (365.25 days)
			BigDecimal periodicInterestRate = assetDepositRate.divide(
					BigDecimal.valueOf(MILIS_IN_YEAR / accrualPeriod), 12, RoundingMode.HALF_UP);
			BigDecimal balance = deposit.getBalance();

			// Accrue interest on total balance
			if (balance.compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal interestAccrual = balance.multiply(periodicInterestRate);
				deposit.accrueInterest(interestAccrual);
			}
		});

		log.info("Completed deposit interest accrual task");
	}

	@Scheduled(fixedRateString = "${satchel.financial.accrual-period}")
	@Transactional
	public void accrueInterestOnActiveLoans() {
		loanRepository.findAllUnpaid().forEach(loan -> {
			BigDecimal periodicInterestRate = assetInterestRates.get(loan.getLoanAssetId())
					.divide(BigDecimal.valueOf(MILIS_IN_YEAR), 12, RoundingMode.HALF_UP);
			BigDecimal interest = loan.getAmount().multiply(periodicInterestRate);
			loan.setAccruedInterest(loan.getAccruedInterest().add(interest));
		});

		log.info("Completed loan interest accrual task");
	}

	@Scheduled(fixedRateString = "${satchel.financial.ltv-check-period}")
	@Transactional
	public void liquidateUnhealthyLoans() {
		loanRepository.findAllUnpaid().forEach(loan -> {
			try {
				BigDecimal collateral = loan.getCurrentCollateral();
				BigDecimal debt = loan.getOutstandingAmount();
				String collateralAssetId = loan.getCollateralAssetId();
				BigDecimal maxLtv = assetLiquidationLtv.get(collateralAssetId);
				BigDecimal targetLtv = assetLtv.get(collateralAssetId);
				BigDecimal rate = conversionRateService.getConversionRate(collateralAssetId,
						loan.getLoanAssetId());
				BigDecimal collateralValue = collateral.multiply(rate);
				BigDecimal currentLtv = debt.divide(collateralValue, LTV_SCALE, RoundingMode.HALF_UP);
				if (currentLtv.compareTo(maxLtv) <= 0) {
					return;
				}
				BigDecimal amountToSell = collateralToRestore(debt, collateral, collateralValue, rate,
						targetLtv);
				BigDecimal proceeds = conversionRateService.sell(collateralAssetId, loan.getLoanAssetId(),
						amountToSell);
				loan.liquidate(amountToSell, proceeds);
			} catch (RuntimeException ex) {
				log.warn("Skipping LTV liquidation for loan {}: {}", loan.getId(), ex.getMessage());
			}
		});

		log.info("Completed loan LTV liquidation task");
	}

	private BigDecimal collateralToRestore(BigDecimal debt, BigDecimal collateral,
			BigDecimal collateralValue, BigDecimal rate, BigDecimal targetLtv) {
		BigDecimal denominator = rate.multiply(BigDecimal.ONE.subtract(targetLtv));
		if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
			return collateral;
		}
		BigDecimal numerator = debt.subtract(targetLtv.multiply(collateralValue));
		if (numerator.compareTo(BigDecimal.ZERO) <= 0) {
			return BigDecimal.ZERO;
		}
		BigDecimal amountToSell = numerator.divide(denominator, COLLATERAL_SCALE,
				RoundingMode.CEILING);
		return amountToSell.min(collateral);
	}

}
