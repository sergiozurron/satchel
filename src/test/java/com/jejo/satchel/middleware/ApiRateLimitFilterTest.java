package com.jejo.satchel.middleware;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jejo.satchel.service.AccountService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "satchel.api.rate-limit.capacity=2")
class ApiRateLimitFilterTest {

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private AccountService accountService;

	@Test
	void apiRequests_shouldRejectWhenCapacityIsSpent_andLeaveWebhookUncapped() throws Exception {
		mockMvc.perform(get("/api/v1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Welcome to the Satchel API."));
		mockMvc.perform(get("/api/v1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Welcome to the Satchel API."));

		mockMvc.perform(get("/api/v1")).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error").value(ApiRateLimitFilter.ERROR_RATE_LIMIT_EXCEEDED))
				.andExpect(jsonPath("$.message").value("Too many requests"))
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, not("")));

		mockMvc.perform(post("/api/v1/funds_transfer").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isOk());
	}

}
