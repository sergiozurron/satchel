package com.jejo.satchel.exception;

public class DepositWalletNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public DepositWalletNotFoundException() {
		super("Deposit wallet not found");
	}

}
