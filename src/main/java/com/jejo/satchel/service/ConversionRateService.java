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
public class AssetPriceService {

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public AssetPriceService(@Qualifier("bitcoinPriceRestClient") RestClient restClient,
			ObjectMapper objectMapper) {
		this.restClient = restClient;
		this.objectMapper = objectMapper;
	}

	public BigDecimal getEthPrice() {
		BigDecimal price = null;
		String response = restClient.get().uri("?ids=ethereum&vs_currencies=usd").retrieve()
				.body(String.class);

		JsonNode jsonNode;
		try {
			jsonNode = objectMapper.readTree(response);
			price = new BigDecimal(jsonNode.get("ethereum").get("usd").asText());
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return price;
	}

	public BigDecimal convertEthToUsdc(BigDecimal EthAmount) {
		BigDecimal EthPrice = getEthPrice();
		return EthAmount.multiply(EthPrice);
	}

	public BigDecimal convertUsdToEth(BigDecimal usdAmount) {
		BigDecimal EthPrice = getEthPrice();
		return usdAmount.divide(EthPrice, 6, RoundingMode.FLOOR);
	}

}
