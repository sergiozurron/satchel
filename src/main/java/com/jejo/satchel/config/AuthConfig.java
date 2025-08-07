package com.jejo.satchel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

import com.jejo.satchel.repository.UserRepository;

@Configuration
public class AuthConfig {

	@Value("${mailtrap.api.token}")
	private String EMAIL_API_TOKEN;
	
	private final UserRepository userRepository;
	
	public AuthConfig(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	RestClient emailRestClient() {
		return RestClient.builder().baseUrl("https://send.api.mailtrap.io/api/send")
				.defaultHeader("Authorization", "Bearer " + EMAIL_API_TOKEN) // replace with actual API key
				.build();
	}

	@Bean
	UserDetailsService userDetailsService() {
		return username -> userRepository.findByEmail(username)
				.orElseThrow(() -> new UsernameNotFoundException("User with username " + username + " not found"));
	}
	
	@Bean
	AuthenticationManager authenticationManager() {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService());
		authProvider.setPasswordEncoder(passwordEncoder());
		return new ProviderManager(authProvider);
	}

}
