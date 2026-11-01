package com.jejo.satchel.service;

import java.math.BigDecimal;

public interface AssetTradingService {
	BigDecimal getConversionRate(String fromCurrency, String toCurrency);
	BigDecimal sell(String fromAsset, String toAsset, BigDecimal amount);

	boolean withdraw(String asset, BigDecimal amount, String address);
}
