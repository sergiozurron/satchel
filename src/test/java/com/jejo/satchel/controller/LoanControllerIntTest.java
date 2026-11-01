package com.jejo.satchel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jejo.satchel.dto.CreateLoanRequest;
import com.jejo.satchel.dto.LoanRepaymentRequest;
import com.jejo.satchel.dto.UnlockCollateralRequest;
import com.jejo.satchel.model.AuthToken;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.Loan;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.AuthTokenRepository;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.LoanRepository;
import com.jejo.satchel.repository.UserRepository;
import com.jejo.satchel.service.AssetCustodianService;
import com.jejo.satchel.service.AssetTradingService;
import com.jejo.satchel.util.JwtUtil;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LoanControllerIntTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private LoanRepository loanRepository;
	@Autowired
	private DepositWalletRepository depositWalletRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private JwtUtil jwtUtil;
	@Autowired
	private AuthTokenRepository authTokenRepository;

	@MockitoBean
	private AssetCustodianService assetCustodianService;

	@MockitoBean
	private AssetTradingService conversionRateService;

	private User testUser;
	private Loan activeLoan;
	private DepositWallet depositWallet;
	private DepositWallet collateralWallet;
	private String jwtToken;

	@BeforeEach
	void setup() {
		// Clear repositories before each test
		authTokenRepository.deleteAll();
		loanRepository.deleteAll();
		depositWalletRepository.deleteAll();
		userRepository.deleteAll();

		// Create test user
		testUser = User.builder().firstName("John").lastName("Doe").email("john@example.com")
				.password(passwordEncoder.encode("password123")).verified(true).build();
		testUser = userRepository.save(testUser);
		jwtToken = jwtUtil.generateToken(testUser);
		authTokenRepository.save(AuthToken.builder().token(jwtToken).user(testUser).build());

		// Create deposit wallet with sufficient balance
		depositWallet = DepositWallet.builder().address("0x1234567890abcdef").assetId("USDC")
				.balance(new BigDecimal("1000.0000000")).lockedBalance(BigDecimal.ZERO)
				.accruedInterest(BigDecimal.ZERO).openedAt(LocalDateTime.now()).user(testUser)
				.build();
		depositWallet = depositWalletRepository.save(depositWallet);

		// Collateral locked at origination. Repayment leaves this balance locked.
		collateralWallet = DepositWallet.builder().address("0xethcollateral").assetId("ETH")
				.balance(new BigDecimal("2.0000000")).lockedBalance(new BigDecimal("0.5000000"))
				.accruedInterest(BigDecimal.ZERO).openedAt(LocalDateTime.now()).user(testUser)
				.build();
		collateralWallet = depositWalletRepository.save(collateralWallet);

		// Create active loan
		activeLoan = Loan.builder().loanAssetId("USDC").collateralAssetId("ETH")
				.returnedAmount(BigDecimal.ZERO).amount(new BigDecimal("500.0000000"))
				.collateralAmount(new BigDecimal("0.5000000"))
				.currentCollateral(new BigDecimal("0.5000000"))
				.interestRate(new BigDecimal("0.050000"))
				.accruedInterest(new BigDecimal("5.0000000")).grantedAt(LocalDateTime.now())
				.user(testUser).build();
		activeLoan = loanRepository.save(activeLoan);
	}

	@Test
	void repayLoan_shouldReturnOk_whenPartialRepaymentSuccessful() throws Exception {
		// Given
		BigDecimal repaymentAmount = new BigDecimal("100.0000000");
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(repaymentAmount).build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/{id}/repayments", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id", Matchers.equalTo(activeLoan.getId().intValue())))
				.andExpect(jsonPath("$.loanAssetId", Matchers.equalTo("USDC")))
				.andExpect(jsonPath("$.amount", Matchers.equalTo(500.0)))
				.andExpect(jsonPath("$.returnedAmount", Matchers.equalTo(100.0)))
				.andExpect(jsonPath("$.outstandingAmount", Matchers.equalTo(405.0)))
				.andExpect(jsonPath("$.collateralAssetId", Matchers.equalTo("ETH")))
				.andExpect(jsonPath("$.collateralAmount", Matchers.equalTo(0.5)))
				.andExpect(jsonPath("$.interestRate", Matchers.equalTo(0.05)))
				.andExpect(jsonPath("$.isPaid", Matchers.equalTo(false)))
				.andExpect(jsonPath("$.accruedInterest", Matchers.equalTo(5.0)))
				.andExpect(jsonPath("$.grantedAt", Matchers.notNullValue()))
				.andExpect(jsonPath("$.user").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist());

		DepositWallet updatedLoanWallet = depositWalletRepository.findById(depositWallet.getId())
				.orElseThrow();
		DepositWallet updatedCollateral = depositWalletRepository.findById(collateralWallet.getId())
				.orElseThrow();
		Loan updatedLoan = loanRepository.findById(activeLoan.getId()).orElseThrow();
		assertThat(updatedLoanWallet.getBalance()).isEqualByComparingTo("900");
		assertThat(updatedLoan.getCurrentCollateral()).isEqualByComparingTo("0.5");
		assertThat(updatedCollateral.getLockedBalance()).isEqualByComparingTo("0.5");
		assertThat(updatedCollateral.getBalance()).isEqualByComparingTo("2");
	}

	@Test
	void repayLoan_shouldReturnOk_whenFullRepaymentSuccessful() throws Exception {
		// Given
		BigDecimal repaymentAmount = new BigDecimal("505.0000000");
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(repaymentAmount).build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/{id}/repayments", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.returnedAmount", Matchers.equalTo(505.0)))
				.andExpect(jsonPath("$.outstandingAmount", Matchers.equalTo(0.0)))
				.andExpect(jsonPath("$.isPaid", Matchers.equalTo(true)));

		DepositWallet updatedLoanWallet = depositWalletRepository.findById(depositWallet.getId())
				.orElseThrow();
		DepositWallet updatedCollateral = depositWalletRepository.findById(collateralWallet.getId())
				.orElseThrow();
		Loan updatedLoan = loanRepository.findById(activeLoan.getId()).orElseThrow();
		assertThat(updatedLoanWallet.getBalance()).isEqualByComparingTo("495");
		assertThat(updatedCollateral.getLockedBalance()).isEqualByComparingTo("0.5");
		assertThat(updatedLoan.isPaid()).isTrue();
		assertThat(updatedLoan.getCurrentCollateral()).isEqualByComparingTo("0.5");
		assertThat(updatedLoan.getReturnedAmount()).isEqualByComparingTo("505");
	}

	@Test
	void repayLoan_shouldReturnBadRequest_whenRepaymentAmountIsZero() throws Exception {
		// Given
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(BigDecimal.ZERO).build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/{id}/repayments", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void repayLoan_shouldReturnBadRequest_whenRepaymentAmountIsNegative() throws Exception {
		// Given
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(new BigDecimal("-100.0000000")).build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/{id}/repayments", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void repayLoan_shouldReturnNotFound_whenLoanDoesNotExist() throws Exception {
		// Given
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(new BigDecimal("100.0000000")).build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/{id}/repayments", 99999L).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isNotFound());
	}

	@Test
	void repayLoan_shouldReturnBadRequest_whenLoanIsNotActive() throws Exception {
		// Given - Create and save a paid loan
		Loan paidLoan = Loan.builder().loanAssetId("USDC").collateralAssetId("ETH")
				.returnedAmount(new BigDecimal("505.0000000")).amount(new BigDecimal("500.0000000"))
				.collateralAmount(new BigDecimal("0.5000000"))
				.currentCollateral(new BigDecimal("0.5000000"))
				.interestRate(new BigDecimal("0.050000"))
				.accruedInterest(new BigDecimal("5.0000000")).grantedAt(LocalDateTime.now())
				.user(testUser).build();
		paidLoan = loanRepository.save(paidLoan);

		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(new BigDecimal("100.0000000")).build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/{id}/repayments", paidLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isConflict());
	}

	@Test
	void repayLoan_shouldReturnBadRequest_whenInsufficientBalance() throws Exception {
		// Given - Update deposit wallet with insufficient balance
		depositWallet.setBalance(new BigDecimal("10.0000000"));
		depositWalletRepository.save(depositWallet);

		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(new BigDecimal("100.0000000")).build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/{id}/repayments", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isBadRequest());

		DepositWallet unchangedLoanWallet = depositWalletRepository.findById(depositWallet.getId())
				.orElseThrow();
		DepositWallet unchangedCollateral = depositWalletRepository.findById(collateralWallet.getId())
				.orElseThrow();
		assertThat(unchangedLoanWallet.getBalance()).isEqualByComparingTo("10");
		assertThat(unchangedCollateral.getLockedBalance()).isEqualByComparingTo("0.5");
	}

	@Test
	void repayLoan_shouldDebitWalletBalance_onSuccessfulRepayment() throws Exception {
		// Given
		BigDecimal initialBalance = depositWallet.getBalance();
		BigDecimal repaymentAmount = new BigDecimal("100.0000000");
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(repaymentAmount).build();

		// When
		mockMvc.perform(post("/api/v1/loans/{id}/repayments", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isOk());

		// Then - Verify wallet balance was debited
		DepositWallet updatedWallet = depositWalletRepository.findById(depositWallet.getId())
				.orElseThrow();
		BigDecimal expectedBalance = initialBalance.subtract(repaymentAmount);
		assertThat(updatedWallet.getBalance()).isEqualTo(expectedBalance);
		DepositWallet updatedCollateral = depositWalletRepository.findById(collateralWallet.getId())
				.orElseThrow();
		assertThat(updatedCollateral.getLockedBalance()).isEqualByComparingTo("0.5");
		assertThat(updatedCollateral.getBalance()).isEqualByComparingTo("2");
	}

	@Test
	void repayLoan_shouldReturnNotFound_whenUserDoesNotOwnLoan() throws Exception {
		// Given - Create another user
		User otherUser = User.builder().firstName("Jane").lastName("Smith")
				.email("other@example.com").password(passwordEncoder.encode("password123"))
				.verified(true).build();
		otherUser = userRepository.save(otherUser);
		String otherJwt = jwtUtil.generateToken(otherUser);
		authTokenRepository.save(AuthToken.builder().token(otherJwt).user(otherUser).build());

		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(new BigDecimal("100.0000000")).build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/{id}/repayments", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherJwt)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isNotFound());
	}

	@Test
	void repayLoan_shouldReturnBadRequest_whenAmountIsNull() throws Exception {
		// Given
		String requestBody = "{}";

		// When & Then
		mockMvc.perform(post("/api/v1/loans/{id}/repayments", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isBadRequest());
	}

	@Test
	void repayLoan_shouldUpdateLoanReturnedAmount_onSuccessfulRepayment() throws Exception {
		// Given
		BigDecimal repaymentAmount = new BigDecimal("150.0000000");
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(repaymentAmount).build();

		// When
		mockMvc.perform(post("/api/v1/loans/{id}/repayments", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isOk());

		// Then - Verify loan's returned amount was updated and collateral stayed locked
		Loan updatedLoan = loanRepository.findById(activeLoan.getId()).orElseThrow();
		assertThat(updatedLoan.getReturnedAmount()).isEqualTo(repaymentAmount);
		assertThat(updatedLoan.isPaid()).isFalse();
		assertThat(updatedLoan.getCurrentCollateral()).isEqualByComparingTo("0.5");
		DepositWallet updatedLoanWallet = depositWalletRepository.findById(depositWallet.getId())
				.orElseThrow();
		DepositWallet updatedCollateral = depositWalletRepository.findById(collateralWallet.getId())
				.orElseThrow();
		assertThat(updatedLoanWallet.getBalance()).isEqualByComparingTo("850");
		assertThat(updatedCollateral.getLockedBalance()).isEqualByComparingTo("0.5");
	}

	@Test
	void repayLoan_shouldAllowMultiplePartialRepayments() throws Exception {
		// Given - First repayment
		BigDecimal firstRepayment = new BigDecimal("100.0000000");
		LoanRepaymentRequest firstRequest = LoanRepaymentRequest.builder()
				.amount(firstRepayment).build();

		// When - Perform first repayment
		mockMvc.perform(post("/api/v1/loans/{id}/repayments", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(firstRequest))).andExpect(status().isOk());

		// Given - Second repayment
		BigDecimal secondRepayment = new BigDecimal("200.0000000");
		LoanRepaymentRequest secondRequest = LoanRepaymentRequest.builder()
				.amount(secondRepayment).build();

		// Then - Perform second repayment
		mockMvc.perform(post("/api/v1/loans/{id}/repayments", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(secondRequest))).andExpect(status().isOk())
				.andExpect(jsonPath("$.returnedAmount", Matchers.equalTo(300.0)))
				.andExpect(jsonPath("$.outstandingAmount", Matchers.equalTo(205.0)))
				.andExpect(jsonPath("$.isPaid", Matchers.equalTo(false)));

		DepositWallet updatedLoanWallet = depositWalletRepository.findById(depositWallet.getId())
				.orElseThrow();
		DepositWallet updatedCollateral = depositWalletRepository.findById(collateralWallet.getId())
				.orElseThrow();
		assertThat(updatedLoanWallet.getBalance()).isEqualByComparingTo("700");
		Loan updatedLoan = loanRepository.findById(activeLoan.getId()).orElseThrow();
		assertThat(updatedLoan.getCurrentCollateral()).isEqualByComparingTo("0.5");
		assertThat(updatedCollateral.getLockedBalance()).isEqualByComparingTo("0.5");
	}

	@Test
	void repayLoan_shouldReturnInternalServerError_whenLoanWalletMissing() throws Exception {
		depositWalletRepository.delete(depositWallet);

		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(new BigDecimal("100.0000000")).build();

		mockMvc.perform(post("/api/v1/loans/{id}/repayments", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_INTERNAL_SERVER_ERROR));

		Loan unchangedLoan = loanRepository.findById(activeLoan.getId()).orElseThrow();
		DepositWallet unchangedCollateral = depositWalletRepository.findById(collateralWallet.getId())
				.orElseThrow();
		assertThat(unchangedLoan.getReturnedAmount()).isEqualByComparingTo("0");
		assertThat(unchangedLoan.isPaid()).isFalse();
		assertThat(unchangedCollateral.getLockedBalance()).isEqualByComparingTo("0.5");
	}

	@Test
	void repayLoan_shouldSucceed_whenCollateralWalletMissing() throws Exception {
		depositWalletRepository.delete(collateralWallet);

		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(new BigDecimal("100.0000000")).build();

		mockMvc.perform(post("/api/v1/loans/{id}/repayments", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.returnedAmount", Matchers.equalTo(100.0)))
				.andExpect(jsonPath("$.currentCollateral", Matchers.equalTo(0.5)));

		Loan updatedLoan = loanRepository.findById(activeLoan.getId()).orElseThrow();
		DepositWallet updatedLoanWallet = depositWalletRepository.findById(depositWallet.getId())
				.orElseThrow();
		assertThat(updatedLoan.getReturnedAmount()).isEqualByComparingTo("100");
		assertThat(updatedLoan.getCurrentCollateral()).isEqualByComparingTo("0.5");
		assertThat(updatedLoanWallet.getBalance()).isEqualByComparingTo("900");
	}

	@Test
	void getAllUserLoans_shouldReturnTheCurrentUsersLoan() throws Exception {
		mockMvc.perform(
				get("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].id", Matchers.equalTo(activeLoan.getId().intValue())))
				.andExpect(jsonPath("$[0].loanAssetId", Matchers.equalTo("USDC")))
				.andExpect(jsonPath("$[0].amount", Matchers.equalTo(500.0)))
				.andExpect(jsonPath("$[0].returnedAmount", Matchers.equalTo(0.0)))
				.andExpect(jsonPath("$[0].outstandingAmount", Matchers.equalTo(505.0)))
				.andExpect(jsonPath("$[0].collateralAssetId", Matchers.equalTo("ETH")))
				.andExpect(jsonPath("$[0].collateralAmount", Matchers.equalTo(0.5)))
				.andExpect(jsonPath("$[0].currentCollateral", Matchers.equalTo(0.5)))
				.andExpect(jsonPath("$[0].interestRate", Matchers.equalTo(0.05)))
				.andExpect(jsonPath("$[0].isPaid", Matchers.equalTo(false)))
				.andExpect(jsonPath("$[0].accruedInterest", Matchers.equalTo(5.0)))
				.andExpect(jsonPath("$[0].grantedAt", Matchers.notNullValue()))
				.andExpect(jsonPath("$[0].user").doesNotExist())
				.andExpect(jsonPath("$[0].password").doesNotExist());
	}

	@Test
	void getAllUserLoans_shouldReturnOnlyTheCallersLoans() throws Exception {
		User otherUser = User.builder().firstName("Jane").lastName("Smith")
				.email("other-loans@example.com").password(passwordEncoder.encode("password123"))
				.verified(true).build();
		otherUser = userRepository.save(otherUser);
		String otherJwt = jwtUtil.generateToken(otherUser);
		authTokenRepository.save(AuthToken.builder().token(otherJwt).user(otherUser).build());

		Loan otherLoan = loanRepository.save(Loan.builder().loanAssetId("USDC")
				.collateralAssetId("ETH").returnedAmount(BigDecimal.ZERO)
				.amount(new BigDecimal("20.0000000")).collateralAmount(new BigDecimal("0.1000000"))
				.currentCollateral(new BigDecimal("0.1000000"))
				.interestRate(new BigDecimal("0.050000"))
				.accruedInterest(BigDecimal.ZERO).grantedAt(LocalDateTime.now()).user(otherUser)
				.build());

		mockMvc.perform(
				get("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].id", Matchers.equalTo(activeLoan.getId().intValue())))
				.andExpect(jsonPath("$[0].id",
						Matchers.not(Matchers.equalTo(otherLoan.getId().intValue()))));
	}

	@Test
	void getAllUserLoans_shouldReturnPaidAndUnpaidNewestFirst() throws Exception {
		Loan paidLoan = loanRepository.save(Loan.builder().loanAssetId("USDC")
				.collateralAssetId("ETH").returnedAmount(new BigDecimal("100.0000000"))
				.amount(new BigDecimal("100.0000000")).collateralAmount(new BigDecimal("0.2000000"))
				.currentCollateral(new BigDecimal("0.2000000"))
				.interestRate(new BigDecimal("0.050000"))
				.accruedInterest(BigDecimal.ZERO).grantedAt(activeLoan.getGrantedAt().minusDays(2))
				.user(testUser).build());

		mockMvc.perform(
				get("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(2)))
				.andExpect(jsonPath("$[0].id", Matchers.equalTo(activeLoan.getId().intValue())))
				.andExpect(jsonPath("$[0].isPaid", Matchers.equalTo(false)))
				.andExpect(jsonPath("$[1].id", Matchers.equalTo(paidLoan.getId().intValue())))
				.andExpect(jsonPath("$[1].isPaid", Matchers.equalTo(true)));
	}

	@Test
	void getAllUserLoans_shouldReturnEmptyList_whenUserHasNoLoans() throws Exception {
		User otherUser = User.builder().firstName("Jane").lastName("Smith")
				.email("noloans@example.com").password(passwordEncoder.encode("password123"))
				.verified(true).build();
		otherUser = userRepository.save(otherUser);
		String otherJwt = jwtUtil.generateToken(otherUser);
		authTokenRepository.save(AuthToken.builder().token(otherJwt).user(otherUser).build());

		mockMvc.perform(
				get("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherJwt))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", Matchers.hasSize(0)));
	}

	@Test
	void getAllUserLoans_shouldReturnForbidden_whenNoTokenProvided() throws Exception {
		mockMvc.perform(get("/api/v1/loans")).andExpect(status().isForbidden());
	}

	@Test
	void getUserLoan_shouldReturnTheCurrentUsersLoan() throws Exception {
		mockMvc.perform(get("/api/v1/loans/{id}", activeLoan.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id", Matchers.equalTo(activeLoan.getId().intValue())))
				.andExpect(jsonPath("$.loanAssetId", Matchers.equalTo("USDC")))
				.andExpect(jsonPath("$.amount", Matchers.equalTo(500.0)))
				.andExpect(jsonPath("$.returnedAmount", Matchers.equalTo(0.0)))
				.andExpect(jsonPath("$.outstandingAmount", Matchers.equalTo(505.0)))
				.andExpect(jsonPath("$.collateralAssetId", Matchers.equalTo("ETH")))
				.andExpect(jsonPath("$.collateralAmount", Matchers.equalTo(0.5)))
				.andExpect(jsonPath("$.currentCollateral", Matchers.equalTo(0.5)))
				.andExpect(jsonPath("$.interestRate", Matchers.equalTo(0.05)))
				.andExpect(jsonPath("$.isPaid", Matchers.equalTo(false)))
				.andExpect(jsonPath("$.accruedInterest", Matchers.equalTo(5.0)))
				.andExpect(jsonPath("$.grantedAt", Matchers.notNullValue()))
				.andExpect(jsonPath("$.user").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void getUserLoan_shouldReturnPaidLoan() throws Exception {
		Loan paidLoan = loanRepository.save(Loan.builder().loanAssetId("USDC")
				.collateralAssetId("ETH").returnedAmount(new BigDecimal("100.0000000"))
				.amount(new BigDecimal("100.0000000")).collateralAmount(new BigDecimal("0.2000000"))
				.currentCollateral(new BigDecimal("0.2000000"))
				.interestRate(new BigDecimal("0.050000"))
				.accruedInterest(BigDecimal.ZERO).grantedAt(LocalDateTime.now()).user(testUser)
				.build());

		mockMvc.perform(get("/api/v1/loans/{id}", paidLoan.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", Matchers.equalTo(paidLoan.getId().intValue())))
				.andExpect(jsonPath("$.isPaid", Matchers.equalTo(true)));
	}

	@Test
	void getUserLoan_shouldReturnNotFound_whenLoanDoesNotExist() throws Exception {
		mockMvc.perform(get("/api/v1/loans/{id}", 99999L)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_LOAN_NOT_FOUND));
	}

	@Test
	void getUserLoan_shouldReturnNotFound_whenUserDoesNotOwnLoan() throws Exception {
		User otherUser = User.builder().firstName("Jane").lastName("Smith")
				.email("other-get-loan@example.com").password(passwordEncoder.encode("password123"))
				.verified(true).build();
		otherUser = userRepository.save(otherUser);
		String otherJwt = jwtUtil.generateToken(otherUser);
		authTokenRepository.save(AuthToken.builder().token(otherJwt).user(otherUser).build());

		mockMvc.perform(get("/api/v1/loans/{id}", activeLoan.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherJwt))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_LOAN_NOT_FOUND));
	}

	@Test
	void getUserLoan_shouldReturnForbidden_whenNoTokenProvided() throws Exception {
		mockMvc.perform(get("/api/v1/loans/{id}", activeLoan.getId())).andExpect(status().isForbidden());
	}

	@Test
	void handleLoanRequest_shouldReturnTheCreatedLoan() throws Exception {
		long loanCount = loanRepository.count();
		depositWalletRepository.save(DepositWallet.builder().address("0xcollateral").assetId("ETH_TEST5")
				.balance(new BigDecimal("10.0000000")).lockedBalance(BigDecimal.ZERO)
				.accruedInterest(BigDecimal.ZERO).openedAt(LocalDateTime.now()).user(testUser).build());
		depositWalletRepository.save(DepositWallet.builder().address("0xloanasset")
				.assetId("USDC_ETH_TEST5_AN74").balance(BigDecimal.ZERO).lockedBalance(BigDecimal.ZERO)
				.accruedInterest(BigDecimal.ZERO).openedAt(LocalDateTime.now()).user(testUser).build());

		when(conversionRateService.getConversionRate("ETH_TEST5", "USDC_ETH_TEST5_AN74"))
				.thenReturn(BigDecimal.TEN);
		when(assetCustodianService.getPoolSize("USDC_ETH_TEST5_AN74"))
				.thenReturn(new BigDecimal("1000000"));
		when(assetCustodianService.createTransaction(eq("ETH_TEST5"),
				argThat(amount -> amount != null && amount.compareTo(BigDecimal.ONE) == 0),
				eq(testUser.getId()), eq("0x0000000000000000000000000000000000000001")))
				.thenReturn("tx-1");

		CreateLoanRequest request = new CreateLoanRequest();
		request.setLoanAssetId("USDC_ETH_TEST5_AN74");
		request.setCollateralAssetId("ETH_TEST5");
		request.setCollateralAmount(BigDecimal.ONE);

		MvcResult result = mockMvc.perform(post("/api/v1/loans")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id", Matchers.notNullValue()))
				.andExpect(jsonPath("$.loanAssetId", Matchers.equalTo("USDC_ETH_TEST5_AN74")))
				.andExpect(jsonPath("$.amount", Matchers.equalTo(8.0)))
				.andExpect(jsonPath("$.returnedAmount", Matchers.equalTo(0)))
				.andExpect(jsonPath("$.outstandingAmount", Matchers.equalTo(8.0)))
				.andExpect(jsonPath("$.collateralAssetId", Matchers.equalTo("ETH_TEST5")))
				.andExpect(jsonPath("$.collateralAmount", Matchers.equalTo(1)))
				.andExpect(jsonPath("$.currentCollateral", Matchers.equalTo(1)))
				.andExpect(jsonPath("$.interestRate", Matchers.equalTo(0.05)))
				.andExpect(jsonPath("$.isPaid", Matchers.equalTo(false)))
				.andExpect(jsonPath("$.accruedInterest", Matchers.equalTo(0)))
				.andExpect(jsonPath("$.grantedAt", Matchers.notNullValue()))
				.andExpect(jsonPath("$.user").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		Long createdId = body.get("id").asLong();
		Loan saved = loanRepository.findById(createdId).orElseThrow();
		assertThat(loanRepository.count()).isEqualTo(loanCount + 1);
		assertThat(saved.getLoanAssetId()).isEqualTo("USDC_ETH_TEST5_AN74");
		assertThat(saved.getCollateralAssetId()).isEqualTo("ETH_TEST5");
		assertThat(saved.getAmount()).isEqualByComparingTo("8");
		assertThat(saved.getReturnedAmount()).isEqualByComparingTo("0");
		assertThat(saved.getCollateralAmount()).isEqualByComparingTo("1");
		assertThat(saved.getCurrentCollateral()).isEqualByComparingTo("1");
		assertThat(saved.getAccruedInterest()).isEqualByComparingTo("0");
		assertThat(saved.isPaid()).isFalse();
		assertThat(saved.getInterestRate()).isEqualByComparingTo("0.05");
		verify(assetCustodianService).createTransaction(eq("ETH_TEST5"),
				argThat(amount -> amount != null && amount.compareTo(BigDecimal.ONE) == 0),
				eq(testUser.getId()), eq("0x0000000000000000000000000000000000000001"));
	}

	@Test
	void handleLoanRequest_shouldReturnBadRequest_whenLoanAssetIsNotSupported() throws Exception {
		long loanCount = loanRepository.count();
		CreateLoanRequest request = new CreateLoanRequest();
		request.setLoanAssetId("NOT_A_REAL_ASSET");
		request.setCollateralAssetId("ETH_TEST5");
		request.setCollateralAmount(BigDecimal.ONE);

		mockMvc.perform(post("/api/v1/loans")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].error").value("ValidAssetId"))
				.andExpect(jsonPath("$[0].message").value("Asset not supported"));

		assertThat(loanRepository.count()).isEqualTo(loanCount);
	}

	@Test
	void handleLoanRequest_shouldReturnBadRequest_whenCollateralAssetIsNotSupported() throws Exception {
		long loanCount = loanRepository.count();
		CreateLoanRequest request = new CreateLoanRequest();
		request.setLoanAssetId("ETH_TEST5");
		request.setCollateralAssetId("NOT_A_REAL_ASSET");
		request.setCollateralAmount(BigDecimal.ONE);

		mockMvc.perform(post("/api/v1/loans")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].error").value("ValidAssetId"))
				.andExpect(jsonPath("$[0].message").value("Asset not supported"));

		assertThat(loanRepository.count()).isEqualTo(loanCount);
	}

	private Loan saveUnlockLoan() {
		depositWalletRepository.save(DepositWallet.builder().address("0xeth-test5")
				.assetId("ETH_TEST5").balance(new BigDecimal("2.0000000"))
				.lockedBalance(new BigDecimal("1.0000000")).accruedInterest(BigDecimal.ZERO)
				.openedAt(LocalDateTime.now()).user(testUser).build());
		return loanRepository.save(Loan.builder().loanAssetId("USDC_ETH_TEST5_AN74")
				.collateralAssetId("ETH_TEST5").returnedAmount(BigDecimal.ZERO)
				.amount(new BigDecimal("1200.0000000")).collateralAmount(new BigDecimal("1.0000000"))
				.currentCollateral(new BigDecimal("1.0000000")).interestRate(new BigDecimal("0.050000"))
				.accruedInterest(BigDecimal.ZERO).grantedAt(LocalDateTime.now()).user(testUser)
				.build());
	}

	private void stubEthToUsdcRate() {
		when(conversionRateService.getConversionRate("ETH_TEST5", "USDC_ETH_TEST5_AN74"))
				.thenReturn(new BigDecimal("3000"));
	}

	@Test
	void unlockCollateral_shouldReturnOk_whenAmountKeepsHealthyLtv() throws Exception {
		Loan loan = saveUnlockLoan();
		stubEthToUsdcRate();
		when(conversionRateService.withdraw(eq("ETH_TEST5"), any(), any())).thenReturn(true);
		UnlockCollateralRequest request = UnlockCollateralRequest.builder()
				.amount(new BigDecimal("0.5")).build();

		mockMvc.perform(post("/api/v1/loans/{id}/collateral", loan.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", Matchers.equalTo(loan.getId().intValue())))
				.andExpect(jsonPath("$.currentCollateral", Matchers.equalTo(0.5)))
				.andExpect(jsonPath("$.collateralAmount", Matchers.equalTo(1.0)))
				.andExpect(jsonPath("$.outstandingAmount", Matchers.equalTo(1200.0)))
				.andExpect(jsonPath("$.isPaid", Matchers.equalTo(false)))
				.andExpect(jsonPath("$.user").doesNotExist());

		Loan updated = loanRepository.findById(loan.getId()).orElseThrow();
		DepositWallet updatedCollateral = depositWalletRepository
				.findByUserIdAndAssetId(testUser.getId(), "ETH_TEST5").orElseThrow();
		assertThat(updated.getCurrentCollateral()).isEqualByComparingTo("0.5");
		assertThat(updated.getCollateralAmount()).isEqualByComparingTo("1");
		assertThat(updatedCollateral.getLockedBalance()).isEqualByComparingTo("0.5");
		assertThat(updatedCollateral.getBalance()).isEqualByComparingTo("2");
		verify(conversionRateService).withdraw(eq("ETH_TEST5"),
				argThat(amount -> amount != null && amount.compareTo(new BigDecimal("0.5")) == 0),
				eq("0x8eEAa5d944d1B193241d513F6D29Ac82AEBbAA2a"));
	}

	@Test
	void unlockCollateral_shouldReturnBadRequest_whenAmountExceedsHealthyLtv() throws Exception {
		Loan loan = saveUnlockLoan();
		stubEthToUsdcRate();
		UnlockCollateralRequest request = UnlockCollateralRequest.builder()
				.amount(new BigDecimal("0.5000001")).build();

		mockMvc.perform(post("/api/v1/loans/{id}/collateral", loan.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_UNHEALTHY_LTV));

		Loan unchanged = loanRepository.findById(loan.getId()).orElseThrow();
		DepositWallet unchangedCollateral = depositWalletRepository
				.findByUserIdAndAssetId(testUser.getId(), "ETH_TEST5").orElseThrow();
		assertThat(unchanged.getCurrentCollateral()).isEqualByComparingTo("1");
		assertThat(unchangedCollateral.getLockedBalance()).isEqualByComparingTo("1");
		verify(conversionRateService, never()).withdraw(any(), any(), any());
	}

	@Test
	void unlockCollateral_shouldReturnBadRequest_whenAmountIsNotPositive() throws Exception {
		Loan loan = saveUnlockLoan();
		UnlockCollateralRequest request = UnlockCollateralRequest.builder()
				.amount(BigDecimal.ZERO).build();

		mockMvc.perform(post("/api/v1/loans/{id}/collateral", loan.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unlockCollateral_shouldReturnNotFound_whenLoanDoesNotExist() throws Exception {
		UnlockCollateralRequest request = UnlockCollateralRequest.builder()
				.amount(new BigDecimal("0.1")).build();

		mockMvc.perform(post("/api/v1/loans/{id}/collateral", 99999L)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_LOAN_NOT_FOUND));
	}

	@Test
	void unlockCollateral_shouldReturnNotFound_whenUserDoesNotOwnLoan() throws Exception {
		Loan loan = saveUnlockLoan();
		User otherUser = User.builder().firstName("Jane").lastName("Smith")
				.email("unlock-other@example.com").password(passwordEncoder.encode("password123"))
				.verified(true).build();
		otherUser = userRepository.save(otherUser);
		String otherJwt = jwtUtil.generateToken(otherUser);
		authTokenRepository.save(AuthToken.builder().token(otherJwt).user(otherUser).build());

		UnlockCollateralRequest request = UnlockCollateralRequest.builder()
				.amount(new BigDecimal("0.1")).build();

		mockMvc.perform(post("/api/v1/loans/{id}/collateral", loan.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherJwt)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_LOAN_NOT_FOUND));
	}

	@Test
	void unlockCollateral_shouldReturnForbidden_whenNoTokenProvided() throws Exception {
		Loan loan = saveUnlockLoan();
		UnlockCollateralRequest request = UnlockCollateralRequest.builder()
				.amount(new BigDecimal("0.1")).build();

		mockMvc.perform(post("/api/v1/loans/{id}/collateral", loan.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isForbidden());
	}

}
