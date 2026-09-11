package com.jejo.satchel.exception;

public class LoanNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public LoanNotFoundException() {
		super("Loan not found");
	}

	public LoanNotFoundException(String message) {
		super(message);
	}
}
