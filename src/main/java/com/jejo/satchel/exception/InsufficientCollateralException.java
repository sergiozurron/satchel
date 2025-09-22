package com.jejo.satchel.exception;

public class InsufficientCollateralException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InsufficientCollateralException() {
		super("Insufficient collateral");
	}
}
