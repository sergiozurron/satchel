package com.jejo.satchel.exception;

import java.math.BigDecimal;

public class InsufficientBalanceForRepaymentException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InsufficientBalanceForRepaymentException() {
		super("Insufficient balance in deposit wallet for repayment");
	}

	public InsufficientBalanceForRepaymentException(BigDecimal availableBalance, BigDecimal requiredAmount) {
		super(String.format("Insufficient balance. Available: %.7f, Required: %.7f", 
			availableBalance, requiredAmount));
	}
}
