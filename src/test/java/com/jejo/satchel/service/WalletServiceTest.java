package com.jejo.satchel.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jejo.satchel.dto.WalletResponse;
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

	// Tests for getAllUserWallets() method

	@Test
	void getAllUserWallets_shouldReturnAllWalletsForCurrentUser() {
		// Arrange
		DepositWallet wallet1 = DepositWallet.builder()
				.id(1L)
				.address("0x1111111111111111")
				.assetId("USDC_ETH_TEST5_AN74")
				.balance(new BigDecimal("100.50"))
				.lockedBalance(new BigDecimal("10.00"))
				.openedAt(LocalDateTime.now().minusDays(10))
				.user(testUser)
				.build();

		DepositWallet wallet2 = DepositWallet.builder()
				.id(2L)
				.address("0x2222222222222222")
				.assetId("ETH_TEST5")
				.balance(new BigDecimal("5.25"))
				.lockedBalance(new BigDecimal("0.00"))
				.openedAt(LocalDateTime.now().minusDays(5))
				.user(testUser)
				.build();

		List<DepositWallet> wallets = Arrays.asList(wallet1, wallet2);
		when(currentUserProvider.getCurrentUser()).thenReturn(testUser);
		when(depositWalletRepository.findAllByUserId(testUser.getId())).thenReturn(wallets);

		// Act
		List<WalletResponse> result = walletService.getAllUserWallets();

		// Assert
		assertThat(result).isNotNull();
		assertThat(result).hasSize(2);
		assertThat(result.get(0).getId()).isEqualTo(1L);
		assertThat(result.get(0).getAddress()).isEqualTo("0x1111111111111111");
		assertThat(result.get(0).getAssetId()).isEqualTo("USDC_ETH_TEST5_AN74");
		assertThat(result.get(0).getBalance()).isEqualByComparingTo(new BigDecimal("100.50"));
		assertThat(result.get(0).getLockedBalance()).isEqualByComparingTo(new BigDecimal("10.00"));
		assertThat(result.get(0).getAvailableBalance()).isEqualByComparingTo(new BigDecimal("90.50"));

		assertThat(result.get(1).getId()).isEqualTo(2L);
		assertThat(result.get(1).getAssetId()).isEqualTo("ETH_TEST5");
		verify(currentUserProvider, times(1)).getCurrentUser();
		verify(depositWalletRepository, times(1)).findAllByUserId(testUser.getId());
	}

	@Test
	void getAllUserWallets_shouldReturnEmptyList_WhenUserHasNoWallets() {
		// Arrange
		when(currentUserProvider.getCurrentUser()).thenReturn(testUser);
		when(depositWalletRepository.findAllByUserId(testUser.getId())).thenReturn(Arrays.asList());

		// Act
		List<WalletResponse> result = walletService.getAllUserWallets();

		// Assert
		assertThat(result).isNotNull();
		assertThat(result).isEmpty();
		verify(depositWalletRepository, times(1)).findAllByUserId(testUser.getId());
	}

	@Test
	void getAllUserWallets_shouldMapWalletPropertiesCorrectly() {
		// Arrange
		LocalDateTime openedAt = LocalDateTime.now().minusDays(7);
		DepositWallet wallet = DepositWallet.builder()
				.id(5L)
				.address("0x5555555555555555")
				.assetId("USDC_ETH_TEST5_AN74")
				.balance(new BigDecimal("250.75"))
				.lockedBalance(new BigDecimal("50.25"))
				.openedAt(openedAt)
				.user(testUser)
				.build();

		when(currentUserProvider.getCurrentUser()).thenReturn(testUser);
		when(depositWalletRepository.findAllByUserId(testUser.getId())).thenReturn(Arrays.asList(wallet));

		// Act
		List<WalletResponse> result = walletService.getAllUserWallets();

		// Assert
		assertThat(result).hasSize(1);
		WalletResponse response = result.get(0);
		assertThat(response.getId()).isEqualTo(5L);
		assertThat(response.getAddress()).isEqualTo("0x5555555555555555");
		assertThat(response.getAssetId()).isEqualTo("USDC_ETH_TEST5_AN74");
		assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("250.75"));
		assertThat(response.getLockedBalance()).isEqualByComparingTo(new BigDecimal("50.25"));
		assertThat(response.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("200.50"));
		assertThat(response.getOpenedAt()).isEqualTo(openedAt);
		assertThat(response.getDaysSinceOpened()).isEqualTo(7L);
	}

	@Test
	void getAllUserWallets_shouldCalculateAvailableBalance() {
		// Arrange
		DepositWallet wallet = DepositWallet.builder()
				.id(10L)
				.address("0xabcdabcdabcdabcd")
				.assetId("ETH_TEST5")
				.balance(new BigDecimal("50.00"))
				.lockedBalance(new BigDecimal("15.00"))
				.openedAt(LocalDateTime.now().minusDays(3))
				.user(testUser)
				.build();

		when(currentUserProvider.getCurrentUser()).thenReturn(testUser);
		when(depositWalletRepository.findAllByUserId(testUser.getId())).thenReturn(Arrays.asList(wallet));

		// Act
		List<WalletResponse> result = walletService.getAllUserWallets();

		// Assert
		assertThat(result.get(0).getAvailableBalance()).isEqualByComparingTo(new BigDecimal("35.00"));
	}

}
