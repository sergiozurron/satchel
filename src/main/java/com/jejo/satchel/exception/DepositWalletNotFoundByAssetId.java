package com.jejo.satchel.exception;

public class DepositWalletNotFoundByAssetId extends RuntimeException {
    
    private static final long serialVersionUID = 1L;

    public DepositWalletNotFoundByAssetId(String assetId) {
        super("Deposit wallet not found for asset ID: " + assetId);
    }

}
