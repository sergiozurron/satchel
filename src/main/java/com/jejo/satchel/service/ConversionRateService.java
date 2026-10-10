package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ConversionRateService {

	private static final Map<String, String> CURRENCY_MAPPING = Map.of("ETH_TEST5", "ethereum",
			"USDC_ETH_TEST5_AN74", "usd-coin", "AVAXTEST", "avalanche-2", "ETH-AETH_SEPOLIA",
			"ethereum", "AMOY_POLYGON_TEST", "matic-network", "BNB_TEST", "binancecoin");

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public ConversionRateService(ObjectMapper objectMapper) {
		this.restClient = RestClient.builder()
				.baseUrl("https://api.coingecko.com/api/v3/simple/price").build();
		this.objectMapper = objectMapper;
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
			jsonNode = objectMapper.readTree(response);
			BigDecimal rateFrom = new BigDecimal(jsonNode.get(mappedFromCurrency).get("usd").asText());
			BigDecimal rateTo = new BigDecimal(jsonNode.get(mappedToCurrency).get("usd").asText());
			rate = rateFrom.divide(rateTo, 8, RoundingMode.HALF_UP);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return rate;
	}

	public BigDecimal convertCurrency(BigDecimal amount, String fromCurrency, String toCurrency) {
		BigDecimal conversionRate = getConversionRate(fromCurrency, toCurrency);
		return amount.multiply(conversionRate);
	}

}
