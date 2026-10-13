package com.jejo.satchel.service;

import java.math.BigDecimal;

public interface AssetCustodianService {
	String getDepositAddress(Long userId, String assetId);
	String createTransaction(String assetId, BigDecimal amount, Long userId, String destinationAddress);
	BigDecimal getPoolSize(String assetId);
}
