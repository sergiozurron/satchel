package com.jejo.satchel.exception;

public class AssetPriceProviderException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public AssetPriceProviderException(String asset) {
		super("Failed to fetch " + asset + " price");
	}

}
