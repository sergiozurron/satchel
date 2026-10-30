package com.jejo.satchel.exception;

public class CollateralTransferException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public CollateralTransferException() {
		super("Collateral transfer to trading service failed");
	}

}
