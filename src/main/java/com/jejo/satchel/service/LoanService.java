package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.jejo.satchel.dto.CustomLoanRequest;
import com.jejo.satchel.exception.DepositWalletNotFoundByAssetIdException;
import com.jejo.satchel.exception.ExcesiveEquivalentAmountException;
import com.jejo.satchel.exception.InsufficientCollateralException;
import com.jejo.satchel.exception.InsufficientFundsException;
import com.jejo.satchel.exception.InvalidRepaymentAmountException;
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

	private final CurrentUserProvider currentUserProvider;
	private final LoanRepository loanRepository;
	private final DepositWalletRepository depositWalletRepository;
	private final AssetCustodianService assetCustodianService;
	private final AssetPriceService bitcoinPriceService;

	public LoanService(CurrentUserProvider currentUserProvider, LoanRepository loanRepository,
			DepositWalletRepository accountRepository, AssetCustodianService assetCustodianService,
			AssetPriceService bitcoinPriceService) {
		this.currentUserProvider = currentUserProvider;
		this.loanRepository = loanRepository;
		this.depositWalletRepository = accountRepository;
		this.assetCustodianService = assetCustodianService;
		this.bitcoinPriceService = bitcoinPriceService;
	}

	@Transactional
	public void processLoanRequest(CustomLoanRequest loanRequest) {
		User currentUser = currentUserProvider.getCurrentUser();
		depositWalletRepository
				.findByUserIdAndAssetId(currentUser.getId(), loanRequest.getCollateralAssetId())
				.ifPresent(collateralAccount -> {
					if (loanRequest.getCollateralAmount()
							.compareTo(collateralAccount.getAvailableBalance()) > 0) {
						throw new InsufficientCollateralException();
					}
					BigDecimal loanAmount = bitcoinPriceService
							.convertEthToUsdc(loanRequest.getCollateralAmount())
							.multiply(loanRequest.getLtv());
					BigDecimal omnibusBalance = assetCustodianService.getOmnibusBalance();
					if (omnibusBalance.compareTo(loanAmount) < 0) {
						throw new ExcesiveEquivalentAmountException(omnibusBalance);
					}
					// Lock collateral in the collateral wallet
					collateralAccount.setLockedBalance(loanRequest.getCollateralAmount());
					depositWalletRepository.save(collateralAccount);

					// Credit loan amount to user's deposit wallet for the loan asset
					String loanAssetId = loanRequest.getLoanAssetId();
					DepositWallet loanAssetWallet = depositWalletRepository
							.findByUserIdAndAssetId(currentUser.getId(), loanAssetId)
							.orElseThrow(() -> new DepositWalletNotFoundByAssetIdException(
									loanRequest.getLoanAssetId()));
					loanAssetWallet.deposit(loanAmount);
					depositWalletRepository.save(loanAssetWallet);

					LocalDateTime grantedAt = LocalDateTime.now();

					loanRepository.save(Loan.builder().returnedAmount(BigDecimal.ZERO)
							.loanAssetId(loanAssetId).amount(loanAmount)
							.collateralAmount(loanRequest.getCollateralAmount())
							.collateralAssetId(loanRequest.getCollateralAssetId())
							.ltv(loanRequest.getLtv()).interestRate(BigDecimal.valueOf(0.05))
							.status(LoanStatus.ACTIVE)
							.accruedInterest(loanAmount
									.multiply(assetInterestRates.get(loanAssetId))
									.divide(BigDecimal.valueOf(365), RoundingMode.HALF_UP))
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

		// Validate repayment amount
		BigDecimal outstandingAmount = loan.getOutstandingAmount();
		if (repaymentAmount.compareTo(BigDecimal.ZERO) <= 0
				|| repaymentAmount.compareTo(outstandingAmount) > 0) {
			throw new InvalidRepaymentAmountException();
		}

		// Find deposit wallet for the loan asset and verify sufficient balance
		DepositWallet depositWallet = depositWalletRepository
				.findByUserIdAndAssetId(currentUser.getId(), loan.getLoanAssetId()).orElseThrow(
						() -> new DepositWalletNotFoundByAssetIdException(loan.getLoanAssetId()));

		BigDecimal availableBalance = depositWallet.getAvailableBalance();
		if (availableBalance.compareTo(repaymentAmount) < 0) {
			throw new InsufficientFundsException();
		}

		// Debit the deposit wallet
		depositWallet.withdraw(repaymentAmount);
		depositWalletRepository.save(depositWallet);

		// Update loan with repayment
		loan.returnAmount(repaymentAmount);

		// Check if loan is fully paid and update status
		if (loan.isPaidOut()) {
			loan.setStatus(LoanStatus.PAID);
		}

		return loanRepository.save(loan);
	}

}
