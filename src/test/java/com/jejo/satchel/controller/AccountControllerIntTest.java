package com.jejo.satchel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.fireblocks.sdk.model.VaultAccount;
import com.jejo.satchel.model.AuthToken;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.AuthTokenRepository;
import com.jejo.satchel.repository.CollateralAccountRepository;
import com.jejo.satchel.repository.DepositAccountRepository;
import com.jejo.satchel.repository.RepaymentAccountRepository;
import com.jejo.satchel.repository.UserRepository;
import com.jejo.satchel.service.AssetCustodianService;
import com.jejo.satchel.util.JwtUtil;

@SpringBootTest
@AutoConfigureMockMvc
public class AccountControllerIntTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private AuthTokenRepository authTokenRepository;
	@Autowired
	private JwtUtil jwtUtil;
	@Autowired
	private CollateralAccountRepository collateralAccountRepository;
	@Autowired
	private RepaymentAccountRepository repaymentAccountRepository;
	@Autowired
	private DepositAccountRepository depositAccountRepository;
	@Autowired
	private AssetCustodianService assetCustodianService;

	private User user;
	private String jwtToken;

	@BeforeEach
	void setup() {
		// Setup a test user and JWT token if needed
		Long random = System.currentTimeMillis() % 100000;
		user = userRepository.save(User.builder().firstName("John").lastName("Doe").email("email@email" + random)
				.password("pass").verified(true).build());
		jwtToken = jwtUtil.generateToken(user);
		authTokenRepository.save(AuthToken.builder().token(jwtToken).user(user).build());
	}
	
	@AfterEach
	void cleanup() {
		// Clean up repositories after each test
		collateralAccountRepository.deleteAll();
		repaymentAccountRepository.deleteAll();
		depositAccountRepository.deleteAll();
		authTokenRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void createAccount_shouldReturnOkAndCreateAccounts() throws Exception {
		mockMvc.perform(post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isOk());

		assertThat(collateralAccountRepository.findByUserId(user.getId())).isPresent();
		assertThat(repaymentAccountRepository.findByUserId(user.getId())).isPresent();
		assertThat(depositAccountRepository.findByUserId(user.getId())).isPresent();

		List<VaultAccount> vaultAccounts = assetCustodianService.findAllVaultAccounts(user.getEmail().toString());
		assertThat(vaultAccounts.size()).isEqualTo(3);
		assertThat(vaultAccounts.stream().map(VaultAccount::getName).toList()).containsExactlyInAnyOrder(
				assetCustodianService.collateralPrefix + user.getEmail(),
				assetCustodianService.repaymentPrefix + user.getEmail(),
				assetCustodianService.depositPrefix + user.getEmail());
	}
	
	@Test
	void createAccount_shouldReturnBadRequest_WhenAccountsAlreadyCreated() throws Exception {
		mockMvc.perform(post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isBadRequest());
	}

}
