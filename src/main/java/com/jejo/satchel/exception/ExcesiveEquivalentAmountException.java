package com.jejo.satchel.exception;

import java.math.BigDecimal;

public class ExcesiveEquivalentAmountException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public ExcesiveEquivalentAmountException(BigDecimal borrowableAmount) {
		super("Borrowable amount exeeded. Max: " + borrowableAmount.toString());
	}

}
