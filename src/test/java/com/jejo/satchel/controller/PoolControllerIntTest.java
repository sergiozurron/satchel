package com.jejo.satchel.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jejo.satchel.model.AuthToken;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.AuthTokenRepository;
import com.jejo.satchel.repository.UserRepository;
import com.jejo.satchel.service.AssetCustodianService;
import com.jejo.satchel.util.JwtUtil;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PoolControllerIntTest {

	private static final String ASSET_ID = "ETH_TEST5";
	private static final String HYPHENATED_ASSET_ID = "ETH-AETH_SEPOLIA";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AuthTokenRepository authTokenRepository;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private AssetCustodianService assetCustodianService;

	private String jwtToken;
	private User testUser;
	private AuthToken authToken;

	@BeforeEach
	void setup() {
		Long random = System.currentTimeMillis() % 100000;
		testUser = userRepository.save(User.builder()
				.firstName("John")
				.lastName("Doe")
				.email("pool.test" + random + "@example.com")
				.password("password123")
				.verified(true)
				.build());

		jwtToken = jwtUtil.generateToken(testUser);
		authToken = authTokenRepository.save(AuthToken.builder()
				.token(jwtToken)
				.user(testUser)
				.build());
	}

	@AfterEach
	void cleanup() {
		authTokenRepository.delete(authToken);
		userRepository.delete(testUser);
	}

	@Test
	void getPoolLiquidity_shouldReturnAvailableBalance_whenAssetIsSupported() throws Exception {
		when(assetCustodianService.getPoolSize(ASSET_ID)).thenReturn(new BigDecimal("10.5"));

		mockMvc.perform(get("/api/v1/pool/{assetId}", ASSET_ID)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.assetId").value(ASSET_ID))
				.andExpect(jsonPath("$.availableLiquidity").value(10.5));

		verify(assetCustodianService).getPoolSize(ASSET_ID);
	}

	@Test
	void getPoolLiquidity_shouldReturnAvailableBalance_whenAssetIdContainsHyphen() throws Exception {
		when(assetCustodianService.getPoolSize(HYPHENATED_ASSET_ID)).thenReturn(new BigDecimal("2"));

		mockMvc.perform(get("/api/v1/pool/{assetId}", HYPHENATED_ASSET_ID)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assetId").value(HYPHENATED_ASSET_ID))
				.andExpect(jsonPath("$.availableLiquidity").value(2));

		verify(assetCustodianService).getPoolSize(HYPHENATED_ASSET_ID);
	}

	@Test
	void getPoolLiquidity_shouldReturnZero_whenPoolIsEmpty() throws Exception {
		when(assetCustodianService.getPoolSize(ASSET_ID)).thenReturn(BigDecimal.ZERO);

		mockMvc.perform(get("/api/v1/pool/{assetId}", ASSET_ID)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assetId").value(ASSET_ID))
				.andExpect(jsonPath("$.availableLiquidity").value(0));

		verify(assetCustodianService).getPoolSize(ASSET_ID);
	}

	@Test
	void getPoolLiquidity_shouldReturnBadRequest_whenAssetIsNotSupported() throws Exception {
		mockMvc.perform(get("/api/v1/pool/{assetId}", "NOT_A_REAL_ASSET")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].error").value("ValidAssetId"))
				.andExpect(jsonPath("$[0].message").value("Asset not supported"));

		verify(assetCustodianService, never()).getPoolSize("NOT_A_REAL_ASSET");
	}

	@Test
	void getPoolLiquidity_shouldReturnForbidden_whenTokenIsMissing() throws Exception {
		mockMvc.perform(get("/api/v1/pool/{assetId}", ASSET_ID))
				.andExpect(status().isForbidden());

		verify(assetCustodianService, never()).getPoolSize(ASSET_ID);
	}

}
