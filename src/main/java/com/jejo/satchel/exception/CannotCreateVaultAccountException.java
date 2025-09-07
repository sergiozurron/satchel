package com.jejo.satchel.exception;

public class CannotCreateVaultAccountException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CannotCreateVaultAccountException(String accountName) {
		super("Cannot create vault account: " + accountName);
	}
}
