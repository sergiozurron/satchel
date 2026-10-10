package com.jejo.satchel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jejo.satchel.dto.CreateDepositWalletRequest;
import com.jejo.satchel.model.AuthToken;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.AuthTokenRepository;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.LoanRepository;
import com.jejo.satchel.repository.UserRepository;
import com.jejo.satchel.service.AssetCustodianService;
import com.jejo.satchel.util.JwtUtil;

@SpringBootTest
@AutoConfigureMockMvc
public class WalletControllerIntTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;
	
	@Autowired
	private LoanRepository loanRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AuthTokenRepository authTokenRepository;

	@Autowired
	private DepositWalletRepository depositWalletRepository;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private AssetCustodianService assetCustodianService;

	private User testUser;
	private String jwtToken;
	private String testAssetId;
	private String testVaultAccountId;
	private String testWalletAddress;


	@BeforeEach
	void setup() {
		// Create a test user
		Long random = System.currentTimeMillis() % 100000;
		testUser = userRepository.save(User.builder()
				.firstName("John")
				.lastName("Doe")
				.email("wallet.test" + random + "@example.com")
				.password("password123")
				.verified(true)
				.build());

		// Generate JWT token
		jwtToken = jwtUtil.generateToken(testUser);
		authTokenRepository.save(AuthToken.builder()
				.token(jwtToken)
				.user(testUser)
				.build());

		testAssetId = "USDC_ETH_TEST5_AN74";
		testVaultAccountId = "100";
		testWalletAddress = "0xabc";
	}

	@AfterEach
	void cleanup() {
		// Clean up repositories after each test
		loanRepository.deleteAll();
		depositWalletRepository.deleteAll();
		authTokenRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void createDepositWallet_shouldReturnOkAndCreateWallet_WhenValidRequest() throws Exception {
		// Arrange
		testUser.setVaultAccountId(testVaultAccountId);
		testUser = userRepository.save(testUser);
		when(assetCustodianService.createVaultWallet(testVaultAccountId, testAssetId))
				.thenReturn(testWalletAddress);

		CreateDepositWalletRequest request = new CreateDepositWalletRequest();
		request.setAssetId(testAssetId);

		// Act
		mockMvc.perform(post("/api/v1/wallets")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				// Assert
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.message").value("Deposit wallet created successfully"));

		DepositWallet saved = depositWalletRepository
				.findByUserIdAndAssetId(testUser.getId(), testAssetId)
				.orElseThrow();
		assertThat(saved.getAddress()).isEqualTo(testWalletAddress);
		assertThat(saved.getAssetId()).isEqualTo(testAssetId);
		assertThat(saved.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
		verify(assetCustodianService).createVaultWallet(testVaultAccountId, testAssetId);
	}

	@Test 
	void createDepositWallet_shouldReturnConflict_WhenWalletAlreadyExists() throws Exception {
		// Arrange - Create a wallet for the test user
		depositWalletRepository.save(DepositWallet.builder()
				.address("0x1234567890abcdef")
				.assetId(testAssetId)
				.balance(BigDecimal.ZERO)
				.lockedBalance(BigDecimal.ZERO)
				.accruedInterest(BigDecimal.ZERO)
				.openedAt(LocalDateTime.now())
				.user(testUser)
				.build());

		CreateDepositWalletRequest request = new CreateDepositWalletRequest();
		request.setAssetId(testAssetId);

		// Act & Assert
		mockMvc.perform(post("/api/v1/wallets")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isConflict())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_DEPOSIT_WALLET_ALREADY_EXISTS))
				.andExpect(jsonPath("$.message").value(
						"Deposit wallet for asset " + testAssetId + " already exists for the current user."));
		verify(assetCustodianService, never()).createVaultWallet(any(), any());
	}

	// Tests for GET /api/v1/wallets endpoint

	@Test
	void getAllUserWallets_shouldReturnEmptyList_WhenUserHasNoWallets() throws Exception {
		// Act & Assert
		mockMvc.perform(get("/api/v1/wallets")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", Matchers.hasSize(0)));
	}

	@Test
	void getAllUserWallets_shouldReturnAllWalletsForUser() throws Exception {
		// Arrange - Create two wallets for the test user
		DepositWallet wallet1 = depositWalletRepository.save(DepositWallet.builder()
				.address("0x1111111111111111")
				.assetId("USDC_ETH_TEST5_AN74")
				.balance(new BigDecimal("100.50"))
				.lockedBalance(new BigDecimal("10.00"))
				.accruedInterest(new BigDecimal("2.50"))
				.openedAt(LocalDateTime.now().minusDays(10))
				.user(testUser)
				.build());

		DepositWallet wallet2 = depositWalletRepository.save(DepositWallet.builder()
				.address("0x2222222222222222")
				.assetId("ETH_TEST5")
				.balance(new BigDecimal("5.25"))
				.lockedBalance(new BigDecimal("0.00"))
				.openedAt(LocalDateTime.now().minusDays(5))
				.accruedInterest(BigDecimal.ZERO)
				.user(testUser)
				.build());

		// Act & Assert
		mockMvc.perform(get("/api/v1/wallets")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", Matchers.hasSize(2)))
				.andExpect(jsonPath("$[0].id", Matchers.equalTo(wallet1.getId().intValue())))
				.andExpect(jsonPath("$[0].address", Matchers.equalTo("0x1111111111111111")))
				.andExpect(jsonPath("$[0].assetId", Matchers.equalTo("USDC_ETH_TEST5_AN74")))
				.andExpect(jsonPath("$[0].balance", Matchers.equalTo(100.50)))
				.andExpect(jsonPath("$[0].lockedBalance", Matchers.equalTo(10.00)))
				.andExpect(jsonPath("$[0].availableBalance", Matchers.equalTo(90.50)))
				.andExpect(jsonPath("$[1].id", Matchers.equalTo(wallet2.getId().intValue())))
				.andExpect(jsonPath("$[1].address", Matchers.equalTo("0x2222222222222222")))
				.andExpect(jsonPath("$[1].assetId", Matchers.equalTo("ETH_TEST5")));
	}

	@Test
	void getAllUserWallets_shouldIncludeWalletDetails() throws Exception {
		// Arrange
		LocalDateTime openedAt = LocalDateTime.now().minusDays(7);
		DepositWallet wallet = depositWalletRepository.save(DepositWallet.builder()
				.address("0x5555555555555555")
				.assetId("USDC_ETH_TEST5_AN74")
				.balance(new BigDecimal("250.75"))
				.lockedBalance(new BigDecimal("50.25"))
				.accruedInterest(new BigDecimal("5.00"))
				.openedAt(openedAt)
				.user(testUser)
				.build());

		// Act & Assert
		mockMvc.perform(get("/api/v1/wallets")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].id", Matchers.equalTo(wallet.getId().intValue())))
				.andExpect(jsonPath("$[0].address", Matchers.equalTo("0x5555555555555555")))
				.andExpect(jsonPath("$[0].balance", Matchers.equalTo(250.75)))
				.andExpect(jsonPath("$[0].lockedBalance", Matchers.equalTo(50.25)))
				.andExpect(jsonPath("$[0].availableBalance", Matchers.equalTo(200.50)))
				.andExpect(jsonPath("$[0].daysSinceOpened", Matchers.equalTo(7)))
				.andExpect(jsonPath("$[0].accruedInterest", Matchers.equalTo(5.00)))
				.andExpect(jsonPath("$[0].openedAt", Matchers.notNullValue()));
	}

	@Test
	void getAllUserWallets_shouldCalculateAvailableBalance() throws Exception {
		depositWalletRepository.save(DepositWallet.builder()
				.address("0x5555555555555555")
				.assetId("USDC_ETH_TEST5_AN74")
				.balance(new BigDecimal("250.75"))
				.lockedBalance(new BigDecimal("50.25"))
				.accruedInterest(new BigDecimal("5.00"))
				.openedAt(LocalDateTime.now().minusDays(7))
				.user(testUser)
				.build());
		
		// Act & Assert
		mockMvc.perform(get("/api/v1/wallets")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].balance", Matchers.equalTo(250.75)))
				.andExpect(jsonPath("$[0].lockedBalance", Matchers.equalTo(50.25)))
				.andExpect(jsonPath("$[0].availableBalance", Matchers.equalTo(200.50)));
	}

	@Test
	void getAllUserWallets_shouldReturnOnlyCurrentUserWallets() throws Exception {
		// Arrange - Create another user and their wallet
		Long random = System.currentTimeMillis() % 100000;
		User anotherUser = userRepository.save(User.builder()
				.firstName("Jane")
				.lastName("Smith")
				.email("another.user" + random + "@example.com")
				.password("password456")
				.verified(true)
				.build());

		// Create wallet for current user
		DepositWallet currentUserWallet = depositWalletRepository.save(DepositWallet.builder()
				.address("0x1111111111111111")
				.assetId("USDC_ETH_TEST5_AN74")
				.balance(new BigDecimal("100.00"))
				.lockedBalance(new BigDecimal("0.00"))
				.accruedInterest(BigDecimal.ZERO)
				.openedAt(LocalDateTime.now())
				.user(testUser)
				.build());

		// Create wallet for another user
		DepositWallet anotherUserWallet = depositWalletRepository.save(DepositWallet.builder()
				.address("0x2222222222222222")
				.assetId("ETH_TEST5")
				.balance(new BigDecimal("50.00"))
				.lockedBalance(new BigDecimal("0.00"))
				.accruedInterest(BigDecimal.ZERO)
				.openedAt(LocalDateTime.now())
				.user(anotherUser)
				.build());

		// Act & Assert
		mockMvc.perform(get("/api/v1/wallets")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].id", Matchers.equalTo(currentUserWallet.getId().intValue())))
				.andExpect(jsonPath("$[0].address", Matchers.equalTo("0x1111111111111111")));

		// Clean up another user's wallet
		depositWalletRepository.delete(anotherUserWallet);
		userRepository.delete(anotherUser);
	}

	@Test
	void getAllUserWallets_shouldReturnForbidden_WhenNoTokenProvided() throws Exception {
		// Act & Assert
		mockMvc.perform(get("/api/v1/wallets")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isForbidden());
	}

	@Test
	void getAllUserWallets_shouldReturnMultipleWalletsWithCorrectOrdering() throws Exception {
		// Arrange - Create three wallets
		DepositWallet wallet1 = depositWalletRepository.save(DepositWallet.builder()
				.address("0x1111111111111111")
				.assetId("USDC_ETH_TEST5_AN74")
				.balance(new BigDecimal("100.00"))
				.lockedBalance(new BigDecimal("0.00"))
				.accruedInterest(BigDecimal.ZERO)
				.openedAt(LocalDateTime.now().minusDays(15))
				.user(testUser)
				.build());

		DepositWallet wallet2 = depositWalletRepository.save(DepositWallet.builder()
				.address("0x2222222222222222")
				.assetId("ETH_TEST5")
				.balance(new BigDecimal("50.00"))
				.lockedBalance(new BigDecimal("0.00"))
				.accruedInterest(BigDecimal.ZERO)
				.openedAt(LocalDateTime.now().minusDays(10))
				.user(testUser)
				.build());

		DepositWallet wallet3 = depositWalletRepository.save(DepositWallet.builder()
				.address("0x3333333333333333")
				.assetId("USDC_ETH_TEST5_AN74")
				.balance(new BigDecimal("75.50"))
				.lockedBalance(new BigDecimal("5.50"))
				.accruedInterest(new BigDecimal("1.00"))
				.openedAt(LocalDateTime.now().minusDays(5))
				.user(testUser)
				.build());

		// Act & Assert
		mockMvc.perform(get("/api/v1/wallets")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(3)))
				.andExpect(jsonPath("$[0].id", Matchers.equalTo(wallet1.getId().intValue())))
				.andExpect(jsonPath("$[1].id", Matchers.equalTo(wallet2.getId().intValue())))
				.andExpect(jsonPath("$[2].id", Matchers.equalTo(wallet3.getId().intValue())));
	}

}
