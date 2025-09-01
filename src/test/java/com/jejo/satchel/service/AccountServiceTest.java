package com.jejo.satchel.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jejo.satchel.exception.AccountsAlreadyCreatedException;
import com.jejo.satchel.model.CollateralAccount;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.CollateralAccountRepository;
import com.jejo.satchel.repository.DepositAccountRepository;
import com.jejo.satchel.repository.RepaymentAccountRepository;
import com.jejo.satchel.util.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

	@Mock
	private CurrentUserProvider currentUserProvider;
	@Mock
	private AssetCustodianService assetCustodianService;
	@Mock
	private CollateralAccountRepository collateralAccountRepository;
	@Mock
	private RepaymentAccountRepository repaymentAccountRepository;
	@Mock
	private DepositAccountRepository depositAccountRepository;
	@InjectMocks
	private AccountService accountService;

	@Test
	void createUserAccounts_ShouldCreateAccounts_WhenUserHasNoAccounts() {
		// Given
		User user = User.builder().id(1L).build();
		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(collateralAccountRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
		when(assetCustodianService.createCollateralVaultAccount(user.getEmail()))
				.thenReturn(1L);
		when(assetCustodianService.createBTCWallet("1")).thenReturn("btc-wallet-address");
		when(collateralAccountRepository.save(any())).thenReturn(null);
		when(assetCustodianService.createRepaymentVaultAccount(user.getEmail()))
				.thenReturn(2L);
		when(assetCustodianService.createUSDCWallet("2")).thenReturn("usdc-wallet-address");
		when(repaymentAccountRepository.save(any())).thenReturn(null);
		when(assetCustodianService.createDepositVaultAccount(user.getEmail()))
				.thenReturn(3L);
		when(assetCustodianService.createUSDCWallet("3")).thenReturn("usdc-wallet-address-2");
		when(depositAccountRepository.save(any())).thenReturn(null);

		// When
		accountService.createUserAccounts();

		// Then
		verify(collateralAccountRepository).save(any());
		verify(repaymentAccountRepository).save(any());
		verify(depositAccountRepository).save(any());
	}
	
	@Test
	void createUserAccounts_ShouldThrowException_WhenUserAlreadyHasAccounts() {
		// Given
		User user = User.builder().id(1L).build();
		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(collateralAccountRepository.findByUserId(user.getId()))
				.thenReturn(Optional.of(new CollateralAccount()));

		// When & Then
		assertThatThrownBy(() -> accountService.createUserAccounts())
			.isInstanceOf(AccountsAlreadyCreatedException.class);
	}
	
}
