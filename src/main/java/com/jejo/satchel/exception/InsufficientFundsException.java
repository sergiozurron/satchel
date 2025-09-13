package com.jejo.satchel.exception;

public class InsufficientFundsException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InsufficientFundsException() {
		super("Insufficient funds in account");
	}
}
