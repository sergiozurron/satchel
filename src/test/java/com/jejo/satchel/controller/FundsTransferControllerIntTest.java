package com.jejo.satchel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

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
import com.jejo.satchel.dto.WithdrawalRequest;
import com.jejo.satchel.model.AuthToken;
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

	private String jwtToken;

	@BeforeEach
	void setup() {
		Long random = System.currentTimeMillis() % 100000;
		User testUser = userRepository.save(User.builder()
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

}
