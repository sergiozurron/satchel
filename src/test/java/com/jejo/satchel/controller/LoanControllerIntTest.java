package com.jejo.satchel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.jejo.satchel.util.JwtUtil;

@SpringBootTest
@AutoConfigureMockMvc
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

	private User testUser;
	private Loan activeLoan;
	private DepositWallet depositWallet;
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
				.password(passwordEncoder.encode("password123")).verified(true)
				.vaultAccountId("123").build();
		testUser = userRepository.save(testUser);
		jwtToken = jwtUtil.generateToken(testUser);
		authTokenRepository.save(AuthToken.builder().token(jwtToken).user(testUser).build());

		// Create deposit wallet with sufficient balance
		depositWallet = DepositWallet.builder().address("0x1234567890abcdef").assetId("USDC")
				.balance(new BigDecimal("1000.0000000")).lockedBalance(BigDecimal.ZERO)
				.accruedInterest(BigDecimal.ZERO).openedAt(LocalDateTime.now()).user(testUser)
				.build();
		depositWallet = depositWalletRepository.save(depositWallet);

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
				.loanId(activeLoan.getId()).amount(repaymentAmount).build();

		// When & Then
		mockMvc.perform(put("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isOk());
	}

	@Test
	void repayLoan_shouldReturnOk_whenFullRepaymentSuccessful() throws Exception {
		// Given
		BigDecimal repaymentAmount = new BigDecimal("500.0000000");
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId()).amount(repaymentAmount).build();

		// When & Then
		mockMvc.perform(put("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isOk());
	}

	@Test
	void repayLoan_shouldReturnBadRequest_whenRepaymentAmountIsZero() throws Exception {
		// Given
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId()).amount(BigDecimal.ZERO).build();

		// When & Then
		mockMvc.perform(put("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void repayLoan_shouldReturnBadRequest_whenRepaymentAmountIsNegative() throws Exception {
		// Given
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId()).amount(new BigDecimal("-100.0000000")).build();

		// When & Then
		mockMvc.perform(put("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void repayLoan_shouldReturnNotFound_whenLoanDoesNotExist() throws Exception {
		// Given
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder().loanId(99999L)
				.amount(new BigDecimal("100.0000000")).build();

		// When & Then
		mockMvc.perform(put("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
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
				.loanId(paidLoan.getId()).amount(new BigDecimal("100.0000000")).build();

		// When & Then
		mockMvc.perform(put("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void repayLoan_shouldReturnBadRequest_whenInsufficientBalance() throws Exception {
		// Given - Update deposit wallet with insufficient balance
		depositWallet.setBalance(new BigDecimal("10.0000000"));
		depositWalletRepository.save(depositWallet);

		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId()).amount(new BigDecimal("100.0000000")).build();

		// When & Then
		mockMvc.perform(put("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void repayLoan_shouldDebitWalletBalance_onSuccessfulRepayment() throws Exception {
		// Given
		BigDecimal initialBalance = depositWallet.getBalance();
		BigDecimal repaymentAmount = new BigDecimal("100.0000000");
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId()).amount(repaymentAmount).build();

		// When
		mockMvc.perform(put("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isOk());

		// Then - Verify wallet balance was debited
		DepositWallet updatedWallet = depositWalletRepository.findById(depositWallet.getId())
				.orElseThrow();
		BigDecimal expectedBalance = initialBalance.subtract(repaymentAmount);
		org.assertj.core.api.Assertions.assertThat(updatedWallet.getBalance())
				.isEqualTo(expectedBalance);
	}

	@Test
	void repayLoan_shouldReturnNotFound_whenUserDoesNotOwnLoan() throws Exception {
		// Given - Create another user
		User otherUser = User.builder().firstName("Jane").lastName("Smith")
				.email("other@example.com").password(passwordEncoder.encode("password123"))
				.verified(true).vaultAccountId("456").build();
		otherUser = userRepository.save(otherUser);
		String otherJwt = jwtUtil.generateToken(otherUser);
		authTokenRepository.save(AuthToken.builder().token(otherJwt).user(otherUser).build());

		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId()).amount(new BigDecimal("100.0000000")).build();

		// When & Then
		mockMvc.perform(put("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherJwt)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isNotFound());
	}

	@Test
	void repayLoan_shouldReturnBadRequest_whenLoanIdIsNull() throws Exception {
		// Given
		String requestBody = "{\"amount\": 100.0000000}";

		// When & Then
		mockMvc.perform(put("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isBadRequest());
	}

	@Test
	void repayLoan_shouldReturnBadRequest_whenAmountIsNull() throws Exception {
		// Given
		String requestBody = "{\"loanId\": " + activeLoan.getId() + "}";

		// When & Then
		mockMvc.perform(put("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON).content(requestBody))
				.andExpect(status().isBadRequest());
	}

	@Test
	void repayLoan_shouldUpdateLoanReturnedAmount_onSuccessfulRepayment() throws Exception {
		// Given
		BigDecimal repaymentAmount = new BigDecimal("150.0000000");
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId()).amount(repaymentAmount).build();

		// When
		mockMvc.perform(put("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isOk());

		// Then - Verify loan's returned amount was updated
		Loan updatedLoan = loanRepository.findById(activeLoan.getId()).orElseThrow();
		assertThat(updatedLoan.getReturnedAmount()).isEqualTo(repaymentAmount);
	}

	@Test
	void repayLoan_shouldAllowMultiplePartialRepayments() throws Exception {
		// Given - First repayment
		BigDecimal firstRepayment = new BigDecimal("100.0000000");
		LoanRepaymentRequest firstRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId()).amount(firstRepayment).build();

		// When - Perform first repayment
		mockMvc.perform(put("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(firstRequest))).andExpect(status().isOk());

		// Given - Second repayment
		BigDecimal secondRepayment = new BigDecimal("200.0000000");
		LoanRepaymentRequest secondRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId()).amount(secondRepayment).build();

		// Then - Perform second repayment
		mockMvc.perform(put("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(secondRequest)))
				.andExpect(status().isOk());
	}

	@Test
	void getAllUserLoans_shouldReturnTheCurrentUsersLoan() throws Exception {
		mockMvc.perform(get("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
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
				.verified(true).vaultAccountId("456").build();
		otherUser = userRepository.save(otherUser);
		String otherJwt = jwtUtil.generateToken(otherUser);
		authTokenRepository.save(AuthToken.builder().token(otherJwt).user(otherUser).build());

		Loan otherLoan = loanRepository.save(Loan.builder().loanAssetId("USDC")
				.collateralAssetId("ETH").returnedAmount(BigDecimal.ZERO)
				.amount(new BigDecimal("20.0000000")).collateralAmount(new BigDecimal("0.1000000"))
				.interestRate(new BigDecimal("0.050000")).status(LoanStatus.ACTIVE)
				.accruedInterest(BigDecimal.ZERO).grantedAt(LocalDateTime.now()).user(otherUser)
				.build());

		mockMvc.perform(get("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].id", Matchers.equalTo(activeLoan.getId().intValue())))
				.andExpect(jsonPath("$[0].id",
						Matchers.not(Matchers.equalTo(otherLoan.getId().intValue()))));
	}

	@Test
	void getAllUserLoans_shouldReturnEveryStatusNewestFirst() throws Exception {
		Loan paidLoan = loanRepository.save(Loan.builder().loanAssetId("USDC")
				.collateralAssetId("ETH").returnedAmount(new BigDecimal("100.0000000"))
				.amount(new BigDecimal("100.0000000"))
				.collateralAmount(new BigDecimal("0.2000000"))
				.interestRate(new BigDecimal("0.050000")).status(LoanStatus.PAID)
				.accruedInterest(BigDecimal.ZERO)
				.grantedAt(activeLoan.getGrantedAt().minusDays(2)).user(testUser).build());

		mockMvc.perform(get("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
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
				.verified(true).vaultAccountId("789").build();
		otherUser = userRepository.save(otherUser);
		String otherJwt = jwtUtil.generateToken(otherUser);
		authTokenRepository.save(AuthToken.builder().token(otherJwt).user(otherUser).build());

		mockMvc.perform(get("/api/v1/loans").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherJwt))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", Matchers.hasSize(0)));
	}

	@Test
	void getAllUserLoans_shouldReturnForbidden_whenNoTokenProvided() throws Exception {
		mockMvc.perform(get("/api/v1/loans")).andExpect(status().isForbidden());
	}

}
