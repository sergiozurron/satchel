package com.jejo.satchel.exception;

public class EmailVerificationTokenUsedException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public EmailVerificationTokenUsedException(String token) {
		super("Email verification token has already been used: " + token);
	}

}
