package com.jejo.satchel.exception;

public class EmailVerificationTokenNotFoundException extends RuntimeException {
	
	private static final long serialVersionUID = 1L;

	public EmailVerificationTokenNotFoundException(String token) {
		super("Email verification token not found: " + token);
	}

}
