package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.jejo.satchel.dto.CreateLoanRequest;
import com.jejo.satchel.exception.DepositWalletNotFoundByAssetIdException;
import com.jejo.satchel.exception.ExcesiveEquivalentAmountException;
import com.jejo.satchel.exception.InsufficientCollateralException;
import com.jejo.satchel.exception.InsufficientFundsException;
import com.jejo.satchel.exception.LoanNotFoundException;
import com.jejo.satchel.exception.LoanNotActiveException;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.Loan;
import com.jejo.satchel.model.LoanStatus;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.LoanRepository;
import com.jejo.satchel.util.CurrentUserProvider;

import jakarta.transaction.Transactional;

@Service
public class LoanService {

	@Value("#{${satchel.financial.assets.interest-rate}}")
	private Map<String, BigDecimal> assetInterestRates;
	@Value("#{${satchel.financial.assets.ltv}}")
	private Map<String, BigDecimal> assetLtv;

	private final CurrentUserProvider currentUserProvider;
	private final LoanRepository loanRepository;
	private final DepositWalletRepository depositWalletRepository;
	private final AssetCustodianService assetCustodianService;
	private final ConversionRateService conversionRateService;

	public LoanService(CurrentUserProvider currentUserProvider, LoanRepository loanRepository,
			DepositWalletRepository accountRepository, AssetCustodianService assetCustodianService,
			ConversionRateService conversionRateService) {
		this.currentUserProvider = currentUserProvider;
		this.loanRepository = loanRepository;
		this.depositWalletRepository = accountRepository;
		this.assetCustodianService = assetCustodianService;
		this.conversionRateService = conversionRateService;
	}

	@Transactional
	public void processLoanRequest(CreateLoanRequest loanRequest) {
		User currentUser = currentUserProvider.getCurrentUser();
		depositWalletRepository
				.findByUserIdAndAssetId(currentUser.getId(), loanRequest.getCollateralAssetId())
				.ifPresent(collateralAccount -> {
					BigDecimal collateralAmount = loanRequest.getCollateralAmount();
					if (collateralAmount.compareTo(collateralAccount.getAvailableBalance()) > 0) {
						throw new InsufficientCollateralException();
					}
					String loanAssetId = loanRequest.getLoanAssetId();
					String collateralAssetId = loanRequest.getCollateralAssetId();
					BigDecimal ltvCollateral = assetLtv.get(collateralAssetId)
							.multiply(collateralAmount);
					BigDecimal loanAmount = conversionRateService.convertCurrency(ltvCollateral,
							collateralAssetId, loanAssetId);
					BigDecimal omnibusBalance = assetCustodianService
							.getOmnibusBalance(loanAssetId);
					if (omnibusBalance.compareTo(loanAmount) < 0) {
						throw new ExcesiveEquivalentAmountException(omnibusBalance);
					}
					// Lock collateral in the collateral wallet
					collateralAccount.lockBalance(collateralAmount);
					depositWalletRepository.save(collateralAccount);

					// Credit loan amount to user's deposit wallet for the loan asset
					DepositWallet loanAssetWallet = depositWalletRepository
							.findByUserIdAndAssetId(currentUser.getId(), loanAssetId).orElseThrow(
									() -> new DepositWalletNotFoundByAssetIdException(loanAssetId));
					loanAssetWallet.deposit(loanAmount);
					depositWalletRepository.save(loanAssetWallet);

					LocalDateTime grantedAt = LocalDateTime.now();

					loanRepository.save(Loan.builder().returnedAmount(BigDecimal.ZERO)
							.loanAssetId(loanAssetId).amount(loanAmount)
							.collateralAmount(collateralAmount).collateralAssetId(collateralAssetId)
							.interestRate(assetInterestRates.get(loanAssetId))
							.status(LoanStatus.ACTIVE).accruedInterest(BigDecimal.ZERO)
							.grantedAt(grantedAt).user(currentUser).build());
				});
	}

	@Transactional
	public Loan repayLoan(Long loanId, BigDecimal repaymentAmount) {
		User currentUser = currentUserProvider.getCurrentUser();

		// Find the loan by ID and verify ownership
		Loan loan = loanRepository.findByIdAndUserId(loanId, currentUser.getId())
				.orElseThrow(LoanNotFoundException::new);

		// Validate loan is active
		if (!loan.getStatus().equals(LoanStatus.ACTIVE)) {
			throw new LoanNotActiveException("Loan status is " + loan.getStatus());
		}

		// Find deposit wallet for the loan asset and verify sufficient balance
		DepositWallet depositWallet = depositWalletRepository
				.findByUserIdAndAssetId(currentUser.getId(), loan.getLoanAssetId()).orElseThrow(
						() -> new DepositWalletNotFoundByAssetIdException(loan.getLoanAssetId()));

		BigDecimal availableBalance = depositWallet.getAvailableBalance();
		if (availableBalance.compareTo(repaymentAmount) < 0) {
			throw new InsufficientFundsException();
		}

		BigDecimal actualRepaymentAmount = repaymentAmount.min(loan.getOutstandingAmount());
		// Debit the deposit wallet
		depositWallet.withdraw(actualRepaymentAmount);
		depositWalletRepository.save(depositWallet);

		// Update loan with repayment
		loan.returnAmount(actualRepaymentAmount);

		// Check if loan is fully paid and update status
		if (loan.isPaidOut()) {
			loan.setStatus(LoanStatus.PAID);
		}

		return loanRepository.save(loan);
	}

}
