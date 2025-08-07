package com.jejo.satchel.exception;

public class EmailVerificationTokenRevokedException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public EmailVerificationTokenRevokedException(String token) {
		super("Email verification token has been revoked: " + token);
	}

}
