package com.jejo.satchel.exception;

public class CannotCreateWalletException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CannotCreateWalletException(String vaultAccountName) {
		super("Cannot create wallet in vault account: " + vaultAccountName);
	}
}
