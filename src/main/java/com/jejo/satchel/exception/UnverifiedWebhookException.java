package com.jejo.satchel.exception;

public class UnverifiedWebhookException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public UnverifiedWebhookException(String webhook) {
		super("Unverified webhook notification: " + webhook);
	}
}
