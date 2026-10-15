package com.jejo.satchel.exception;

public class InsufficientPoolFundsException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InsufficientPoolFundsException() {
		super("Insufficient funds in the pool");
	}
}
