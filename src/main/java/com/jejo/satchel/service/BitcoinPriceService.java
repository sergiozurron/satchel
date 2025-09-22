package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class BitcoinPriceService {

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public BitcoinPriceService(@Qualifier("bitcoinPriceRestClient") RestClient restClient,
			ObjectMapper objectMapper) {
		this.restClient = restClient;
		this.objectMapper = objectMapper;
	}

	public BigDecimal getBtcPrice() {
		BigDecimal price = null;
		String response = restClient.get().uri("?ids=bitcoin&vs_currencies=usd").retrieve()
				.body(String.class);

		JsonNode jsonNode;
		try {
			jsonNode = objectMapper.readTree(response);
			price = new BigDecimal(jsonNode.get("bitcoin").get("usd").asText());
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return price;
	}

	public BigDecimal convertBtcToUsdc(BigDecimal btcAmount) {
		BigDecimal btcPrice = getBtcPrice();
		return btcAmount.multiply(btcPrice);
	}

	public BigDecimal convertUsdToBtc(BigDecimal usdAmount) {
		BigDecimal btcPrice = getBtcPrice();
		return usdAmount.divide(btcPrice, 6, RoundingMode.FLOOR);
	}

}
