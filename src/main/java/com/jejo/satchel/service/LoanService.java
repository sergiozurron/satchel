package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.jejo.satchel.dto.CreateLoanRequest;
import com.jejo.satchel.exception.CollateralTransferException;
import com.jejo.satchel.exception.DepositWalletNotFoundByAssetIdException;
import com.jejo.satchel.exception.InsufficientPoolFundsException;
import com.jejo.satchel.exception.InsufficientCollateralException;
import com.jejo.satchel.exception.InsufficientFundsException;
import com.jejo.satchel.exception.LoanNotFoundException;
import com.jejo.satchel.exception.UnableToConvertAsset;
import com.jejo.satchel.exception.UnhealthyLtvException;
import com.jejo.satchel.exception.LoanAlreadyPaidException;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.Loan;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.LoanRepository;

import jakarta.transaction.Transactional;

@Service
public class LoanService {

	private static final int COLLATERAL_SCALE = 7;

	@Value("#{${satchel.financial.assets.interest-rate}}")
	private Map<String, BigDecimal> assetInterestRates;
	@Value("#{${satchel.financial.assets.ltv}}")
	private Map<String, BigDecimal> assetLtv;
	@Value("${satchel.trading.eth-collateral-address}")
	private String coinbaseEthCollateralAddress;
	@Value("${satchel.custodian.account.omnibus.address}")
	private String omnibusAddress;

	private final CurrentUserProvider currentUserProvider;
	private final LoanRepository loanRepository;
	private final DepositWalletRepository depositWalletRepository;
	private final AssetCustodianService assetCustodianService;
	private final AssetTradingService conversionRateService;

	public LoanService(CurrentUserProvider currentUserProvider, LoanRepository loanRepository,
			DepositWalletRepository accountRepository, AssetCustodianService assetCustodianService,
			AssetTradingService conversionRateService) {
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
			throw new InsufficientPoolFundsException();
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
				.interestRate(assetInterestRates.get(loanAssetId))
				.accruedInterest(BigDecimal.ZERO).grantedAt(grantedAt).user(currentUser).build());

		// Last step: a failed send rolls the loan back. The transfer itself cannot be undone.
		String transactionId = assetCustodianService.createTransaction(collateralAssetId,
				collateralAmount, currentUser.getId(), coinbaseEthCollateralAddress);
		if (transactionId == null) {
			throw new CollateralTransferException();
		}
		return newLoan;
	}

	@Transactional
	public Loan repayLoan(Long loanId, BigDecimal repaymentAmount) {
		User currentUser = currentUserProvider.getCurrentUser();

		// Find the loan by ID and verify ownership
		Loan loan = loanRepository.findByIdAndUserId(loanId, currentUser.getId())
				.orElseThrow(LoanNotFoundException::new);

		if (loan.isPaid()) {
			throw new LoanAlreadyPaidException(loanId);
		}

		// Find deposit wallet for the loan asset and verify sufficient balance
		DepositWallet loanDepositWallet = depositWalletRepository
				.findByUserIdAndAssetId(currentUser.getId(), loan.getLoanAssetId()).get();

		BigDecimal availableBalance = loanDepositWallet.getAvailableBalance();
		if (availableBalance.compareTo(repaymentAmount) < 0) {
			throw new InsufficientFundsException();
		}

		BigDecimal actualRepaymentAmount = repaymentAmount.min(loan.getOutstandingAmount());
		loanDepositWallet.withdraw(actualRepaymentAmount);
		loan.returnAmount(actualRepaymentAmount);

		return loan;
	}

	@Transactional
	public Loan unlockCollateral(Long loanId, BigDecimal amount) {
		User currentUser = currentUserProvider.getCurrentUser();
		Loan loan = loanRepository.findByIdAndUserId(loanId, currentUser.getId())
				.orElseThrow(LoanNotFoundException::new);
		DepositWallet collateralWallet = depositWalletRepository
				.findByUserIdAndAssetId(currentUser.getId(), loan.getCollateralAssetId())
				.orElseThrow(() -> new DepositWalletNotFoundByAssetIdException(
						loan.getCollateralAssetId()));

		if (amount.compareTo(maximumUnlock(loan)) > 0) {
			throw new UnhealthyLtvException();
		}
		// Return the coins before releasing the pledge. A failed send leaves the lock in place.
		conversionRateService.withdraw(loan.getCollateralAssetId(), amount, omnibusAddress);
		loan.unlockCollateral(amount);
		collateralWallet.unlockBalance(amount);
		return loan;
	}

	private BigDecimal maximumUnlock(Loan loan) {
		BigDecimal currentCollateral = loan.getCurrentCollateral();
		if (loan.getOutstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
			return currentCollateral;
		}
		String collateralAssetId = loan.getCollateralAssetId();
		String loanAssetId = loan.getLoanAssetId();
		BigDecimal healthyLtv = assetLtv.get(collateralAssetId);
		BigDecimal rate = conversionRateService.getConversionRate(collateralAssetId,
				loanAssetId);
		if (rate == null || rate.signum() <= 0) {
			throw new UnableToConvertAsset(collateralAssetId, loanAssetId);
		}
		BigDecimal minRemaining = loan.getOutstandingAmount().divide(healthyLtv.multiply(rate),
				COLLATERAL_SCALE, RoundingMode.CEILING);
		return currentCollateral.subtract(minRemaining).max(BigDecimal.ZERO);
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
