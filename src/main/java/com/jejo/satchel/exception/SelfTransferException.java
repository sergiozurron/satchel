package com.jejo.satchel.exception;

public class SelfTransferException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public SelfTransferException() {
		super("Cannot transfer funds to your own account" );
	}
}
