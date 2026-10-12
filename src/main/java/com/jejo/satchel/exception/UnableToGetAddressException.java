package com.jejo.satchel.exception;

public class UnableToGetAddressException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public UnableToGetAddressException() {
		super("Asset custodian unable to get deposit address");
	}

}
