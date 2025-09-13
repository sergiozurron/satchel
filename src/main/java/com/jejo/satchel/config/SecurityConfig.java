package com.jejo.satchel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.jejo.satchel.controller.JwtLogoutHandler;
import com.jejo.satchel.middleware.JwtAuthFilter;

@EnableWebSecurity
@Configuration
public class SecurityConfig {
	
	public final JwtAuthFilter jwtAuthFilter;
	public final JwtLogoutHandler jwtLogoutHandler;
	
	public SecurityConfig(JwtAuthFilter jwtAuthFilter, JwtLogoutHandler jwtLogoutHandler) {
		this.jwtAuthFilter = jwtAuthFilter;
		this.jwtLogoutHandler = jwtLogoutHandler;
	}
	
	// Checkout Spring Security 6.0 migration guide for more details
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.requestMatchers("/api/v1/auth/**", "/api/v1/accounts/funds_transfer").permitAll()
				.anyRequest().authenticated())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(AbstractHttpConfigurer::disable)
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.cors(Customizer.withDefaults())
				.logout(logout -> logout
						.logoutUrl("/api/v1/auth/logout")
						.addLogoutHandler(jwtLogoutHandler)
						.logoutSuccessHandler((request, response, authentication) -> {}));
		return http.build();
	}

}
