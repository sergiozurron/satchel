package com.jejo.satchel.exception;

public class UserAlreadyVerifiedException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public UserAlreadyVerifiedException(String token) {
		super("User with token " + token + " is already verified.");
	}

}
