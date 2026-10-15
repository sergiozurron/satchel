package com.jejo.satchel.exception;

public class LoanAlreadyPaidException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public LoanAlreadyPaidException(Long loanId) {
		super("Loan with id " + loanId + " has already been paid");
	}
}
