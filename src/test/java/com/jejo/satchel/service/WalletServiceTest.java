package com.jejo.satchel.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.util.CurrentUserProvider;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class WalletServiceTest {

	@Mock
	private AssetCustodianService assetCustodianService;

	@Mock
	private CurrentUserProvider currentUserProvider;

	@Mock
	private DepositWalletRepository depositWalletRepository;

	@InjectMocks
	private WalletService walletService;

	private User testUser;
	private String testAssetId;
	private String walletAddress;

	@BeforeEach
	void setUp() {
		// Set up test data
		testUser = User.builder()
				.id(1L)
				.firstName("John")
				.lastName("Doe")
				.email("john.doe@example.com")
				.password("password123")
				.vaultAccountId(100L)
				.build();

		testAssetId = "USDC_ETH_TEST5_AN74";
		walletAddress = "0x1234567890abcdef";
	}

	@Test
	void createDepositWallet_shouldCreateAndSaveWallet_WhenValidAssetId() {
		// Arrange
		when(currentUserProvider.getCurrentUser()).thenReturn(testUser);
		when(assetCustodianService.createVaultWallet(testUser.getVaultAccountId(), testAssetId))
				.thenReturn(walletAddress);
		when(depositWalletRepository.save(any(DepositWallet.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		walletService.createDepositWallet(testAssetId);

		// Assert
		verify(currentUserProvider, times(2)).getCurrentUser();
		verify(assetCustodianService, times(1))
				.createVaultWallet(testUser.getVaultAccountId(), testAssetId);

		ArgumentCaptor<DepositWallet> walletCaptor = ArgumentCaptor.forClass(DepositWallet.class);
		verify(depositWalletRepository, times(1)).save(walletCaptor.capture());

		DepositWallet savedWallet = walletCaptor.getValue();
		assertThat(savedWallet.getAddress()).isEqualTo(walletAddress);
		assertThat(savedWallet.getAssetId()).isEqualTo(testAssetId);
		assertThat(savedWallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(savedWallet.getLockedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(savedWallet.getUser()).isEqualTo(testUser);
		assertThat(savedWallet.getOpenedAt()).isNotNull();
	}

	@Test
	void createDepositWallet_shouldCallAssetCustodianWithCorrectVaultId() {
		// Arrange
		when(currentUserProvider.getCurrentUser()).thenReturn(testUser);
		when(assetCustodianService.createVaultWallet(testUser.getVaultAccountId(), testAssetId))
				.thenReturn(walletAddress);
		when(depositWalletRepository.save(any(DepositWallet.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		walletService.createDepositWallet(testAssetId);

		// Assert
		ArgumentCaptor<Long> vaultIdCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<String> assetIdCaptor = ArgumentCaptor.forClass(String.class);
		verify(assetCustodianService, times(1))
				.createVaultWallet(vaultIdCaptor.capture(), assetIdCaptor.capture());

		assertThat(vaultIdCaptor.getValue()).isEqualTo(100L);
		assertThat(assetIdCaptor.getValue()).isEqualTo(testAssetId);
	}

	@Test
	void createDepositWallet_shouldSetCorrectBalances() {
		// Arrange
		when(currentUserProvider.getCurrentUser()).thenReturn(testUser);
		when(assetCustodianService.createVaultWallet(testUser.getVaultAccountId(), testAssetId))
				.thenReturn(walletAddress);
		when(depositWalletRepository.save(any(DepositWallet.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		walletService.createDepositWallet(testAssetId);

		// Assert
		ArgumentCaptor<DepositWallet> walletCaptor = ArgumentCaptor.forClass(DepositWallet.class);
		verify(depositWalletRepository).save(walletCaptor.capture());

		DepositWallet savedWallet = walletCaptor.getValue();
		assertThat(savedWallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(savedWallet.getLockedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void createDepositWallet_shouldUseEtheriumTestnetAsset() {
		// Arrange
		String ethAssetId = "ETH_TEST5";
		when(currentUserProvider.getCurrentUser()).thenReturn(testUser);
		when(assetCustodianService.createVaultWallet(testUser.getVaultAccountId(), ethAssetId))
				.thenReturn("0x9876543210fedcba");
		when(depositWalletRepository.save(any(DepositWallet.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		walletService.createDepositWallet(ethAssetId);

		// Assert
		ArgumentCaptor<DepositWallet> walletCaptor = ArgumentCaptor.forClass(DepositWallet.class);
		verify(depositWalletRepository).save(walletCaptor.capture());

		assertThat(walletCaptor.getValue().getAssetId()).isEqualTo(ethAssetId);
	}

}
