package com.jejo.satchel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.fireblocks.sdk.model.VaultAccount;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.AuthToken;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.AuthTokenRepository;
import com.jejo.satchel.repository.FundsTransferRepository;
import com.jejo.satchel.repository.UserRepository;
import com.jejo.satchel.service.AssetCustodianService;
import com.jejo.satchel.util.JwtUtil;

@SpringBootTest
@AutoConfigureMockMvc
public class AccountControllerIntTest {

	@Value("${name.deposit.prefix}")
	private String depositPrefix;
	@Value("${name.collateral.prefix}")
	private String collateralPrefix;
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private AuthTokenRepository authTokenRepository;
	@Autowired
	private JwtUtil jwtUtil;
	@Autowired
	private AssetCustodianService assetCustodianService;
	@Autowired
	private DepositWalletRepository accountRepository;
	@Autowired
	private FundsTransferRepository fundsTransferRepository;

	private User user;
	private String jwtToken;

	@BeforeEach
	void setup() {
		// Setup a test user and JWT token if needed
		Long random = System.currentTimeMillis() % 100000;
		user = userRepository.save(User.builder().firstName("John").lastName("Doe")
				.email("email@email" + random).password("pass").verified(true).build());
		jwtToken = jwtUtil.generateToken(user);
		authTokenRepository.save(AuthToken.builder().token(jwtToken).user(user).build());
	}

	@AfterEach
	void cleanup() {
		// Clean up repositories after each test
		fundsTransferRepository.deleteAll();
		accountRepository.deleteAll();
		authTokenRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void createAccount_shouldReturnOkAndCreateAccounts() throws Exception {
		mockMvc.perform(
				post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isOk());

		List<VaultAccount> vaultAccounts = assetCustodianService
				.findAllVaultAccountsBySuffix(user.getEmail());
		assertThat(vaultAccounts.size()).isEqualTo(2);
		assertThat(vaultAccounts.stream().map(VaultAccount::getName).toList())
				.containsExactlyInAnyOrder(collateralPrefix + user.getEmail(),
						depositPrefix + user.getEmail());
		List<DepositWallet> accounts = accountRepository.findAllByUserId(user.getId());
		assertThat(accounts.size()).isEqualTo(2);
	}

	@Test
	void createAccount_shouldReturnBadRequest_WhenAccountsAlreadyCreated() throws Exception {
		mockMvc.perform(
				post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isOk());
		mockMvc.perform(
				post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isBadRequest());
	}

}
