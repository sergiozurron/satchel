package com.jejo.satchel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginRequest {
	
	@NotBlank(message = "Email is required")
	private String email;
	private String password;
}
