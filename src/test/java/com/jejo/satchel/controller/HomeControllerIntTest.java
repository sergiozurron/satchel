package com.jejo.satchel.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeControllerIntTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void home_shouldReturnWelcome_withoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Welcome to the Satchel API."));
	}

}
