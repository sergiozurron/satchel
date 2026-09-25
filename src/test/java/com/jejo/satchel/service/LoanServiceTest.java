package com.jejo.satchel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.jejo.satchel.dto.CustomLoanRequest;
import com.jejo.satchel.exception.DepositWalletNotFoundByAssetIdException;
import com.jejo.satchel.exception.ExcesiveEquivalentAmountException;
import com.jejo.satchel.exception.InsufficientFundsException;
import com.jejo.satchel.exception.InsufficientCollateralException;
import com.jejo.satchel.exception.InvalidRepaymentAmountException;
import com.jejo.satchel.exception.LoanNotActiveException;
import com.jejo.satchel.exception.LoanNotFoundException;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.Loan;
import com.jejo.satchel.model.LoanStatus;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.LoanRepository;
import com.jejo.satchel.util.CurrentUserProvider;

/**
 * Unit tests for {@link LoanService}. Combines tests for processLoanRequest and
 * repayLoan.
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

	private static final Long USER_ID = 1L;
	private static final String COLLATERAL_ASSET_ID = "TEST_BTC";
	private static final String LOAN_ASSET_ID = "TEST_USDT";

	@Mock
	private CurrentUserProvider currentUserProvider;

	@Mock
	private LoanRepository loanRepository;

	@Mock
	private DepositWalletRepository depositWalletRepository;

	@Mock
	private AssetCustodianService assetCustodianService;

	@Mock
	private AssetPriceService bitcoinPriceService; // also used as assetPriceService in repay tests

	private User currentUser;

	@Mock
	private DepositWallet collateralWallet;

	@Mock
	private DepositWallet loanAssetWallet;

	private LoanService loanService;

	private static final BigDecimal INTEREST_RATE = BigDecimal.valueOf(0.05);

	@BeforeEach
	void setUp() {
		currentUser = User.builder().id(USER_ID).build();
		loanService = new LoanService(currentUserProvider, loanRepository, depositWalletRepository,
				assetCustodianService, bitcoinPriceService);
		ReflectionTestUtils.setField(loanService, "collateralCoin", "ETH");
		ReflectionTestUtils.setField(loanService, "repaymentPrefix", "REPAY-");
		ReflectionTestUtils.setField(loanService, "interestRate", INTEREST_RATE);

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
	}

	// ========== processLoanRequest tests ==========

	@Test
	void processLoanRequest_happyPath_locksCollateralCreditsWalletAndSavesLoan() {
		BigDecimal collateralAmount = BigDecimal.valueOf(2);
		BigDecimal ltv = BigDecimal.valueOf(0.5);
		BigDecimal convertedUsdc = BigDecimal.valueOf(4000); // e.g. 2 ETH -> 4000 USDC
		BigDecimal expectedLoanAmount = convertedUsdc.multiply(ltv); // 2000

		CustomLoanRequest loanRequest = new CustomLoanRequest();
		loanRequest.setCollateralAssetId(COLLATERAL_ASSET_ID);
		loanRequest.setLoanAssetId(LOAN_ASSET_ID);
		loanRequest.setCollateralAmount(collateralAmount);
		loanRequest.setLtv(ltv);

		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, COLLATERAL_ASSET_ID))
				.thenReturn(Optional.of(collateralWallet));
		when(collateralWallet.getAvailableBalance()).thenReturn(BigDecimal.valueOf(5));
		when(bitcoinPriceService.convertEthToUsdc(collateralAmount)).thenReturn(convertedUsdc);
		when(assetCustodianService.getOmnibusBalance()).thenReturn(BigDecimal.valueOf(1_000_000));
		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, LOAN_ASSET_ID))
				.thenReturn(Optional.of(loanAssetWallet));

		loanService.processLoanRequest(loanRequest);

		// Collateral gets locked and saved
		verify(collateralWallet).setLockedBalance(collateralAmount);
		verify(depositWalletRepository).save(collateralWallet);

		// Loan asset wallet is credited with the loan amount and saved
		verify(loanAssetWallet).deposit(expectedLoanAmount);
		verify(depositWalletRepository).save(loanAssetWallet);

		// Loan entity persisted with expected values
		ArgumentCaptor<Loan> loanCaptor = ArgumentCaptor.forClass(Loan.class);
		verify(loanRepository).save(loanCaptor.capture());
		Loan savedLoan = loanCaptor.getValue();

		assertThat(savedLoan.getUser()).isEqualTo(currentUser);
		assertThat(savedLoan.getAmount()).isEqualByComparingTo(expectedLoanAmount);
		assertThat(savedLoan.getCollateralAmount()).isEqualByComparingTo(collateralAmount);
		assertThat(savedLoan.getCollateralAssetId()).isEqualTo(COLLATERAL_ASSET_ID);
		assertThat(savedLoan.getLoanAssetId()).isEqualTo(LOAN_ASSET_ID);
		assertThat(savedLoan.getLtv()).isEqualByComparingTo(ltv);
		assertThat(savedLoan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
		assertThat(savedLoan.getReturnedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(savedLoan.getInterestRate()).isEqualByComparingTo(BigDecimal.valueOf(0.05));

		BigDecimal expectedAccruedInterest = expectedLoanAmount.multiply(INTEREST_RATE)
				.divide(BigDecimal.valueOf(365), java.math.RoundingMode.HALF_UP);
		assertThat(savedLoan.getAccruedInterest()).isEqualByComparingTo(expectedAccruedInterest);
	}

	@Test
	void processLoanRequest_noCollateralWallet_doesNothingSilently() {
		CustomLoanRequest loanRequest = new CustomLoanRequest();
		loanRequest.setCollateralAssetId(COLLATERAL_ASSET_ID);
		
		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, COLLATERAL_ASSET_ID))
				.thenReturn(Optional.empty());

		loanService.processLoanRequest(loanRequest);

		verify(depositWalletRepository, never()).save(any(DepositWallet.class));
		verify(loanRepository, never()).save(any(Loan.class));
		verify(bitcoinPriceService, never()).convertEthToUsdc(any());
		verify(assetCustodianService, never()).getOmnibusBalance();
	}

	@Test
	void processLoanRequest_collateralAmountExceedsAvailableBalance_throwsInsufficientCollateral() {
		BigDecimal collateralAmount = BigDecimal.valueOf(10);
		CustomLoanRequest loanRequest = new CustomLoanRequest();
		loanRequest.setCollateralAmount(collateralAmount);
		loanRequest.setCollateralAssetId(COLLATERAL_ASSET_ID);

		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, COLLATERAL_ASSET_ID))
				.thenReturn(Optional.of(collateralWallet));
		when(collateralWallet.getAvailableBalance()).thenReturn(BigDecimal.valueOf(5));

		assertThrows(InsufficientCollateralException.class,
				() -> loanService.processLoanRequest(loanRequest));

		verify(depositWalletRepository, never()).save(any(DepositWallet.class));
		verify(loanRepository, never()).save(any(Loan.class));
		verify(bitcoinPriceService, never()).convertEthToUsdc(any());
	}

	@Test
	void processLoanRequest_omnibusBalanceInsufficient_throwsExcesiveEquivalentAmount() {
		BigDecimal collateralAmount = BigDecimal.valueOf(2);
		BigDecimal ltv = BigDecimal.valueOf(0.5);
		BigDecimal convertedUsdc = BigDecimal.valueOf(4000);
		BigDecimal expectedLoanAmount = convertedUsdc.multiply(ltv); // 2000
		BigDecimal omnibusBalance = BigDecimal.valueOf(1000); // less than loan amount

		CustomLoanRequest loanRequest = new CustomLoanRequest();
		loanRequest.setCollateralAssetId(COLLATERAL_ASSET_ID);
		loanRequest.setLoanAssetId(LOAN_ASSET_ID);
		loanRequest.setCollateralAmount(collateralAmount);
		loanRequest.setLtv(ltv);

		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, COLLATERAL_ASSET_ID))
				.thenReturn(Optional.of(collateralWallet));
		when(collateralWallet.getAvailableBalance()).thenReturn(BigDecimal.valueOf(5));
		when(bitcoinPriceService.convertEthToUsdc(collateralAmount)).thenReturn(convertedUsdc);
		when(assetCustodianService.getOmnibusBalance()).thenReturn(omnibusBalance);

		ExcesiveEquivalentAmountException ex = assertThrows(ExcesiveEquivalentAmountException.class,
				() -> loanService.processLoanRequest(loanRequest));

		assertThat(ex).isNotNull();

		// Collateral must not be locked/saved when the request fails downstream
		verify(collateralWallet, never()).setLockedBalance(any());
		verify(depositWalletRepository, never()).save(any(DepositWallet.class));
		verify(loanRepository, never()).save(any(Loan.class));

		// Sanity check on the amount comparison used to trigger the exception
		assertThat(omnibusBalance).isLessThan(expectedLoanAmount);
	}

	@Test
	void processLoanRequest_loanAssetWalletMissing_throwsDepositWalletNotFound() {
		BigDecimal collateralAmount = BigDecimal.valueOf(2);
		BigDecimal ltv = BigDecimal.valueOf(0.5);
		BigDecimal convertedUsdc = BigDecimal.valueOf(4000);
		
		CustomLoanRequest loanRequest = new CustomLoanRequest();
		loanRequest.setCollateralAssetId(COLLATERAL_ASSET_ID);
		loanRequest.setLoanAssetId(LOAN_ASSET_ID);
		loanRequest.setCollateralAmount(collateralAmount);
		loanRequest.setLtv(ltv);

		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, COLLATERAL_ASSET_ID))
				.thenReturn(Optional.of(collateralWallet));
		when(collateralWallet.getAvailableBalance()).thenReturn(BigDecimal.valueOf(5));
		when(bitcoinPriceService.convertEthToUsdc(collateralAmount)).thenReturn(convertedUsdc);
		when(assetCustodianService.getOmnibusBalance()).thenReturn(BigDecimal.valueOf(1_000_000));

		// Collateral is locked before the loan-asset wallet lookup happens
		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, LOAN_ASSET_ID))
				.thenReturn(Optional.empty());

		assertThrows(DepositWalletNotFoundByAssetIdException.class,
				() -> loanService.processLoanRequest(loanRequest));

		// Collateral was already locked+saved before the failure, per current
		// implementation (no rollback of the in-memory mutation itself, though the
		// @Transactional annotation will roll back the DB transaction).
		verify(collateralWallet).setLockedBalance(collateralAmount);
		verify(depositWalletRepository).save(collateralWallet);
		verify(loanRepository, never()).save(any(Loan.class));
	}

	// ========== repayLoan helpers ==========

	private User createTestUser() {
		return User.builder().id(1L).firstName("John").lastName("Doe").email("john@example.com")
				.password("password123").verified(true).vaultAccountId("123").build();
	}

	private Loan createActiveLoan(User user) {
		return Loan.builder().id(1L).loanAssetId("USDC").collateralAssetId("ETH")
				.returnedAmount(BigDecimal.ZERO).amount(new BigDecimal("500.0000000"))
				.collateralAmount(new BigDecimal("0.5000000")).ltv(new BigDecimal("0.7500"))
				.interestRate(new BigDecimal("0.050000")).status(LoanStatus.ACTIVE)
				.accruedInterest(new BigDecimal("5.0000000")).grantedAt(LocalDateTime.now())
				.user(user).build();
	}

	private DepositWallet createDepositWallet(User user) {
		return DepositWallet.builder().id(1L).address("0x1234567890abcdef").assetId("USDC")
				.balance(new BigDecimal("1000.0000000")).lockedBalance(BigDecimal.ZERO)
				.openedAt(LocalDateTime.now()).user(user).build();
	}

	// ========== repayLoan tests ==========

	@Test
	void repayLoan_shouldSuccessfullyRepayPartialAmount() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		DepositWallet wallet = createDepositWallet(user);
		BigDecimal repaymentAmount = new BigDecimal("100.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC"))
				.thenReturn(Optional.of(wallet));
		when(loanRepository.save(any(Loan.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(depositWalletRepository.save(any(DepositWallet.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		// When
		Loan result = loanService.repayLoan(1L, repaymentAmount);

		// Then
		assertThat(result.getReturnedAmount()).isEqualTo(repaymentAmount);
		assertThat(result.getOutstandingAmount()).isEqualTo(new BigDecimal("400.0000000"));
		assertThat(result.getStatus()).isEqualTo(LoanStatus.ACTIVE);

		verify(loanRepository).findByIdAndUserId(1L, 1L);
		verify(depositWalletRepository).findByUserIdAndAssetId(1L, "USDC");
		verify(depositWalletRepository).save(any(DepositWallet.class));
		verify(loanRepository).save(any(Loan.class));
	}

	@Test
	void repayLoan_shouldSuccessfullyRepayFullAmount() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		DepositWallet wallet = createDepositWallet(user);
		BigDecimal repaymentAmount = new BigDecimal("500.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC"))
				.thenReturn(Optional.of(wallet));
		when(loanRepository.save(any(Loan.class))).thenReturn(activeLoan);
		when(depositWalletRepository.save(any(DepositWallet.class))).thenReturn(wallet);

		// When
		Loan result = loanService.repayLoan(1L, repaymentAmount);

		// Then
		assertThat(result.getReturnedAmount()).isEqualTo(repaymentAmount);
		assertThat(result.getStatus()).isEqualTo(LoanStatus.PAID);

		verify(loanRepository).save(any(Loan.class));
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
		paidLoan.setStatus(LoanStatus.PAID);

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(paidLoan));

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(1L, new BigDecimal("100.0000000")))
				.isInstanceOf(LoanNotActiveException.class);
	}

	@Test
	void repayLoan_shouldThrowLoanNotActiveException_whenLoanIsLiquidated() {
		// Given
		User user = createTestUser();
		Loan liquidatedLoan = createActiveLoan(user);
		liquidatedLoan.setStatus(LoanStatus.LIQUIDATED);

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(liquidatedLoan));

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(1L, new BigDecimal("100.0000000")))
				.isInstanceOf(LoanNotActiveException.class);
	}

	@Test
	void repayLoan_shouldThrowInvalidRepaymentAmountException_whenAmountIsZero() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(1L, BigDecimal.ZERO))
				.isInstanceOf(InvalidRepaymentAmountException.class);
	}

	@Test
	void repayLoan_shouldThrowInvalidRepaymentAmountException_whenAmountIsNegative() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(1L, new BigDecimal("-100.0000000")))
				.isInstanceOf(InvalidRepaymentAmountException.class);
	}

	@Test
	void repayLoan_shouldThrowInvalidRepaymentAmountException_whenAmountExceedsOutstanding() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		BigDecimal excessiveAmount = new BigDecimal("1000.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(1L, excessiveAmount))
				.isInstanceOf(InvalidRepaymentAmountException.class);
	}

	@Test
	void repayLoan_shouldThrowInsufficientFundsException_whenWalletNotFound() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC"))
				.thenReturn(Optional.empty());

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(1L, new BigDecimal("100.0000000")))
				.isInstanceOf(DepositWalletNotFoundByAssetIdException.class);
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
	}

	@Test
	void repayLoan_shouldDebitWalletBalance() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		DepositWallet wallet = createDepositWallet(user);
		BigDecimal repaymentAmount = new BigDecimal("100.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC"))
				.thenReturn(Optional.of(wallet));
		when(loanRepository.save(any(Loan.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(depositWalletRepository.save(any(DepositWallet.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		// When
		loanService.repayLoan(1L, repaymentAmount);

		// Then - Verify wallet balance was decreased
		ArgumentCaptor<DepositWallet> walletCaptor = ArgumentCaptor.forClass(DepositWallet.class);
		verify(depositWalletRepository).save(walletCaptor.capture());
		DepositWallet savedWallet = walletCaptor.getValue();
		assertThat(savedWallet.getBalance()).isEqualTo(new BigDecimal("900.0000000"));
	}

	@Test
	void repayLoan_shouldUpdateLoanReturnedAmount() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		DepositWallet wallet = createDepositWallet(user);
		BigDecimal repaymentAmount = new BigDecimal("150.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC"))
				.thenReturn(Optional.of(wallet));
		when(loanRepository.save(any(Loan.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(depositWalletRepository.save(any(DepositWallet.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		// When
		Loan result = loanService.repayLoan(1L, repaymentAmount);

		// Then
		assertThat(result.getReturnedAmount()).isEqualTo(repaymentAmount);
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
		DepositWallet wallet = createDepositWallet(user);
		BigDecimal repaymentAmount = new BigDecimal("500.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC"))
				.thenReturn(Optional.of(wallet));
		when(loanRepository.save(any(Loan.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(depositWalletRepository.save(any(DepositWallet.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		// When
		Loan result = loanService.repayLoan(1L, repaymentAmount);

		// Then
		assertThat(result.getStatus()).isEqualTo(LoanStatus.PAID);
	}

	@Test
	void repayLoan_shouldAllowMultipleRepayments() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		DepositWallet wallet = createDepositWallet(user);

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC"))
				.thenReturn(Optional.of(wallet));
		when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> {
			Loan loan = invocation.getArgument(0);
			return loan;
		});
		when(depositWalletRepository.save(any(DepositWallet.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		// When - First repayment
		BigDecimal firstRepayment = new BigDecimal("100.0000000");
		Loan resultAfterFirst = loanService.repayLoan(1L, firstRepayment);

		// Then - Verify first repayment
		assertThat(resultAfterFirst.getReturnedAmount()).isEqualTo(firstRepayment);
		assertThat(resultAfterFirst.getOutstandingAmount())
				.isEqualTo(new BigDecimal("400.0000000"));

		// Given - Update loan for second repayment
		activeLoan.setReturnedAmount(firstRepayment);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));

		// When - Second repayment
		BigDecimal secondRepayment = new BigDecimal("200.0000000");
		Loan resultAfterSecond = loanService.repayLoan(1L, secondRepayment);

		// Then - Verify cumulative repayment
		assertThat(resultAfterSecond.getReturnedAmount()).isEqualTo(new BigDecimal("300.0000000"));
		assertThat(resultAfterSecond.getOutstandingAmount())
				.isEqualTo(new BigDecimal("200.0000000"));
	}

	@Test
	void repayLoan_shouldHandleSmallRepaymentAmounts() {
		// Given
		User user = createTestUser();
		Loan activeLoan = createActiveLoan(user);
		DepositWallet wallet = createDepositWallet(user);
		BigDecimal repaymentAmount = new BigDecimal("0.0001000");

		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC"))
				.thenReturn(Optional.of(wallet));
		when(loanRepository.save(any(Loan.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(depositWalletRepository.save(any(DepositWallet.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		// When
		Loan result = loanService.repayLoan(1L, repaymentAmount);

		// Then
		assertThat(result.getReturnedAmount()).isEqualTo(repaymentAmount);
		assertThat(result.getOutstandingAmount()).isEqualTo(new BigDecimal("499.9999000"));
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
}