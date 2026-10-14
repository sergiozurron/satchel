package com.jejo.satchel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.jejo.satchel.model.AuthToken;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.Loan;
import com.jejo.satchel.model.LoanStatus;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.AuthTokenRepository;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.LoanRepository;
import com.jejo.satchel.repository.UserRepository;
import com.jejo.satchel.service.AssetCustodianService;
import com.jejo.satchel.service.ConversionRateService;
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
	private ConversionRateService conversionRateService;

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

		// Collateral locked at origination; repayment unlocks this balance
		collateralWallet = DepositWallet.builder().address("0xethcollateral").assetId("ETH")
				.balance(new BigDecimal("2.0000000")).lockedBalance(new BigDecimal("0.5000000"))
				.accruedInterest(BigDecimal.ZERO).openedAt(LocalDateTime.now()).user(testUser)
				.build();
		collateralWallet = depositWalletRepository.save(collateralWallet);

		// Create active loan
		activeLoan = Loan.builder().loanAssetId("USDC").collateralAssetId("ETH")
				.returnedAmount(BigDecimal.ZERO).amount(new BigDecimal("500.0000000"))
				.collateralAmount(new BigDecimal("0.5000000"))
				.interestRate(new BigDecimal("0.050000")).status(LoanStatus.ACTIVE)
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
		mockMvc.perform(patch("/api/v1/loans/{id}", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
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
				.andExpect(jsonPath("$.status", Matchers.equalTo("ACTIVE")))
				.andExpect(jsonPath("$.accruedInterest", Matchers.equalTo(5.0)))
				.andExpect(jsonPath("$.grantedAt", Matchers.notNullValue()))
				.andExpect(jsonPath("$.user").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist());

		DepositWallet updatedLoanWallet = depositWalletRepository.findById(depositWallet.getId())
				.orElseThrow();
		DepositWallet updatedCollateral = depositWalletRepository.findById(collateralWallet.getId())
				.orElseThrow();
		assertThat(updatedLoanWallet.getBalance()).isEqualByComparingTo("900");
		assertThat(updatedCollateral.getLockedBalance()).isEqualByComparingTo("0");
		assertThat(updatedCollateral.getBalance()).isEqualByComparingTo("2");
	}

	@Test
	void repayLoan_shouldReturnOk_whenFullRepaymentSuccessful() throws Exception {
		// Given
		BigDecimal repaymentAmount = new BigDecimal("505.0000000");
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(repaymentAmount).build();

		// When & Then
		mockMvc.perform(patch("/api/v1/loans/{id}", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.returnedAmount", Matchers.equalTo(505.0)))
				.andExpect(jsonPath("$.outstandingAmount", Matchers.equalTo(0.0)))
				.andExpect(jsonPath("$.status", Matchers.equalTo("PAID")));

		DepositWallet updatedLoanWallet = depositWalletRepository.findById(depositWallet.getId())
				.orElseThrow();
		DepositWallet updatedCollateral = depositWalletRepository.findById(collateralWallet.getId())
				.orElseThrow();
		Loan updatedLoan = loanRepository.findById(activeLoan.getId()).orElseThrow();
		assertThat(updatedLoanWallet.getBalance()).isEqualByComparingTo("495");
		assertThat(updatedCollateral.getLockedBalance()).isEqualByComparingTo("0");
		assertThat(updatedLoan.getStatus()).isEqualTo(LoanStatus.PAID);
		assertThat(updatedLoan.getReturnedAmount()).isEqualByComparingTo("505");
	}

	@Test
	void repayLoan_shouldReturnBadRequest_whenRepaymentAmountIsZero() throws Exception {
		// Given
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(BigDecimal.ZERO).build();

		// When & Then
		mockMvc.perform(patch("/api/v1/loans/{id}", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
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
		mockMvc.perform(patch("/api/v1/loans/{id}", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
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
		mockMvc.perform(patch("/api/v1/loans/{id}", 99999L).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isNotFound());
	}

	@Test
	void repayLoan_shouldReturnBadRequest_whenLoanIsNotActive() throws Exception {
		// Given - Create and save a paid loan
		Loan paidLoan = Loan.builder().loanAssetId("USDC").collateralAssetId("ETH")
				.returnedAmount(new BigDecimal("500.0000000")).amount(new BigDecimal("500.0000000"))
				.collateralAmount(new BigDecimal("0.5000000"))
				.interestRate(new BigDecimal("0.050000")).status(LoanStatus.PAID)
				.accruedInterest(new BigDecimal("5.0000000")).grantedAt(LocalDateTime.now())
				.user(testUser).build();
		paidLoan = loanRepository.save(paidLoan);

		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(new BigDecimal("100.0000000")).build();

		// When & Then
		mockMvc.perform(patch("/api/v1/loans/{id}", paidLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
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
		mockMvc.perform(patch("/api/v1/loans/{id}", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
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
		mockMvc.perform(patch("/api/v1/loans/{id}", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
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
		assertThat(updatedCollateral.getLockedBalance()).isEqualByComparingTo("0");
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
		mockMvc.perform(patch("/api/v1/loans/{id}", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherJwt)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isNotFound());
	}

	@Test
	void repayLoan_shouldReturnBadRequest_whenAmountIsNull() throws Exception {
		// Given
		String requestBody = "{}";

		// When & Then
		mockMvc.perform(patch("/api/v1/loans/{id}", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
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
		mockMvc.perform(patch("/api/v1/loans/{id}", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isOk());

		// Then - Verify loan's returned amount was updated and collateral was unlocked
		Loan updatedLoan = loanRepository.findById(activeLoan.getId()).orElseThrow();
		assertThat(updatedLoan.getReturnedAmount()).isEqualTo(repaymentAmount);
		assertThat(updatedLoan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
		DepositWallet updatedLoanWallet = depositWalletRepository.findById(depositWallet.getId())
				.orElseThrow();
		DepositWallet updatedCollateral = depositWalletRepository.findById(collateralWallet.getId())
				.orElseThrow();
		assertThat(updatedLoanWallet.getBalance()).isEqualByComparingTo("850");
		assertThat(updatedCollateral.getLockedBalance()).isEqualByComparingTo("0");
	}

	@Test
	void repayLoan_shouldAllowMultiplePartialRepayments() throws Exception {
		// Given - First repayment
		BigDecimal firstRepayment = new BigDecimal("100.0000000");
		LoanRepaymentRequest firstRequest = LoanRepaymentRequest.builder()
				.amount(firstRepayment).build();

		// When - Perform first repayment
		mockMvc.perform(patch("/api/v1/loans/{id}", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(firstRequest))).andExpect(status().isOk());

		// Given - Second repayment
		BigDecimal secondRepayment = new BigDecimal("200.0000000");
		LoanRepaymentRequest secondRequest = LoanRepaymentRequest.builder()
				.amount(secondRepayment).build();

		// Then - Perform second repayment
		mockMvc.perform(patch("/api/v1/loans/{id}", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(secondRequest))).andExpect(status().isOk())
				.andExpect(jsonPath("$.returnedAmount", Matchers.equalTo(300.0)))
				.andExpect(jsonPath("$.outstandingAmount", Matchers.equalTo(205.0)))
				.andExpect(jsonPath("$.status", Matchers.equalTo("ACTIVE")));

		DepositWallet updatedLoanWallet = depositWalletRepository.findById(depositWallet.getId())
				.orElseThrow();
		DepositWallet updatedCollateral = depositWalletRepository.findById(collateralWallet.getId())
				.orElseThrow();
		assertThat(updatedLoanWallet.getBalance()).isEqualByComparingTo("700");
		// Each repayment unlocks the full collateral amount
		assertThat(updatedCollateral.getLockedBalance()).isEqualByComparingTo("-0.5");
	}

	@Test
	void repayLoan_shouldReturnInternalServerError_whenLoanWalletMissing() throws Exception {
		depositWalletRepository.delete(depositWallet);

		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(new BigDecimal("100.0000000")).build();

		mockMvc.perform(patch("/api/v1/loans/{id}", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_INTERNAL_SERVER_ERROR));

		Loan unchangedLoan = loanRepository.findById(activeLoan.getId()).orElseThrow();
		DepositWallet unchangedCollateral = depositWalletRepository.findById(collateralWallet.getId())
				.orElseThrow();
		assertThat(unchangedLoan.getReturnedAmount()).isEqualByComparingTo("0");
		assertThat(unchangedLoan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
		assertThat(unchangedCollateral.getLockedBalance()).isEqualByComparingTo("0.5");
	}

	@Test
	void repayLoan_shouldReturnInternalServerError_whenCollateralWalletMissing() throws Exception {
		depositWalletRepository.delete(collateralWallet);

		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.amount(new BigDecimal("100.0000000")).build();

		mockMvc.perform(patch("/api/v1/loans/{id}", activeLoan.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value(GlobalExceptionHandler.ERROR_INTERNAL_SERVER_ERROR));

		Loan unchangedLoan = loanRepository.findById(activeLoan.getId()).orElseThrow();
		DepositWallet unchangedLoanWallet = depositWalletRepository.findById(depositWallet.getId())
				.orElseThrow();
		assertThat(unchangedLoan.getReturnedAmount()).isEqualByComparingTo("0");
		assertThat(unchangedLoan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
		assertThat(unchangedLoanWallet.getBalance()).isEqualByComparingTo("1000");
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
				.andExpect(jsonPath("$[0].interestRate", Matchers.equalTo(0.05)))
				.andExpect(jsonPath("$[0].status", Matchers.equalTo("ACTIVE")))
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
				.interestRate(new BigDecimal("0.050000")).status(LoanStatus.ACTIVE)
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
	void getAllUserLoans_shouldReturnEveryStatusNewestFirst() throws Exception {
		Loan paidLoan = loanRepository.save(Loan.builder().loanAssetId("USDC")
				.collateralAssetId("ETH").returnedAmount(new BigDecimal("100.0000000"))
				.amount(new BigDecimal("100.0000000")).collateralAmount(new BigDecimal("0.2000000"))
				.interestRate(new BigDecimal("0.050000")).status(LoanStatus.PAID)
				.accruedInterest(BigDecimal.ZERO).grantedAt(activeLoan.getGrantedAt().minusDays(2))
				.user(testUser).build());

		mockMvc.perform(
				get("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(2)))
				.andExpect(jsonPath("$[0].id", Matchers.equalTo(activeLoan.getId().intValue())))
				.andExpect(jsonPath("$[0].status", Matchers.equalTo("ACTIVE")))
				.andExpect(jsonPath("$[1].id", Matchers.equalTo(paidLoan.getId().intValue())))
				.andExpect(jsonPath("$[1].status", Matchers.equalTo("PAID")));
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
				.andExpect(jsonPath("$.interestRate", Matchers.equalTo(0.05)))
				.andExpect(jsonPath("$.status", Matchers.equalTo("ACTIVE")))
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
				.interestRate(new BigDecimal("0.050000")).status(LoanStatus.PAID)
				.accruedInterest(BigDecimal.ZERO).grantedAt(LocalDateTime.now()).user(testUser)
				.build());

		mockMvc.perform(get("/api/v1/loans/{id}", paidLoan.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", Matchers.equalTo(paidLoan.getId().intValue())))
				.andExpect(jsonPath("$.status", Matchers.equalTo("PAID")));
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
				.andExpect(jsonPath("$.interestRate", Matchers.equalTo(0.05)))
				.andExpect(jsonPath("$.status", Matchers.equalTo("ACTIVE")))
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
		assertThat(saved.getAccruedInterest()).isEqualByComparingTo("0");
		assertThat(saved.getStatus()).isEqualTo(LoanStatus.ACTIVE);
		assertThat(saved.getInterestRate()).isEqualByComparingTo("0.05");
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

}
