package com.jejo.satchel.exception;

public class DepositWalletNotFoundByAssetIdException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;

    public DepositWalletNotFoundByAssetIdException(String assetId) {
        super("Deposit wallet not found for asset ID: " + assetId);
    }

}
