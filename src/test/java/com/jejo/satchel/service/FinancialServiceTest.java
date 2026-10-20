package com.jejo.satchel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.LoanRepository;

@ExtendWith(MockitoExtension.class)
public class FinancialServiceTest {

	@Mock
	private DepositWalletRepository depositWalletRepository;

	@Mock
	private LoanRepository loanRepository;

	@Mock
	private AssetTradingService conversionRateService;

	@InjectMocks
	private FinancialService financialService;

	@Test
	void creditAccruedInterestOnDeposits_shouldAddInterestToBalanceAndResetAccrued() {
		DepositWallet wallet = wallet("100", "40", "1.25");
		when(depositWalletRepository.findByAccruedInterestGreaterThan(BigDecimal.ZERO))
				.thenReturn(List.of(wallet));

		financialService.creditAccruedInterestOnDeposits();

		assertThat(wallet.getBalance()).isEqualByComparingTo("101.25");
		assertThat(wallet.getAccruedInterest()).isEqualByComparingTo("0");
		assertThat(wallet.getLockedBalance()).isEqualByComparingTo("40");
		verify(depositWalletRepository).findByAccruedInterestGreaterThan(BigDecimal.ZERO);
		verify(depositWalletRepository, never()).save(any());
	}

	@Test
	void creditAccruedInterestOnDeposits_shouldLeaveZeroAccruedInterestUnchanged() {
		DepositWallet wallet = wallet("100", "40", "0");
		when(depositWalletRepository.findByAccruedInterestGreaterThan(BigDecimal.ZERO))
				.thenReturn(List.of(wallet));

		financialService.creditAccruedInterestOnDeposits();

		assertThat(wallet.getBalance()).isEqualByComparingTo("100");
		assertThat(wallet.getAccruedInterest()).isEqualByComparingTo("0");
		assertThat(wallet.getLockedBalance()).isEqualByComparingTo("40");
		verify(depositWalletRepository).findByAccruedInterestGreaterThan(BigDecimal.ZERO);
		verify(depositWalletRepository, never()).save(any());
	}

	private DepositWallet wallet(String balance, String lockedBalance, String accruedInterest) {
		return DepositWallet.builder().balance(new BigDecimal(balance))
				.lockedBalance(new BigDecimal(lockedBalance))
				.accruedInterest(new BigDecimal(accruedInterest)).build();
	}

}
