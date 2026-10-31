package com.jejo.satchel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.jejo.satchel.dto.CreateLoanRequest;
import com.jejo.satchel.exception.CollateralTransferException;
import com.jejo.satchel.exception.DepositWalletNotFoundByAssetIdException;
import com.jejo.satchel.exception.InsufficientPoolFundsException;
import com.jejo.satchel.exception.InsufficientFundsException;
import com.jejo.satchel.exception.InsufficientCollateralException;
import com.jejo.satchel.exception.LoanAlreadyPaidException;
import com.jejo.satchel.exception.LoanNotFoundException;
import com.jejo.satchel.exception.TradingOrderException;
import com.jejo.satchel.exception.UnhealthyLtvException;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.Loan;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.LoanRepository;

/**
 * Unit tests for {@link LoanService}. Combines tests for processLoanRequest,
 * repayLoan, and unlockCollateral.
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

	private static final Long USER_ID = 1L;
	private static final String COLLATERAL_ASSET_ID = "TEST_BTC";
	private static final String LOAN_ASSET_ID = "TEST_USDT";
	private static final String COINBASE_ADDRESS = "0xcoinbase";
	private static final String OMNIBUS_ADDRESS = "0xomnibus";
	private final Map<String, BigDecimal> assetInterestRates = Map.of("TEST_USDT",
			BigDecimal.valueOf(0.05));

	@Mock
	private CurrentUserProvider currentUserProvider;

	@Mock
	private LoanRepository loanRepository;

	@Mock
	private DepositWalletRepository depositWalletRepository;

	@Mock
	private AssetCustodianService assetCustodianService;

	@Mock
	private AssetTradingService conversionRateService;

	private User currentUser;

	@InjectMocks
	private LoanService loanService;

	@BeforeEach
	void setUp() {
		currentUser = User.builder().id(USER_ID).build();
		loanService = new LoanService(currentUserProvider, loanRepository, depositWalletRepository,
				assetCustodianService, conversionRateService);

		ReflectionTestUtils.setField(loanService, "assetInterestRates", assetInterestRates);
		ReflectionTestUtils.setField(loanService, "assetLtv",
				Map.of(COLLATERAL_ASSET_ID, BigDecimal.valueOf(0.5)));
		ReflectionTestUtils.setField(loanService, "coinbaseEthCollateralAddress", COINBASE_ADDRESS);
		ReflectionTestUtils.setField(loanService, "omnibusAddress", OMNIBUS_ADDRESS);
		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
	}

	// ========== processLoanRequest tests ==========

	@Test
	void processLoanRequest_locksCollateralCreditsWalletAndSavesLoan() {
		BigDecimal collateralAmount = BigDecimal.valueOf(2);

		CreateLoanRequest loanRequest = new CreateLoanRequest();
		loanRequest.setCollateralAssetId(COLLATERAL_ASSET_ID);
		loanRequest.setLoanAssetId(LOAN_ASSET_ID);
		loanRequest.setCollateralAmount(collateralAmount);

		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, COLLATERAL_ASSET_ID))
				.thenReturn(Optional.of(DepositWallet.builder().balance(BigDecimal.TEN)
						.lockedBalance(BigDecimal.ZERO).build()));
		when(conversionRateService.getConversionRate(COLLATERAL_ASSET_ID, LOAN_ASSET_ID))
				.thenReturn(BigDecimal.TEN);
		when(assetCustodianService.getPoolSize(LOAN_ASSET_ID))
				.thenReturn(BigDecimal.valueOf(1_000_000));
		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, LOAN_ASSET_ID))
				.thenReturn(Optional.of(DepositWallet.builder().balance(BigDecimal.TEN)
						.lockedBalance(BigDecimal.ZERO).build()));
		Loan savedLoan = Loan.builder().id(1L).build();
		when(loanRepository.save(any(Loan.class))).thenReturn(savedLoan);
		when(assetCustodianService.createTransaction(COLLATERAL_ASSET_ID, collateralAmount, USER_ID,
				COINBASE_ADDRESS)).thenReturn("tx-1");

		Loan result = loanService.processLoanRequest(loanRequest);

		assertThat(result).isSameAs(savedLoan);
		verify(depositWalletRepository, times(2)).save(any());
		ArgumentCaptor<Loan> loanCaptor = ArgumentCaptor.forClass(Loan.class);
		verify(loanRepository).save(loanCaptor.capture());
		assertThat(loanCaptor.getValue().getCurrentCollateral()).isEqualByComparingTo(collateralAmount);
		assertThat(loanCaptor.getValue().isPaid()).isFalse();
		verify(assetCustodianService).createTransaction(COLLATERAL_ASSET_ID, collateralAmount, USER_ID,
				COINBASE_ADDRESS);
	}

	@Test
	void processLoanRequest_shouldThrowWalletNotFoundException_whenWalletNotFoundByAssetAndUser() {
		CreateLoanRequest loanRequest = new CreateLoanRequest();
		loanRequest.setCollateralAssetId(COLLATERAL_ASSET_ID);

		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, COLLATERAL_ASSET_ID))
				.thenReturn(Optional.empty());

		assertThrows(DepositWalletNotFoundByAssetIdException.class,
				() -> loanService.processLoanRequest(loanRequest));

		verify(depositWalletRepository, never()).save(any(DepositWallet.class));
		verify(loanRepository, never()).save(any(Loan.class));
		verify(conversionRateService, never()).getConversionRate(any(), any());
		verify(assetCustodianService, never()).getPoolSize(LOAN_ASSET_ID);
		verify(assetCustodianService, never()).createTransaction(any(), any(), any(), any());
	}

	@Test
	void processLoanRequest_collateralAmountExceedsAvailableBalance_throwsInsufficientCollateral() {
		BigDecimal collateralAmount = BigDecimal.valueOf(11);
		CreateLoanRequest loanRequest = new CreateLoanRequest();
		loanRequest.setCollateralAmount(collateralAmount);
		loanRequest.setCollateralAssetId(COLLATERAL_ASSET_ID);

		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, COLLATERAL_ASSET_ID))
				.thenReturn(Optional.of(DepositWallet.builder().balance(BigDecimal.TEN)
						.lockedBalance(BigDecimal.ZERO).build()));

		assertThrows(InsufficientCollateralException.class,
				() -> loanService.processLoanRequest(loanRequest));

		verify(depositWalletRepository, never()).save(any(DepositWallet.class));
		verify(loanRepository, never()).save(any(Loan.class));
		verify(conversionRateService, never()).getConversionRate(any(), any());
		verify(assetCustodianService, never()).createTransaction(any(), any(), any(), any());
	}

	@Test
	void processLoanRequest_omnibusBalanceInsufficient_throwsInsufficientPoolFunds() {
		BigDecimal collateralAmount = BigDecimal.valueOf(2);
		BigDecimal ltv = BigDecimal.valueOf(0.5);
		BigDecimal loanAmount = BigDecimal.valueOf(4000);
		BigDecimal expectedLoanAmount = loanAmount.multiply(ltv); // 2000
		BigDecimal omnibusBalance = BigDecimal.valueOf(1000); // less than loan amount

		CreateLoanRequest loanRequest = new CreateLoanRequest();
		loanRequest.setCollateralAssetId(COLLATERAL_ASSET_ID);
		loanRequest.setLoanAssetId(LOAN_ASSET_ID);
		loanRequest.setCollateralAmount(collateralAmount);

		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, COLLATERAL_ASSET_ID))
				.thenReturn(Optional.of(DepositWallet.builder().balance(BigDecimal.TEN)
						.lockedBalance(BigDecimal.ZERO).build()));
		when(conversionRateService.getConversionRate(COLLATERAL_ASSET_ID, LOAN_ASSET_ID))
				.thenReturn(loanAmount);
		when(assetCustodianService.getPoolSize(LOAN_ASSET_ID)).thenReturn(omnibusBalance);

		InsufficientPoolFundsException ex = assertThrows(InsufficientPoolFundsException.class,
				() -> loanService.processLoanRequest(loanRequest));

		assertThat(ex).isNotNull();

		// Collateral must not be locked/saved when the request fails downstream
		verify(depositWalletRepository, never()).save(any(DepositWallet.class));
		verify(loanRepository, never()).save(any(Loan.class));
		verify(assetCustodianService, never()).createTransaction(any(), any(), any(), any());

		// Sanity check on the amount comparison used to trigger the exception
		assertThat(omnibusBalance).isLessThan(expectedLoanAmount);
	}

	@Test
	void processLoanRequest_loanAssetWalletMissing_throwsDepositWalletNotFound() {
		BigDecimal collateralAmount = BigDecimal.valueOf(2);
		BigDecimal loanAmount = BigDecimal.valueOf(4000);

		CreateLoanRequest loanRequest = new CreateLoanRequest();
		loanRequest.setCollateralAssetId(COLLATERAL_ASSET_ID);
		loanRequest.setLoanAssetId(LOAN_ASSET_ID);
		loanRequest.setCollateralAmount(collateralAmount);

		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, COLLATERAL_ASSET_ID))
				.thenReturn(Optional.of(DepositWallet.builder().balance(BigDecimal.TEN)
						.lockedBalance(BigDecimal.ZERO).build()));
		when(conversionRateService.getConversionRate(COLLATERAL_ASSET_ID, LOAN_ASSET_ID))
				.thenReturn(loanAmount);
		when(assetCustodianService.getPoolSize(LOAN_ASSET_ID))
				.thenReturn(BigDecimal.valueOf(1_000_000));

		// Collateral is locked before the loan-asset wallet lookup happens
		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, LOAN_ASSET_ID))
				.thenReturn(Optional.empty());

		assertThrows(DepositWalletNotFoundByAssetIdException.class,
				() -> loanService.processLoanRequest(loanRequest));

		// Collateral was already locked+saved before the failure, per current
		// implementation (no rollback of the in-memory mutation itself, though the
		// @Transactional annotation will roll back the DB transaction).
		verify(depositWalletRepository).save(any(DepositWallet.class));
		verify(loanRepository, never()).save(any(Loan.class));
		verify(assetCustodianService, never()).createTransaction(any(), any(), any(), any());
	}

	@Test
	void processLoanRequest_nullCollateralTransfer_throwsCollateralTransferException() {
		BigDecimal collateralAmount = BigDecimal.valueOf(2);
		CreateLoanRequest loanRequest = new CreateLoanRequest();
		loanRequest.setCollateralAssetId(COLLATERAL_ASSET_ID);
		loanRequest.setLoanAssetId(LOAN_ASSET_ID);
		loanRequest.setCollateralAmount(collateralAmount);

		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, COLLATERAL_ASSET_ID))
				.thenReturn(Optional.of(DepositWallet.builder().balance(BigDecimal.TEN)
						.lockedBalance(BigDecimal.ZERO).build()));
		when(conversionRateService.getConversionRate(COLLATERAL_ASSET_ID, LOAN_ASSET_ID))
				.thenReturn(BigDecimal.TEN);
		when(assetCustodianService.getPoolSize(LOAN_ASSET_ID))
				.thenReturn(BigDecimal.valueOf(1_000_000));
		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, LOAN_ASSET_ID))
				.thenReturn(Optional.of(DepositWallet.builder().balance(BigDecimal.TEN)
						.lockedBalance(BigDecimal.ZERO).build()));
		when(loanRepository.save(any(Loan.class))).thenReturn(Loan.builder().id(1L).build());
		when(assetCustodianService.createTransaction(COLLATERAL_ASSET_ID, collateralAmount, USER_ID,
				COINBASE_ADDRESS)).thenReturn(null);

		assertThatThrownBy(() -> loanService.processLoanRequest(loanRequest))
				.isInstanceOf(CollateralTransferException.class);

		verify(loanRepository).save(any(Loan.class));
	}

	// ========== repayLoan helpers ==========

	private User createTestUser() {
		return User.builder().id(1L).firstName("John").lastName("Doe").email("john@example.com")
				.password("password123").verified(true).build();
	}

	private Loan createActiveLoan(User user) {
		return Loan.builder().id(1L).loanAssetId("USDC").collateralAssetId("ETH")
				.returnedAmount(BigDecimal.ZERO).amount(new BigDecimal("500.0000000"))
				.collateralAmount(new BigDecimal("0.5000000"))
				.currentCollateral(new BigDecimal("0.5000000"))
				.interestRate(new BigDecimal("0.050000"))
				.accruedInterest(new BigDecimal("5.0000000")).grantedAt(LocalDateTime.now())
				.user(user).build();
	}

	private DepositWallet createDepositWallet(User user) {
		return DepositWallet.builder().id(1L).address("0x1234567890abcdef").assetId("USDC")
				.balance(new BigDecimal("1000.0000000")).lockedBalance(BigDecimal.ZERO)
				.openedAt(LocalDateTime.now()).user(user).build();
	}

	private void stubLoanWallet(DepositWallet loanWallet) {
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC"))
				.thenReturn(Optional.of(loanWallet));
	}

	// ========== repayLoan tests ==========

	@Test
	void repayLoan_shouldSuccessfullyRepayPartialAmount() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		DepositWallet loanWallet = createDepositWallet(user);
		BigDecimal repaymentAmount = new BigDecimal("100.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		stubLoanWallet(loanWallet);

		// When
		Loan result = loanService.repayLoan(1L, repaymentAmount);

		// Then
		assertThat(result.getReturnedAmount()).isEqualTo(repaymentAmount);
		assertThat(result.getOutstandingAmount()).isEqualTo(new BigDecimal("405.0000000"));
		assertThat(result.isPaid()).isFalse();
		assertThat(result.getCurrentCollateral()).isEqualByComparingTo("0.5");
		assertThat(loanWallet.getBalance()).isEqualByComparingTo("900");

		verify(loanRepository).findByIdAndUserId(1L, 1L);
		verify(depositWalletRepository).findByUserIdAndAssetId(1L, "USDC");
		verify(depositWalletRepository, never()).findByUserIdAndAssetId(1L, "ETH");
		verify(depositWalletRepository, never()).save(any(DepositWallet.class));
		verify(loanRepository, never()).save(any(Loan.class));
	}

	@Test
	void repayLoan_shouldSuccessfullyRepayFullAmount() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		DepositWallet loanWallet = createDepositWallet(user);
		BigDecimal repaymentAmount = new BigDecimal("600.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		stubLoanWallet(loanWallet);

		// When
		Loan result = loanService.repayLoan(1L, repaymentAmount);

		// Then — requested 600 is capped at the 505 outstanding
		assertThat(result.getReturnedAmount()).isEqualTo(new BigDecimal("505.0000000"));
		assertThat(result.isPaid()).isTrue();
		assertThat(result.getCurrentCollateral()).isEqualByComparingTo("0.5");
		assertThat(loanWallet.getBalance()).isEqualByComparingTo("495");

		verify(loanRepository, never()).save(any(Loan.class));
		verify(depositWalletRepository, never()).save(any(DepositWallet.class));
	}

	@Test
	void repayLoan_shouldThrowLoanNotFoundException_whenLoanDoesNotExist() {
		// Given
		User user = createTestUser();
		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(99L, new BigDecimal("100.0000000")))
				.isInstanceOf(LoanNotFoundException.class).hasMessage("Loan not found");

		verify(loanRepository).findByIdAndUserId(99L, 1L);
	}

	@Test
	void repayLoan_shouldThrowLoanNotActiveException_whenLoanIsPaid() {
		// Given
		User user = createTestUser();
		Loan paidLoan = createActiveLoan(user);
		paidLoan.setReturnedAmount(new BigDecimal("505.0000000"));

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(paidLoan));

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(1L, new BigDecimal("100.0000000")))
				.isInstanceOf(LoanAlreadyPaidException.class);
	}

	@Test
	void repayLoan_shouldThrowNoSuchElementException_whenLoanWalletNotFound() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC"))
				.thenReturn(Optional.empty());

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(1L, new BigDecimal("100.0000000")))
				.isInstanceOf(NoSuchElementException.class);

		assertThat(activeLoan.getReturnedAmount()).isEqualByComparingTo("0");
		assertThat(activeLoan.isPaid()).isFalse();
		verify(depositWalletRepository, never()).findByUserIdAndAssetId(1L, "ETH");
	}

	@Test
	void repayLoan_shouldRepayWhenCollateralWalletIsMissing() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		DepositWallet loanWallet = createDepositWallet(user);

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		stubLoanWallet(loanWallet);

		// When
		Loan result = loanService.repayLoan(1L, new BigDecimal("100.0000000"));

		// Then
		assertThat(result.getReturnedAmount()).isEqualByComparingTo("100");
		assertThat(result.getCurrentCollateral()).isEqualByComparingTo("0.5");
		assertThat(loanWallet.getBalance()).isEqualByComparingTo("900");
		verify(depositWalletRepository, never()).findByUserIdAndAssetId(1L, "ETH");
	}

	@Test
	void repayLoan_shouldThrowInsufficientFundsException_whenBalanceInsufficient() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		DepositWallet wallet = createDepositWallet(user);
		wallet.setBalance(new BigDecimal("50.0000000"));
		BigDecimal repaymentAmount = new BigDecimal("100.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC"))
				.thenReturn(Optional.of(wallet));

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(1L, repaymentAmount))
				.isInstanceOf(InsufficientFundsException.class);

		assertThat(wallet.getBalance()).isEqualByComparingTo("50");
		assertThat(activeLoan.getReturnedAmount()).isEqualByComparingTo("0");
		verify(depositWalletRepository, never()).findByUserIdAndAssetId(1L, "ETH");
	}

	@Test
	void repayLoan_shouldDebitLoanWalletWithoutUnlockingCollateral() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		DepositWallet loanWallet = createDepositWallet(user);
		BigDecimal repaymentAmount = new BigDecimal("100.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		stubLoanWallet(loanWallet);

		// When
		Loan result = loanService.repayLoan(1L, repaymentAmount);

		// Then
		assertThat(loanWallet.getBalance()).isEqualByComparingTo("900");
		assertThat(result.getCurrentCollateral()).isEqualByComparingTo("0.5");
		assertThat(result.getCollateralAmount()).isEqualByComparingTo("0.5");
		verify(depositWalletRepository, never()).save(any(DepositWallet.class));
		verify(depositWalletRepository, never()).findByUserIdAndAssetId(1L, "ETH");
	}

	@Test
	void repayLoan_shouldUpdateLoanReturnedAmount() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		DepositWallet loanWallet = createDepositWallet(user);
		BigDecimal repaymentAmount = new BigDecimal("150.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		stubLoanWallet(loanWallet);

		// When
		Loan result = loanService.repayLoan(1L, repaymentAmount);

		// Then
		assertThat(result.getReturnedAmount()).isEqualTo(repaymentAmount);
		assertThat(result.getCurrentCollateral()).isEqualByComparingTo("0.5");
	}

	@Test
	void repayLoan_shouldVerifyLoanOwnership() {
		// Given
		User user = createTestUser();
		user.setId(1L);
		Loan activeLoan = createActiveLoan(user);

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));

		// When - The method verifies ownership by calling findByIdAndUserId
		try {
			loanService.repayLoan(1L, new BigDecimal("100.0000000"));
		} catch (Exception e) {
			// Expected to fail at wallet check, but ownership was verified
		}

		// Then
		verify(loanRepository).findByIdAndUserId(1L, 1L);
	}

	@Test
	void repayLoan_shouldTransitionLoanToPaidWhenFullyRepaid() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		DepositWallet loanWallet = createDepositWallet(user);
		BigDecimal repaymentAmount = new BigDecimal("600.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		stubLoanWallet(loanWallet);

		// When
		Loan result = loanService.repayLoan(1L, repaymentAmount);

		// Then
		assertThat(result.isPaid()).isTrue();
		assertThat(result.getOutstandingAmount()).isEqualByComparingTo("0");
		assertThat(result.getCurrentCollateral()).isEqualByComparingTo("0.5");
	}

	@Test
	void repayLoan_shouldAllowMultipleRepayments() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		DepositWallet loanWallet = createDepositWallet(user);

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		stubLoanWallet(loanWallet);

		// When - First repayment
		BigDecimal firstRepayment = new BigDecimal("100.0000000");
		Loan resultAfterFirst = loanService.repayLoan(1L, firstRepayment);

		// Then
		assertThat(resultAfterFirst.getReturnedAmount()).isEqualTo(firstRepayment);
		assertThat(resultAfterFirst.getOutstandingAmount())
				.isEqualTo(new BigDecimal("405.0000000"));
		assertThat(resultAfterFirst.isPaid()).isFalse();
		assertThat(resultAfterFirst.getCurrentCollateral()).isEqualByComparingTo("0.5");
		assertThat(loanWallet.getBalance()).isEqualByComparingTo("900");

		// Given - Update loan for second repayment
		activeLoan.setReturnedAmount(firstRepayment);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));

		// When
		BigDecimal secondRepayment = new BigDecimal("200.0000000");
		Loan resultAfterSecond = loanService.repayLoan(1L, secondRepayment);

		// Then
		assertThat(resultAfterSecond.getReturnedAmount()).isEqualTo(new BigDecimal("300.0000000"));
		assertThat(resultAfterSecond.getOutstandingAmount())
				.isEqualTo(new BigDecimal("205.0000000"));
		assertThat(resultAfterSecond.getCurrentCollateral()).isEqualByComparingTo("0.5");
		assertThat(loanWallet.getBalance()).isEqualByComparingTo("700");
	}

	@Test
	void repayLoan_shouldHandleSmallRepaymentAmounts() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		DepositWallet loanWallet = createDepositWallet(user);
		BigDecimal repaymentAmount = new BigDecimal("0.0001000");

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		stubLoanWallet(loanWallet);

		// When
		Loan result = loanService.repayLoan(1L, repaymentAmount);

		// Then
		assertThat(result.getReturnedAmount()).isEqualTo(repaymentAmount);
		assertThat(result.getOutstandingAmount()).isEqualTo(new BigDecimal("504.9999000"));
		assertThat(result.isPaid()).isFalse();
		assertThat(result.getCurrentCollateral()).isEqualByComparingTo("0.5000000");
		assertThat(loanWallet.getBalance()).isEqualByComparingTo("999.9999");
	}

	@Test
	void repayLoan_shouldConsiderLockedBalanceWhenCalculatingAvailable() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		activeLoan.setAmount(new BigDecimal("1000.0000000")); // Loan amount

		DepositWallet wallet = createDepositWallet(user);
		wallet.setLockedBalance(new BigDecimal("100.0000000")); // Locked balance

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC"))
				.thenReturn(Optional.of(wallet));

		// When & Then - Available balance is 900 (1000 - 100 locked), so 950 repayment
		// should fail
		BigDecimal repaymentAmount = new BigDecimal("950.0000000");
		assertThatThrownBy(() -> loanService.repayLoan(1L, repaymentAmount))
				.isInstanceOf(InsufficientFundsException.class);
	}

	// ========== unlockCollateral tests ==========

	private void setHealthyLtv(String collateralAssetId, String ltv) {
		ReflectionTestUtils.setField(loanService, "assetLtv",
				Map.of(collateralAssetId, new BigDecimal(ltv)));
	}

	private Loan pledgeLoan(String outstanding, String collateral) {
		return Loan.builder().id(1L).loanAssetId("USDC_ETH_TEST5_AN74")
				.collateralAssetId("ETH_TEST5").returnedAmount(BigDecimal.ZERO)
				.amount(new BigDecimal(outstanding)).collateralAmount(new BigDecimal(collateral))
				.currentCollateral(new BigDecimal(collateral))
				.interestRate(new BigDecimal("0.050000")).accruedInterest(BigDecimal.ZERO)
				.grantedAt(LocalDateTime.now()).user(createTestUser()).build();
	}

	private DepositWallet pledgedWallet(String locked) {
		return DepositWallet.builder().id(2L).address("0xcollateral").assetId("ETH_TEST5")
				.balance(new BigDecimal("2.0000000")).lockedBalance(new BigDecimal(locked))
				.openedAt(LocalDateTime.now()).user(createTestUser()).build();
	}

	private void stubUnlock(Loan loan, DepositWallet collateralWallet, String rate) {
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(loan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "ETH_TEST5"))
				.thenReturn(Optional.of(collateralWallet));
		when(conversionRateService.getConversionRate("ETH_TEST5", "USDC_ETH_TEST5_AN74"))
				.thenReturn(new BigDecimal(rate));
	}

	@Test
	void unlockCollateral_shouldUnlockUpToHealthyLtv() {
		Loan loan = pledgeLoan("1200", "1.0000000");
		DepositWallet collateralWallet = pledgedWallet("1.0000000");
		setHealthyLtv("ETH_TEST5", "0.8");
		stubUnlock(loan, collateralWallet, "3000");

		Loan result = loanService.unlockCollateral(1L, new BigDecimal("0.5"));

		assertThat(result.getCurrentCollateral()).isEqualByComparingTo("0.5");
		assertThat(result.getCollateralAmount()).isEqualByComparingTo("1");
		assertThat(collateralWallet.getLockedBalance()).isEqualByComparingTo("0.5");
		assertThat(collateralWallet.getBalance()).isEqualByComparingTo("2");
		BigDecimal collateralValue = result.getCurrentCollateral().multiply(new BigDecimal("3000"));
		BigDecimal ltv = result.getOutstandingAmount().divide(collateralValue, 8,
				RoundingMode.HALF_UP);
		assertThat(ltv).isLessThanOrEqualTo(new BigDecimal("0.8"));
		verify(conversionRateService).withdraw("ETH_TEST5", new BigDecimal("0.5"), OMNIBUS_ADDRESS);
		verify(loanRepository, never()).save(any(Loan.class));
		verify(depositWalletRepository, never()).save(any(DepositWallet.class));
	}

	@Test
	void unlockCollateral_shouldRejectOneUnitAboveTheCap() {
		Loan loan = pledgeLoan("1200", "1.0000000");
		DepositWallet collateralWallet = pledgedWallet("1.0000000");
		setHealthyLtv("ETH_TEST5", "0.8");
		stubUnlock(loan, collateralWallet, "3000");

		assertThatThrownBy(() -> loanService.unlockCollateral(1L, new BigDecimal("0.5000001")))
				.isInstanceOf(UnhealthyLtvException.class);

		assertThat(loan.getCurrentCollateral()).isEqualByComparingTo("1");
		assertThat(collateralWallet.getLockedBalance()).isEqualByComparingTo("1");
		assertThat(collateralWallet.getBalance()).isEqualByComparingTo("2");
		verify(conversionRateService, never()).withdraw(any(), any(), any());
		verify(loanRepository, never()).save(any(Loan.class));
		verify(depositWalletRepository, never()).save(any(DepositWallet.class));
	}

	@Test
	void unlockCollateral_shouldRejectWhenLoanIsAlreadyAboveTheCap() {
		Loan loan = pledgeLoan("1200", "1.0000000");
		DepositWallet collateralWallet = pledgedWallet("1.0000000");
		setHealthyLtv("ETH_TEST5", "0.8");
		stubUnlock(loan, collateralWallet, "1000");

		assertThatThrownBy(() -> loanService.unlockCollateral(1L, new BigDecimal("0.0000001")))
				.isInstanceOf(UnhealthyLtvException.class);

		assertThat(loan.getCurrentCollateral()).isEqualByComparingTo("1");
		assertThat(collateralWallet.getLockedBalance()).isEqualByComparingTo("1");
		verify(conversionRateService, never()).withdraw(any(), any(), any());
	}

	@Test
	void unlockCollateral_shouldKeepLtvAtOrUnderTheCapWhenTheMinimumIsCeiled() {
		Loan loan = pledgeLoan("100", "50.0000000");
		DepositWallet collateralWallet = pledgedWallet("50.0000000");
		setHealthyLtv("ETH_TEST5", "0.8");
		stubUnlock(loan, collateralWallet, "3");

		Loan result = loanService.unlockCollateral(1L, new BigDecimal("8.3333333"));

		assertThat(result.getCurrentCollateral()).isEqualByComparingTo("41.6666667");
		assertThat(collateralWallet.getLockedBalance()).isEqualByComparingTo("41.6666667");
		BigDecimal collateralValue = result.getCurrentCollateral().multiply(new BigDecimal("3"));
		BigDecimal ltv = result.getOutstandingAmount().divide(collateralValue, 8,
				RoundingMode.HALF_UP);
		assertThat(ltv).isLessThanOrEqualTo(new BigDecimal("0.8"));
		verify(conversionRateService).withdraw("ETH_TEST5", new BigDecimal("8.3333333"),
				OMNIBUS_ADDRESS);
		verify(loanRepository, never()).save(any(Loan.class));
		verify(depositWalletRepository, never()).save(any(DepositWallet.class));
	}

	@Test
	void unlockCollateral_shouldReleaseTheRemainderOfAPaidLoan() {
		Loan loan = pledgeLoan("100", "0.5000000");
		loan.setReturnedAmount(new BigDecimal("100"));
		DepositWallet collateralWallet = pledgedWallet("0.5000000");
		setHealthyLtv("ETH_TEST5", "0.8");
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(loan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "ETH_TEST5"))
				.thenReturn(Optional.of(collateralWallet));

		Loan result = loanService.unlockCollateral(1L, new BigDecimal("0.5"));

		assertThat(result.isPaid()).isTrue();
		assertThat(result.getCurrentCollateral()).isEqualByComparingTo("0");
		assertThat(collateralWallet.getLockedBalance()).isEqualByComparingTo("0");
		assertThat(collateralWallet.getBalance()).isEqualByComparingTo("2");
		verify(conversionRateService).withdraw("ETH_TEST5", new BigDecimal("0.5"), OMNIBUS_ADDRESS);
		verify(conversionRateService, never()).getConversionRate(any(), any());
		verify(loanRepository, never()).save(any(Loan.class));
		verify(depositWalletRepository, never()).save(any(DepositWallet.class));
	}

	@Test
	void unlockCollateral_shouldLeavePledgeLocked_whenWithdrawFails() {
		Loan loan = pledgeLoan("1200", "1.0000000");
		DepositWallet collateralWallet = pledgedWallet("1.0000000");
		setHealthyLtv("ETH_TEST5", "0.8");
		stubUnlock(loan, collateralWallet, "3000");
		doThrow(new TradingOrderException("send failed")).when(conversionRateService)
				.withdraw(eq("ETH_TEST5"), eq(new BigDecimal("0.5")), eq(OMNIBUS_ADDRESS));

		assertThatThrownBy(() -> loanService.unlockCollateral(1L, new BigDecimal("0.5")))
				.isInstanceOf(TradingOrderException.class);

		assertThat(loan.getCurrentCollateral()).isEqualByComparingTo("1");
		assertThat(collateralWallet.getLockedBalance()).isEqualByComparingTo("1");
		verify(loanRepository, never()).save(any(Loan.class));
		verify(depositWalletRepository, never()).save(any(DepositWallet.class));
	}

	@Test
	void unlockCollateral_shouldThrowLoanNotFoundException_whenLoanDoesNotExist() {
		when(loanRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> loanService.unlockCollateral(99L, new BigDecimal("0.1")))
				.isInstanceOf(LoanNotFoundException.class).hasMessage("Loan not found");

		verify(conversionRateService, never()).withdraw(any(), any(), any());
	}

	@Test
	void unlockCollateral_shouldThrowLoanNotFoundException_whenUserDoesNotOwnLoan() {
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> loanService.unlockCollateral(1L, new BigDecimal("0.1")))
				.isInstanceOf(LoanNotFoundException.class);

		verify(depositWalletRepository, never()).findByUserIdAndAssetId(any(), any());
		verify(conversionRateService, never()).withdraw(any(), any(), any());
	}

	@Test
	void unlockCollateral_shouldThrowDepositWalletNotFound_whenCollateralWalletIsMissing() {
		Loan loan = pledgeLoan("1200", "1.0000000");
		setHealthyLtv("ETH_TEST5", "0.8");
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(loan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "ETH_TEST5"))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> loanService.unlockCollateral(1L, new BigDecimal("0.1")))
				.isInstanceOf(DepositWalletNotFoundByAssetIdException.class);

		assertThat(loan.getCurrentCollateral()).isEqualByComparingTo("1");
		verify(conversionRateService, never()).getConversionRate(any(), any());
		verify(conversionRateService, never()).withdraw(any(), any(), any());
		verify(loanRepository, never()).save(any(Loan.class));
		verify(depositWalletRepository, never()).save(any(DepositWallet.class));
	}

	// ========== getAllUserLoans tests ==========

	@Test
	void getAllUserLoans_shouldPreserveRepositoryOrder() {
		User user = createTestUser();
		LocalDateTime newerGrantedAt = LocalDateTime.of(2026, 3, 2, 12, 0);
		LocalDateTime olderGrantedAt = LocalDateTime.of(2026, 1, 1, 8, 0);

		Loan newer = Loan.builder().id(2L).loanAssetId("USDC").collateralAssetId("ETH")
				.returnedAmount(BigDecimal.ZERO).amount(new BigDecimal("500.0000000"))
				.collateralAmount(new BigDecimal("0.5000000"))
				.interestRate(new BigDecimal("0.050000"))
				.accruedInterest(new BigDecimal("5.0000000")).grantedAt(newerGrantedAt).user(user)
				.build();

		Loan older = Loan.builder().id(1L).loanAssetId("ETH_TEST5").collateralAssetId("AVAXTEST")
				.returnedAmount(new BigDecimal("10.0000000")).amount(new BigDecimal("100.0000000"))
				.collateralAmount(new BigDecimal("1.0000000"))
				.interestRate(new BigDecimal("0.080000"))
				.accruedInterest(new BigDecimal("1.0000000")).grantedAt(olderGrantedAt).user(user)
				.build();

		when(loanRepository.findAllByUserIdOrderByGrantedAtDesc(USER_ID))
				.thenReturn(List.of(newer, older));

		List<Loan> result = loanService.getAllUserLoans();

		assertThat(result).containsExactly(newer, older);
		assertThat(result.get(0).getOutstandingAmount()).isEqualByComparingTo("505");
		assertThat(result.get(1).getOutstandingAmount()).isEqualByComparingTo("91");
		verify(loanRepository).findAllByUserIdOrderByGrantedAtDesc(USER_ID);
	}

	@Test
	void getAllUserLoans_shouldReturnEmptyList() {
		when(loanRepository.findAllByUserIdOrderByGrantedAtDesc(USER_ID)).thenReturn(List.of());

		List<Loan> result = loanService.getAllUserLoans();

		assertThat(result).isNotNull();
		assertThat(result).isEmpty();
		verify(loanRepository).findAllByUserIdOrderByGrantedAtDesc(USER_ID);
	}

	@Test
	void getAllUserLoans_shouldQueryOnlyTheCurrentUser() {
		User other = User.builder().id(99L).build();
		when(currentUserProvider.getCurrentUser()).thenReturn(other);
		when(loanRepository.findAllByUserIdOrderByGrantedAtDesc(99L)).thenReturn(List.of());

		loanService.getAllUserLoans();

		verify(loanRepository).findAllByUserIdOrderByGrantedAtDesc(99L);
		verify(loanRepository, never()).findAllByUserIdOrderByGrantedAtDesc(USER_ID);
	}

	// ========== getUserLoan tests ==========

	@Test
	void getUserLoan_shouldReturnTheCurrentUsersLoan() {
		Loan paidLoan = createActiveLoan(currentUser);
		paidLoan.setId(7L);
		paidLoan.setReturnedAmount(new BigDecimal("505.0000000"));

		when(loanRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(paidLoan));

		Loan result = loanService.getUserLoan(7L);

		assertThat(result).isSameAs(paidLoan);
		assertThat(result.isPaid()).isTrue();
		verify(loanRepository).findByIdAndUserId(7L, USER_ID);
	}

	@Test
	void getUserLoan_shouldThrowLoanNotFoundException_whenLoanDoesNotExist() {
		when(loanRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> loanService.getUserLoan(99L))
				.isInstanceOf(LoanNotFoundException.class).hasMessage("Loan not found");

		verify(loanRepository).findByIdAndUserId(99L, USER_ID);
	}

	@Test
	void getUserLoan_shouldQueryOnlyTheCurrentUser() {
		User other = User.builder().id(99L).build();
		when(currentUserProvider.getCurrentUser()).thenReturn(other);
		when(loanRepository.findByIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> loanService.getUserLoan(1L)).isInstanceOf(LoanNotFoundException.class);

		verify(loanRepository).findByIdAndUserId(1L, 99L);
		verify(loanRepository, never()).findByIdAndUserId(1L, USER_ID);
	}
}