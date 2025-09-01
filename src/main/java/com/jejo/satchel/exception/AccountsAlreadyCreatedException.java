package com.jejo.satchel.exception;

public class AccountsAlreadyCreatedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public AccountsAlreadyCreatedException(Long userId) {
		super("User with ID " + userId + " already has accounts");
	}
}
