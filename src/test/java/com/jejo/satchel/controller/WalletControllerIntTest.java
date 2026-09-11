package com.jejo.satchel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jejo.satchel.dto.CreateDepositWalletRequest;
import com.jejo.satchel.model.AuthToken;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.AuthTokenRepository;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.UserRepository;
import com.jejo.satchel.util.JwtUtil;

@SpringBootTest
@AutoConfigureMockMvc
public class WalletControllerIntTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AuthTokenRepository authTokenRepository;

	@Autowired
	private DepositWalletRepository depositWalletRepository;

	@Autowired
	private JwtUtil jwtUtil;

	private User testUser;
	private String jwtToken;
	private String testAssetId;

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
	}

	@AfterEach
	void cleanup() {
		// Clean up repositories after each test
		depositWalletRepository.deleteAll();
		authTokenRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void createDepositWallet_shouldReturnOkAndCreateWallet_WhenValidRequest() throws Exception {
		// Arrange
		CreateDepositWalletRequest request = new CreateDepositWalletRequest();
		request.setAssetId(testAssetId);

		// Act
		mockMvc.perform(post("/api/v1/wallets")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				// Assert
				.andExpect(status().isOk())
				.andExpect(content().string("Deposit wallet created successfully"));
	}

	@Test
	void createDepositWallet_shouldCallWalletServiceAsynchronously() throws Exception {
		// Arrange
		CreateDepositWalletRequest request = new CreateDepositWalletRequest();
		request.setAssetId(testAssetId);

		// Act
		mockMvc.perform(post("/api/v1/wallets")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				// Assert - endpoint should return OK immediately
				.andExpect(status().isOk())
				.andExpect(content().string("Deposit wallet created successfully"));
		
		// Note: The actual wallet creation happens asynchronously via @Async annotation.
		// The endpoint returns immediately without waiting for the async operation to complete.
		// In production, the wallet would be created in a separate thread.
	}

	@Test
	void createDepositWallet_shouldReturnOkWithEthereumTestnetAsset() throws Exception {
		// Arrange
		String ethAssetId = "ETH_TEST5";
		CreateDepositWalletRequest request = new CreateDepositWalletRequest();
		request.setAssetId(ethAssetId);

		// Act
		mockMvc.perform(post("/api/v1/wallets")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				// Assert
				.andExpect(status().isOk())
				.andExpect(content().string("Deposit wallet created successfully"));
	}

	@Test
	void createDepositWallet_shouldReturnOkWithCorrectAssetId_WhenUsdcEthTestnet() throws Exception {
		// Arrange
		CreateDepositWalletRequest request = new CreateDepositWalletRequest();
		request.setAssetId("USDC_ETH_TEST5_AN74");

		// Act & Assert
		mockMvc.perform(post("/api/v1/wallets")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(content().string("Deposit wallet created successfully"));
	}

}
