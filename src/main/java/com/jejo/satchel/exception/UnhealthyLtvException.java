package com.jejo.satchel.exception;

public class UnhealthyLtvException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UnhealthyLtvException() {
		super("Unlock would exceed the allowed LTV");
	}

}
