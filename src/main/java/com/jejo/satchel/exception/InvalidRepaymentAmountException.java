package com.jejo.satchel.exception;

import java.math.BigDecimal;

public class InvalidRepaymentAmountException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InvalidRepaymentAmountException() {
		super("Repayment amount must be greater than 0");
	}

	public InvalidRepaymentAmountException(String message) {
		super(message);
	}

	public InvalidRepaymentAmountException(BigDecimal repaymentAmount, BigDecimal outstandingAmount) {
		super(String.format("Repayment amount %.7f exceeds outstanding loan amount %.7f", 
			repaymentAmount, outstandingAmount));
	}
}
