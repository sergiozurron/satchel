package com.jejo.satchel.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class BitcoinPriceService {
	
	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	
	public BitcoinPriceService(RestClient restClient, ObjectMapper objectMapper) {
		this.restClient = restClient;
		this.objectMapper = objectMapper;
	}
	
	public Double getBtcPrice() {
		try {
            String response = restClient.get()
                .uri("/simple/price?ids=bitcoin&vs_currencies=usd")
                .retrieve()
                .body(String.class);

            JsonNode jsonNode = objectMapper.readTree(response);
            return jsonNode.get("bitcoin").get("usd").asDouble();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch BTC price: " + e.getMessage(), e);
        }
	}

	public Double convertBtcToUsd(Double btcAmount) {
		Double btcPrice = getBtcPrice();
		return btcAmount * btcPrice;
	}
	
	public Double convertUsdToBtc(Double usdAmount) {
		Double btcPrice = getBtcPrice();
		return usdAmount / btcPrice;
	}

}
