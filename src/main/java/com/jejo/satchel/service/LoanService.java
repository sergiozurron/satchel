package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
	public Loan processLoanRequest(CreateLoanRequest loanRequest) {
		User currentUser = currentUserProvider.getCurrentUser();
		String collateralAssetId = loanRequest.getCollateralAssetId();
		DepositWallet collateralWallet = depositWalletRepository
				.findByUserIdAndAssetId(currentUser.getId(), collateralAssetId)
				.orElseThrow(() -> new DepositWalletNotFoundByAssetIdException(collateralAssetId));
		String loanAssetId = loanRequest.getLoanAssetId();
		BigDecimal collateralAmount = loanRequest.getCollateralAmount();
		if (collateralAmount.compareTo(collateralWallet.getAvailableBalance()) > 0) {
			throw new InsufficientCollateralException();
		}
		BigDecimal ltvCollateral = assetLtv.get(collateralAssetId).multiply(collateralAmount);
		BigDecimal loanAmount = conversionRateService
				.getConversionRate(collateralAssetId, loanAssetId).multiply(ltvCollateral);
		BigDecimal omnibusBalance = assetCustodianService.getPoolSize(loanAssetId);
		if (omnibusBalance.compareTo(loanAmount) < 0) {
			throw new ExcesiveEquivalentAmountException(omnibusBalance);
		}
		// Lock collateral in the collateral wallet
		collateralWallet.lockBalance(collateralAmount);
		depositWalletRepository.save(collateralWallet);

		// Credit loan amount to user's deposit wallet for the loan asset
		DepositWallet loanAssetWallet = depositWalletRepository
				.findByUserIdAndAssetId(currentUser.getId(), loanAssetId)
				.orElseThrow(() -> new DepositWalletNotFoundByAssetIdException(loanAssetId));
		loanAssetWallet.deposit(loanAmount);
		depositWalletRepository.save(loanAssetWallet);

		LocalDateTime grantedAt = LocalDateTime.now();

		Loan newLoan = loanRepository.save(Loan.builder().returnedAmount(BigDecimal.ZERO)
				.loanAssetId(loanAssetId).amount(loanAmount).collateralAmount(collateralAmount)
				.currentCollateral(collateralAmount).collateralAssetId(collateralAssetId)
				.interestRate(assetInterestRates.get(loanAssetId)).isPaid(false)
				.accruedInterest(BigDecimal.ZERO).grantedAt(grantedAt).user(currentUser).build());

		return newLoan;
	}

	@Transactional
	public Loan repayLoan(Long loanId, BigDecimal repaymentAmount) {
		User currentUser = currentUserProvider.getCurrentUser();

		// Find the loan by ID and verify ownership
		Loan loan = loanRepository.findByIdAndUserId(loanId, currentUser.getId())
				.orElseThrow(LoanNotFoundException::new);

		if (loan.isPaid()) {
			throw new LoanNotActiveException();
		}

		// Find deposit wallet for the loan asset and verify sufficient balance
		DepositWallet loanDepositWallet = depositWalletRepository
				.findByUserIdAndAssetId(currentUser.getId(), loan.getLoanAssetId()).get();

		BigDecimal availableBalance = loanDepositWallet.getAvailableBalance();
		if (availableBalance.compareTo(repaymentAmount) < 0) {
			throw new InsufficientFundsException();
		}

		BigDecimal actualRepaymentAmount = repaymentAmount.min(loan.getOutstandingAmount());
		// Debit the deposit wallet
		loanDepositWallet.withdraw(actualRepaymentAmount);

		// Find deposit wallet for the loan collateral and unlock balance
		DepositWallet collateralDepositWallet = depositWalletRepository
				.findByUserIdAndAssetId(currentUser.getId(), loan.getCollateralAssetId()).get();
		
		collateralDepositWallet.unlockBalance(loan.getCollateralAmount());

		// Update loan with repayment
		loan.returnAmount(actualRepaymentAmount);

		if (loan.isPaidOut()) {
			loan.setPaid(true);
		}

		return loan;
	}

	@Transactional
	public Loan getUserLoan(Long loanId) {
		User currentUser = currentUserProvider.getCurrentUser();
		return loanRepository.findByIdAndUserId(loanId, currentUser.getId())
				.orElseThrow(LoanNotFoundException::new);
	}

	@Transactional
	public List<Loan> getAllUserLoans() {
		User currentUser = currentUserProvider.getCurrentUser();
		return loanRepository.findAllByUserIdOrderByGrantedAtDesc(currentUser.getId());
	}

}
