package com.jejo.satchel.exception;

public class EmailVerificationTokenExpiredException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public EmailVerificationTokenExpiredException(String token) {
		super("Email verification token expired: " + token);
	}

}
