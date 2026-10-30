package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CoingeckoTradingService implements AssetTradingService {

	private static final Map<String, String> CURRENCY_MAPPING = Map.of("ETH_TEST5", "ethereum",
			"USDC_ETH_TEST5_AN74", "usd-coin", "AVAXTEST", "avalanche-2", "ETH-AETH_SEPOLIA",
			"ethereum", "AMOY_POLYGON_TEST", "matic-network", "BNB_TEST", "binancecoin");

	private final RestClient restClient;

	public CoingeckoTradingService() {
		this.restClient = RestClient.builder()
				.baseUrl("https://api.coingecko.com/api/v3/simple/price").build();
	}

	public BigDecimal getConversionRate(String fromCurrency, String toCurrency) {
		BigDecimal rate = null;
		String mappedFromCurrency = CURRENCY_MAPPING.get(fromCurrency);
		String mappedToCurrency = CURRENCY_MAPPING.get(toCurrency);
		String response = restClient.get()
				.uri("?ids=" + mappedFromCurrency + "," + mappedToCurrency + "&vs_currencies=usd")
				.retrieve().body(String.class);

		JsonNode jsonNode;
		try {
			jsonNode = new ObjectMapper().readTree(response);
			BigDecimal rateFrom = new BigDecimal(jsonNode.get(mappedFromCurrency).get("usd").asText());
			BigDecimal rateTo = new BigDecimal(jsonNode.get(mappedToCurrency).get("usd").asText());
			rate = rateFrom.divide(rateTo, 8, RoundingMode.HALF_UP);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return rate;
	}

	@Override
	public BigDecimal sell(String fromAsset, String toAsset, BigDecimal amount) {
		return getConversionRate(fromAsset, toAsset).multiply(amount);
	}

	@Override
	public void withdraw(String asset, BigDecimal amount, String address) {
		// Paper prices only. Unlock still updates the ledger; coins are not moved.
	}

}
