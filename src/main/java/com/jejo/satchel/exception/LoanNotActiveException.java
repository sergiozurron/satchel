package com.jejo.satchel.exception;

public class LoanNotActiveException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public LoanNotActiveException() {
		super("Loan is not active");
	}

	public LoanNotActiveException(String message) {
		super(message);
	}
}
