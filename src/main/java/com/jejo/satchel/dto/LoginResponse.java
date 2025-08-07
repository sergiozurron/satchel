package com.jejo.satchel.dto;

import lombok.experimental.SuperBuilder;

@SuperBuilder
public class LoginResponse extends Response {

	private String token;
	
	public LoginResponse(String message, String token) {
		super(message);
		this.token = token;
	}
	
	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

}
