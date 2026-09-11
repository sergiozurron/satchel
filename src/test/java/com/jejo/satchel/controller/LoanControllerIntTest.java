package com.jejo.satchel.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jejo.satchel.dto.LoanRepaymentRequest;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.Loan;
import com.jejo.satchel.model.LoanStatus;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.LoanRepository;
import com.jejo.satchel.repository.UserRepository;

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

	private User testUser;
	private Loan activeLoan;
	private DepositWallet depositWallet;

	@BeforeEach
	void setup() {
		// Clear repositories before each test
		loanRepository.deleteAll();
		depositWalletRepository.deleteAll();
		userRepository.deleteAll();

		// Create test user
		testUser = User.builder()
				.firstName("John")
				.lastName("Doe")
				.email("john@example.com")
				.password(passwordEncoder.encode("password123"))
				.verified(true)
				.vaultAccountId(123L)
				.build();
		testUser = userRepository.save(testUser);

		// Create deposit wallet with sufficient balance
		depositWallet = DepositWallet.builder()
				.address("0x1234567890abcdef")
				.assetId("USDC")
				.balance(new BigDecimal("1000.0000000"))
				.lockedBalance(BigDecimal.ZERO)
				.openedAt(LocalDateTime.now())
				.user(testUser)
				.build();
		depositWallet = depositWalletRepository.save(depositWallet);

		// Create active loan
		activeLoan = Loan.builder()
				.loanAssetId("USDC")
				.collateralAssetId("ETH")
				.returnedAmount(BigDecimal.ZERO)
				.amount(new BigDecimal("500.0000000"))
				.collateralAmount(new BigDecimal("0.5000000"))
				.ltv(new BigDecimal("0.7500"))
				.interestRate(new BigDecimal("0.050000"))
				.status(LoanStatus.ACTIVE)
				.accruedInterest(new BigDecimal("5.0000000"))
				.grantedAt(LocalDateTime.now())
				.user(testUser)
				.build();
		activeLoan = loanRepository.save(activeLoan);
	}

	@Test
	@WithMockUser(username = "john@example.com")
	void repayLoan_shouldReturnOk_whenPartialRepaymentSuccessful() throws Exception {
		// Given
		BigDecimal repaymentAmount = new BigDecimal("100.0000000");
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId())
				.amount(repaymentAmount)
				.build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/repayment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loanId").value(activeLoan.getId()))
				.andExpect(jsonPath("$.repaymentAmount").value(100.0))
				.andExpect(jsonPath("$.outstandingAmount").value(400.0))
				.andExpect(jsonPath("$.totalRepaidAmount").value(100.0))
				.andExpect(jsonPath("$.loanStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.message").value("Partial repayment successful"));
	}

	@Test
	@WithMockUser(username = "john@example.com")
	void repayLoan_shouldReturnOk_whenFullRepaymentSuccessful() throws Exception {
		// Given
		BigDecimal repaymentAmount = new BigDecimal("500.0000000");
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId())
				.amount(repaymentAmount)
				.build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/repayment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loanId").value(activeLoan.getId()))
				.andExpect(jsonPath("$.repaymentAmount").value(500.0))
				.andExpect(jsonPath("$.outstandingAmount").value(0.0))
				.andExpect(jsonPath("$.totalRepaidAmount").value(500.0))
				.andExpect(jsonPath("$.loanStatus").value("PAID"))
				.andExpect(jsonPath("$.message").value("Loan repaid successfully"));
	}

	@Test
	@WithMockUser(username = "john@example.com")
	void repayLoan_shouldReturnBadRequest_whenRepaymentAmountIsZero() throws Exception {
		// Given
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId())
				.amount(BigDecimal.ZERO)
				.build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/repayment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(username = "john@example.com")
	void repayLoan_shouldReturnBadRequest_whenRepaymentAmountIsNegative() throws Exception {
		// Given
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId())
				.amount(new BigDecimal("-100.0000000"))
				.build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/repayment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(username = "john@example.com")
	void repayLoan_shouldReturnBadRequest_whenRepaymentAmountExceedsOutstanding() throws Exception {
		// Given
		BigDecimal excessiveAmount = new BigDecimal("1000.0000000");
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId())
				.amount(excessiveAmount)
				.build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/repayment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(username = "john@example.com")
	void repayLoan_shouldReturnNotFound_whenLoanDoesNotExist() throws Exception {
		// Given
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(99999L)
				.amount(new BigDecimal("100.0000000"))
				.build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/repayment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "john@example.com")
	void repayLoan_shouldReturnBadRequest_whenLoanIsNotActive() throws Exception {
		// Given - Create and save a paid loan
		Loan paidLoan = Loan.builder()
				.loanAssetId("USDC")
				.collateralAssetId("ETH")
				.returnedAmount(new BigDecimal("500.0000000"))
				.amount(new BigDecimal("500.0000000"))
				.collateralAmount(new BigDecimal("0.5000000"))
				.ltv(new BigDecimal("0.7500"))
				.interestRate(new BigDecimal("0.050000"))
				.status(LoanStatus.PAID)
				.accruedInterest(new BigDecimal("5.0000000"))
				.grantedAt(LocalDateTime.now())
				.user(testUser)
				.build();
		paidLoan = loanRepository.save(paidLoan);

		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(paidLoan.getId())
				.amount(new BigDecimal("100.0000000"))
				.build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/repayment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(username = "john@example.com")
	void repayLoan_shouldReturnBadRequest_whenInsufficientBalance() throws Exception {
		// Given - Update deposit wallet with insufficient balance
		depositWallet.setBalance(new BigDecimal("10.0000000"));
		depositWalletRepository.save(depositWallet);

		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId())
				.amount(new BigDecimal("100.0000000"))
				.build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/repayment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(username = "john@example.com")
	void repayLoan_shouldDebitWalletBalance_onSuccessfulRepayment() throws Exception {
		// Given
		BigDecimal initialBalance = depositWallet.getBalance();
		BigDecimal repaymentAmount = new BigDecimal("100.0000000");
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId())
				.amount(repaymentAmount)
				.build();

		// When
		mockMvc.perform(post("/api/v1/loans/repayment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isOk());

		// Then - Verify wallet balance was debited
		DepositWallet updatedWallet = depositWalletRepository.findById(depositWallet.getId()).orElseThrow();
		BigDecimal expectedBalance = initialBalance.subtract(repaymentAmount);
		org.assertj.core.api.Assertions.assertThat(updatedWallet.getBalance())
				.isEqualTo(expectedBalance);
	}

	@Test
	@WithMockUser(username = "other@example.com")
	void repayLoan_shouldReturnNotFound_whenUserDoesNotOwnLoan() throws Exception {
		// Given - Create another user
		User otherUser = User.builder()
				.firstName("Jane")
				.lastName("Smith")
				.email("other@example.com")
				.password(passwordEncoder.encode("password123"))
				.verified(true)
				.vaultAccountId(456L)
				.build();
		userRepository.save(otherUser);

		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId())
				.amount(new BigDecimal("100.0000000"))
				.build();

		// When & Then
		mockMvc.perform(post("/api/v1/loans/repayment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "john@example.com")
	void repayLoan_shouldReturnBadRequest_whenLoanIdIsNull() throws Exception {
		// Given
		String requestBody = "{\"amount\": 100.0000000}";

		// When & Then
		mockMvc.perform(post("/api/v1/loans/repayment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(username = "john@example.com")
	void repayLoan_shouldReturnBadRequest_whenAmountIsNull() throws Exception {
		// Given
		String requestBody = "{\"loanId\": " + activeLoan.getId() + "}";

		// When & Then
		mockMvc.perform(post("/api/v1/loans/repayment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(username = "john@example.com")
	void repayLoan_shouldUpdateLoanReturnedAmount_onSuccessfulRepayment() throws Exception {
		// Given
		BigDecimal repaymentAmount = new BigDecimal("150.0000000");
		LoanRepaymentRequest repaymentRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId())
				.amount(repaymentAmount)
				.build();

		// When
		mockMvc.perform(post("/api/v1/loans/repayment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(repaymentRequest)))
				.andExpect(status().isOk());

		// Then - Verify loan's returned amount was updated
		Loan updatedLoan = loanRepository.findById(activeLoan.getId()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(updatedLoan.getReturnedAmount())
				.isEqualTo(repaymentAmount);
	}

	@Test
	@WithMockUser(username = "john@example.com")
	void repayLoan_shouldAllowMultiplePartialRepayments() throws Exception {
		// Given - First repayment
		BigDecimal firstRepayment = new BigDecimal("100.0000000");
		LoanRepaymentRequest firstRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId())
				.amount(firstRepayment)
				.build();

		// When - Perform first repayment
		mockMvc.perform(post("/api/v1/loans/repayment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(firstRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalRepaidAmount").value(100.0))
				.andExpect(jsonPath("$.outstandingAmount").value(400.0));

		// Given - Second repayment
		BigDecimal secondRepayment = new BigDecimal("200.0000000");
		LoanRepaymentRequest secondRequest = LoanRepaymentRequest.builder()
				.loanId(activeLoan.getId())
				.amount(secondRepayment)
				.build();

		// Then - Perform second repayment
		mockMvc.perform(post("/api/v1/loans/repayment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(secondRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalRepaidAmount").value(300.0))
				.andExpect(jsonPath("$.outstandingAmount").value(200.0))
				.andExpect(jsonPath("$.loanStatus").value("ACTIVE"));
	}

}
