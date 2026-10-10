package com.jejo.satchel.exception;

public class DepositWalletAlreadyExistsException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public DepositWalletAlreadyExistsException(String assetId) {
		super("Deposit wallet for asset " + assetId + " already exists for the current user.");
	}

}
