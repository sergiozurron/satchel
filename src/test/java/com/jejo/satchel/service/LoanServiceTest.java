package com.jejo.satchel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jejo.satchel.exception.InsufficientBalanceForRepaymentException;
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

@ExtendWith(MockitoExtension.class)
public class LoanServiceTest {

	@Mock
	private CurrentUserProvider currentUserProvider;
	@Mock
	private LoanRepository loanRepository;
	@Mock
	private DepositWalletRepository depositWalletRepository;
	@Mock
	private AssetCustodianService assetCustodianService;
	@Mock
	private AssetPriceService assetPriceService;
	@InjectMocks
	private LoanService loanService;

	private User createTestUser() {
		return User.builder()
				.id(1L)
				.firstName("John")
				.lastName("Doe")
				.email("john@example.com")
				.password("password123")
				.verified(true)
				.vaultAccountId(123L)
				.build();
	}

	private Loan createActiveLoan(User user) {
		return Loan.builder()
				.id(1L)
				.loanAssetId("USDC")
				.collateralAssetId("ETH")
				.returnedAmount(BigDecimal.ZERO)
				.amount(new BigDecimal("500.0000000"))
				.collateralAmount(new BigDecimal("0.5000000"))
				.ltv(new BigDecimal("0.7500"))
				.interestRate(new BigDecimal("0.050000"))
				.status(LoanStatus.ACTIVE)
				.accruedInterest(new BigDecimal("5.0000000"))
				.grantedAt(LocalDateTime.now())
				.user(user)
				.build();
	}

	private DepositWallet createDepositWallet(User user) {
		return DepositWallet.builder()
				.id(1L)
				.address("0x1234567890abcdef")
				.assetId("USDC")
				.balance(new BigDecimal("1000.0000000"))
				.lockedBalance(BigDecimal.ZERO)
				.openedAt(LocalDateTime.now())
				.user(user)
				.build();
	}

	@Test
	void repayLoan_shouldSuccessfullyRepayPartialAmount() {
		// Given
		User currentUser = createTestUser();
		Loan activeLoan = createActiveLoan(currentUser);
		DepositWallet wallet = createDepositWallet(currentUser);
		BigDecimal repaymentAmount = new BigDecimal("100.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC")).thenReturn(Optional.of(wallet));
		when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(depositWalletRepository.save(any(DepositWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
		User currentUser = createTestUser();
		Loan activeLoan = createActiveLoan(currentUser);
		DepositWallet wallet = createDepositWallet(currentUser);
		BigDecimal repaymentAmount = new BigDecimal("500.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC")).thenReturn(Optional.of(wallet));
		when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(depositWalletRepository.save(any(DepositWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// When
		Loan result = loanService.repayLoan(1L, repaymentAmount);

		// Then
		assertThat(result.getReturnedAmount()).isEqualTo(repaymentAmount);
		assertThat(result.getOutstandingAmount()).isEqualTo(BigDecimal.ZERO);
		assertThat(result.getStatus()).isEqualTo(LoanStatus.PAID);
		verify(loanRepository).save(any(Loan.class));
	}

	@Test
	void repayLoan_shouldThrowLoanNotFoundException_whenLoanDoesNotExist() {
		// Given
		User currentUser = createTestUser();
		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
		when(loanRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(99L, new BigDecimal("100.0000000")))
				.isInstanceOf(LoanNotFoundException.class)
				.hasMessage("Loan not found");

		verify(loanRepository).findByIdAndUserId(99L, 1L);
	}

	@Test
	void repayLoan_shouldThrowLoanNotActiveException_whenLoanIsPaid() {
		// Given
		User currentUser = createTestUser();
		Loan paidLoan = createActiveLoan(currentUser);
		paidLoan.setStatus(LoanStatus.PAID);

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(paidLoan));

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(1L, new BigDecimal("100.0000000")))
				.isInstanceOf(LoanNotActiveException.class)
				.hasMessage("Loan is not active");
	}

	@Test
	void repayLoan_shouldThrowLoanNotActiveException_whenLoanIsLiquidated() {
		// Given
		User currentUser = createTestUser();
		Loan liquidatedLoan = createActiveLoan(currentUser);
		liquidatedLoan.setStatus(LoanStatus.LIQUIDATED);

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(liquidatedLoan));

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(1L, new BigDecimal("100.0000000")))
				.isInstanceOf(LoanNotActiveException.class);
	}

	@Test
	void repayLoan_shouldThrowInvalidRepaymentAmountException_whenAmountIsZero() {
		// Given
		User currentUser = createTestUser();
		Loan activeLoan = createActiveLoan(currentUser);

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(1L, BigDecimal.ZERO))
				.isInstanceOf(InvalidRepaymentAmountException.class);
	}

	@Test
	void repayLoan_shouldThrowInvalidRepaymentAmountException_whenAmountIsNegative() {
		// Given
		User currentUser = createTestUser();
		Loan activeLoan = createActiveLoan(currentUser);

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(1L, new BigDecimal("-100.0000000")))
				.isInstanceOf(InvalidRepaymentAmountException.class);
	}

	@Test
	void repayLoan_shouldThrowInvalidRepaymentAmountException_whenAmountExceedsOutstanding() {
		// Given
		User currentUser = createTestUser();
		Loan activeLoan = createActiveLoan(currentUser);
		BigDecimal excessiveAmount = new BigDecimal("1000.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(1L, excessiveAmount))
				.isInstanceOf(InvalidRepaymentAmountException.class)
				.hasMessageContaining("exceeds outstanding");
	}

	@Test
	void repayLoan_shouldThrowInsufficientBalanceForRepaymentException_whenWalletNotFound() {
		// Given
		User currentUser = createTestUser();
		Loan activeLoan = createActiveLoan(currentUser);

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC")).thenReturn(Optional.empty());

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(1L, new BigDecimal("100.0000000")))
				.isInstanceOf(InsufficientBalanceForRepaymentException.class);
	}

	@Test
	void repayLoan_shouldThrowInsufficientBalanceForRepaymentException_whenBalanceInsufficient() {
		// Given
		User currentUser = createTestUser();
		Loan activeLoan = createActiveLoan(currentUser);
		DepositWallet wallet = createDepositWallet(currentUser);
		wallet.setBalance(new BigDecimal("50.0000000"));
		BigDecimal repaymentAmount = new BigDecimal("100.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC")).thenReturn(Optional.of(wallet));

		// When & Then
		assertThatThrownBy(() -> loanService.repayLoan(1L, repaymentAmount))
				.isInstanceOf(InsufficientBalanceForRepaymentException.class)
				.hasMessageContaining("Insufficient balance");
	}

	@Test
	void repayLoan_shouldDebitWalletBalance() {
		// Given
		User currentUser = createTestUser();
		Loan activeLoan = createActiveLoan(currentUser);
		DepositWallet wallet = createDepositWallet(currentUser);
		BigDecimal repaymentAmount = new BigDecimal("100.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC")).thenReturn(Optional.of(wallet));
		when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(depositWalletRepository.save(any(DepositWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
		User currentUser = createTestUser();
		Loan activeLoan = createActiveLoan(currentUser);
		DepositWallet wallet = createDepositWallet(currentUser);
		BigDecimal repaymentAmount = new BigDecimal("150.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC")).thenReturn(Optional.of(wallet));
		when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(depositWalletRepository.save(any(DepositWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// When
		Loan result = loanService.repayLoan(1L, repaymentAmount);

		// Then
		assertThat(result.getReturnedAmount()).isEqualTo(repaymentAmount);
	}

	@Test
	void repayLoan_shouldVerifyLoanOwnership() {
		// Given
		User currentUser = createTestUser();
		currentUser.setId(1L);
		Loan activeLoan = createActiveLoan(currentUser);

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
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
		User currentUser = createTestUser();
		Loan activeLoan = createActiveLoan(currentUser);
		DepositWallet wallet = createDepositWallet(currentUser);
		BigDecimal repaymentAmount = new BigDecimal("500.0000000");

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC")).thenReturn(Optional.of(wallet));
		when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(depositWalletRepository.save(any(DepositWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// When
		Loan result = loanService.repayLoan(1L, repaymentAmount);

		// Then
		assertThat(result.getStatus()).isEqualTo(LoanStatus.PAID);
	}

	@Test
	void repayLoan_shouldAllowMultipleRepayments() {
		// Given
		User currentUser = createTestUser();
		Loan activeLoan = createActiveLoan(currentUser);
		DepositWallet wallet = createDepositWallet(currentUser);

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC")).thenReturn(Optional.of(wallet));
		when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> {
			Loan loan = invocation.getArgument(0);
			return loan;
		});
		when(depositWalletRepository.save(any(DepositWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// When - First repayment
		BigDecimal firstRepayment = new BigDecimal("100.0000000");
		Loan resultAfterFirst = loanService.repayLoan(1L, firstRepayment);

		// Then - Verify first repayment
		assertThat(resultAfterFirst.getReturnedAmount()).isEqualTo(firstRepayment);
		assertThat(resultAfterFirst.getOutstandingAmount()).isEqualTo(new BigDecimal("400.0000000"));

		// Given - Update loan for second repayment
		activeLoan.setReturnedAmount(firstRepayment);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));

		// When - Second repayment
		BigDecimal secondRepayment = new BigDecimal("200.0000000");
		Loan resultAfterSecond = loanService.repayLoan(1L, secondRepayment);

		// Then - Verify cumulative repayment
		assertThat(resultAfterSecond.getReturnedAmount()).isEqualTo(new BigDecimal("300.0000000"));
		assertThat(resultAfterSecond.getOutstandingAmount()).isEqualTo(new BigDecimal("200.0000000"));
	}

	@Test
	void repayLoan_shouldHandleSmallRepaymentAmounts() {
		// Given
		User currentUser = createTestUser();
		Loan activeLoan = createActiveLoan(currentUser);
		DepositWallet wallet = createDepositWallet(currentUser);
		BigDecimal repaymentAmount = new BigDecimal("0.0001000");

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC")).thenReturn(Optional.of(wallet));
		when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(depositWalletRepository.save(any(DepositWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// When
		Loan result = loanService.repayLoan(1L, repaymentAmount);

		// Then
		assertThat(result.getReturnedAmount()).isEqualTo(repaymentAmount);
		assertThat(result.getOutstandingAmount()).isEqualTo(new BigDecimal("499.9999000"));
	}

	@Test
	void repayLoan_shouldConsiderLockedBalanceWhenCalculatingAvailable() {
		// Given
		User currentUser = createTestUser();
		Loan activeLoan = createActiveLoan(currentUser);
		DepositWallet wallet = createDepositWallet(currentUser);
		wallet.setLockedBalance(new BigDecimal("100.0000000")); // Locked balance

		when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
		when(loanRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(activeLoan));
		when(depositWalletRepository.findByUserIdAndAssetId(1L, "USDC")).thenReturn(Optional.of(wallet));

		// When & Then - Available balance is 900 (1000 - 100 locked), so 1000 repayment should fail
		BigDecimal repaymentAmount = new BigDecimal("950.0000000");
		assertThatThrownBy(() -> loanService.repayLoan(1L, repaymentAmount))
				.isInstanceOf(InsufficientBalanceForRepaymentException.class);
	}

}
