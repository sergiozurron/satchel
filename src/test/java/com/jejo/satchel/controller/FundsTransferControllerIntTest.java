package com.jejo.satchel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jejo.satchel.dto.WithdrawalRequest;
import com.jejo.satchel.model.AuthToken;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.FundsTransfer;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.AuthTokenRepository;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.FundsTransferRepository;
import com.jejo.satchel.repository.LoanRepository;
import com.jejo.satchel.repository.UserRepository;
import com.jejo.satchel.service.AssetCustodianService;
import com.jejo.satchel.util.JwtUtil;

@SpringBootTest
@AutoConfigureMockMvc
public class FundsTransferControllerIntTest {

	private static final String ASSET_ID = "USDC_ETH_TEST5_AN74";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AuthTokenRepository authTokenRepository;

	@Autowired
	private LoanRepository loanRepository;

	@Autowired
	private DepositWalletRepository depositWalletRepository;

	@Autowired
	private FundsTransferRepository fundsTransferRepository;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private AssetCustodianService assetCustodianService;

	private User testUser;
	private String jwtToken;

	@BeforeEach
	void setup() {
		Long random = System.currentTimeMillis() % 100000;
		testUser = userRepository.save(User.builder()
				.firstName("John")
				.lastName("Doe")
				.email("withdrawal.test" + random + "@example.com")
				.password("password123")
				.verified(true)
				.build());

		jwtToken = jwtUtil.generateToken(testUser);
		authTokenRepository.save(AuthToken.builder()
				.token(jwtToken)
				.user(testUser)
				.build());
	}

	@AfterEach
	void cleanup() {
		fundsTransferRepository.deleteAll();
		loanRepository.deleteAll();
		depositWalletRepository.deleteAll();
		authTokenRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void initiateWithdrawal_shouldReturnBadRequest_WhenAssetIsNotSupported() throws Exception {
		WithdrawalRequest request = new WithdrawalRequest();
		request.setAssetId("NOT_A_REAL_ASSET");
		request.setDestinationAddress("0xabc");
		request.setAmount(BigDecimal.ONE);

		mockMvc.perform(post("/api/v1/funds_transfer/withdrawal")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].error").value("ValidAssetId"))
				.andExpect(jsonPath("$[0].message").value("Asset not supported"));

		verify(assetCustodianService, never()).createTransaction(any(), any(), any(), any());
	}

	@Test
	void initiateWithdrawal_shouldReturnForbidden_WhenNoTokenProvided() throws Exception {
		WithdrawalRequest request = withdrawalRequest(ASSET_ID, "0xexternal", BigDecimal.ONE);

		postWithdrawal(request, false).andExpect(status().isForbidden());

		verify(assetCustodianService, never()).createTransaction(any(), any(), any(), any());
	}

	@Test
	void initiateWithdrawal_shouldReturnBadRequest_WhenAssetIdIsBlank() throws Exception {
		WithdrawalRequest request = withdrawalRequest("", "0xexternal", BigDecimal.ONE);

		postWithdrawal(request, true)
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", Matchers.hasSize(2)))
				.andExpect(jsonPath("$[*].message", Matchers.hasItem("Asset Id is required")))
				.andExpect(jsonPath("$[*].message", Matchers.hasItem("Asset not supported")));

		verify(assetCustodianService, never()).createTransaction(any(), any(), any(), any());
	}

	@Test
	void initiateWithdrawal_shouldReturnBadRequest_WhenDestinationAddressIsBlank() throws Exception {
		WithdrawalRequest request = withdrawalRequest(ASSET_ID, "", BigDecimal.ONE);

		postWithdrawal(request, true)
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].message").value("Destination address is required"));

		verify(assetCustodianService, never()).createTransaction(any(), any(), any(), any());
	}

	@Test
	void initiateWithdrawal_shouldReturnBadRequest_WhenAmountIsNegative() throws Exception {
		WithdrawalRequest request = withdrawalRequest(ASSET_ID, "0xexternal", new BigDecimal("-1"));

		postWithdrawal(request, true)
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].error").value("Min"))
				.andExpect(jsonPath("$[0].message").value("Amount must be greater than zero"));

		verify(assetCustodianService, never()).createTransaction(any(), any(), any(), any());
	}

	@Test
	void initiateWithdrawal_shouldReturnNotFound_WhenUserHasNoWalletForAsset() throws Exception {
		long transfersBefore = fundsTransferRepository.count();
		WithdrawalRequest request = withdrawalRequest(ASSET_ID, "0xexternal", BigDecimal.ONE);

		postWithdrawal(request, true)
				.andExpect(status().isNotFound())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_DEPOSIT_WALLET_NOT_FOUND))
				.andExpect(jsonPath("$.message").value("Deposit wallet not found for asset ID: " + ASSET_ID));

		assertThat(fundsTransferRepository.count()).isEqualTo(transfersBefore);
		verify(assetCustodianService, never()).createTransaction(any(), any(), any(), any());
	}

	@Test
	void initiateWithdrawal_shouldReturnBadRequest_WhenDestinationIsOwnWallet() throws Exception {
		DepositWallet wallet = saveWallet(testUser, "0xsource", "100", "0");
		long transfersBefore = fundsTransferRepository.count();
		WithdrawalRequest request = withdrawalRequest(ASSET_ID, "0xsource", new BigDecimal("25"));

		postWithdrawal(request, true)
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_SELF_TRANSFER))
				.andExpect(jsonPath("$.message").value("Cannot transfer funds to your own account"));

		assertThat(reload(wallet).getBalance()).isEqualByComparingTo("100");
		assertThat(fundsTransferRepository.count()).isEqualTo(transfersBefore);
		verify(assetCustodianService, never()).createTransaction(any(), any(), any(), any());
	}

	@Test
	void initiateWithdrawal_shouldMoveBalances_WhenDestinationIsAnotherUser() throws Exception {
		DepositWallet source = saveWallet(testUser, "0xsource", "100", "0");
		User otherUser = saveOtherUser();
		DepositWallet destination = saveWallet(otherUser, "0xdest", "10", "0");
		BigDecimal amount = new BigDecimal("25");
		WithdrawalRequest request = withdrawalRequest(ASSET_ID, "0xdest", amount);

		postWithdrawal(request, true)
				.andExpect(status().isOk())
				.andExpect(content().string(""));

		assertThat(reload(source).getBalance()).isEqualByComparingTo("75");
		assertThat(reload(destination).getBalance()).isEqualByComparingTo("35");

		List<FundsTransfer> sourceTransfers = transfersFor(source.getId());
		assertThat(sourceTransfers).hasSize(1);
		assertThat(sourceTransfers.get(0).getAmount()).isEqualByComparingTo("-25");
		assertThat(sourceTransfers.get(0).getCounterpartyAddress()).isEqualTo("0xdest");
		assertThat(sourceTransfers.get(0).getIsCompleted()).isTrue();

		List<FundsTransfer> destinationTransfers = transfersFor(destination.getId());
		assertThat(destinationTransfers).hasSize(1);
		assertThat(destinationTransfers.get(0).getAmount()).isEqualByComparingTo("25");
		assertThat(destinationTransfers.get(0).getCounterpartyAddress()).isEqualTo("0xsource");
		assertThat(destinationTransfers.get(0).getIsCompleted()).isTrue();

		verify(assetCustodianService, never()).createTransaction(any(), any(), any(), any());
	}

	@Test
	void initiateWithdrawal_shouldRecordPendingWithdrawal_WhenDestinationIsExternal() throws Exception {
		DepositWallet source = saveWallet(testUser, "0xsource", "100", "40");
		BigDecimal amount = new BigDecimal("59");
		String destination = "0xexternal";
		WithdrawalRequest request = withdrawalRequest(ASSET_ID, destination, amount);

		postWithdrawal(request, true)
				.andExpect(status().isOk())
				.andExpect(content().string(""));

		assertThat(reload(source).getBalance()).isEqualByComparingTo("100");

		List<FundsTransfer> transfers = transfersFor(source.getId());
		assertThat(transfers).hasSize(1);
		assertThat(transfers.get(0).getAmount()).isEqualByComparingTo("-59");
		assertThat(transfers.get(0).getCounterpartyAddress()).isEqualTo(destination);
		assertThat(transfers.get(0).getIsCompleted()).isFalse();
		assertThat(transfers.get(0).getTransactionId()).isNull();

		verify(assetCustodianService).createTransaction(eq(ASSET_ID), eq(amount), eq(testUser.getId()),
				eq(destination));
	}

	private ResultActions postWithdrawal(WithdrawalRequest request, boolean authenticated) throws Exception {
		MockHttpServletRequestBuilder requestBuilder = post("/api/v1/funds_transfer/withdrawal")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request));
		if (authenticated) {
			requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken);
		}
		return mockMvc.perform(requestBuilder);
	}

	private DepositWallet saveWallet(User user, String address, String balance, String lockedBalance) {
		return depositWalletRepository.save(DepositWallet.builder()
				.user(user)
				.address(address)
				.assetId(ASSET_ID)
				.balance(new BigDecimal(balance))
				.lockedBalance(new BigDecimal(lockedBalance))
				.accruedInterest(BigDecimal.ZERO)
				.openedAt(LocalDateTime.now())
				.build());
	}

	private User saveOtherUser() {
		return userRepository.save(User.builder()
				.firstName("Jane")
				.lastName("Roe")
				.email("withdrawal.dest" + System.nanoTime() + "@example.com")
				.password("password123")
				.verified(true)
				.build());
	}

	private WithdrawalRequest withdrawalRequest(String assetId, String destination, BigDecimal amount) {
		WithdrawalRequest request = new WithdrawalRequest();
		request.setAssetId(assetId);
		request.setDestinationAddress(destination);
		request.setAmount(amount);
		return request;
	}

	private DepositWallet reload(DepositWallet wallet) {
		return depositWalletRepository.findById(wallet.getId()).orElseThrow();
	}

	private List<FundsTransfer> transfersFor(Long walletId) {
		return fundsTransferRepository.findAll().stream()
				.filter(transfer -> walletId.equals(transfer.getDepositWallet().getId()))
				.toList();
	}

}
