package com.jejo.satchel.exception;

public class UnableToConvertAsset extends RuntimeException {

	private static final long serialVersionUID = 1L;

	
	public UnableToConvertAsset(String from, String to) {
		super("Unable to get conversion rate from " + from + " to " + to);
	}
}
