package com.jejo.satchel.service;

import java.math.BigDecimal;

public interface ConversionRateService {
	BigDecimal getConversionRate(String fromCurrency, String toCurrency);

	BigDecimal sell(String fromAsset, String toAsset, BigDecimal amount);
}
