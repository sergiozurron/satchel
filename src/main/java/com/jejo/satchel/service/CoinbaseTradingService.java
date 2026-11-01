package com.jejo.satchel.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoinbaseTradingService implements AssetTradingService {

	private static final Map<String, String> CURRENCY_MAPPING = Map.of("ETH_TEST5", "ETH",
			"USDC_ETH_TEST5_AN74", "USDC", "AVAXTEST", "AVAX", "ETH-AETH_SEPOLIA", "ETH",
			"AMOY_POLYGON_TEST", "POL", "BNB_TEST", "BNB");

	private static final int RATE_SCALE = 8;
	private static final String BROKERAGE = "/api/v3/brokerage";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final RestClient restClient;
	private final CoinbaseJwt jwt;
	private final String host;

	public CoinbaseTradingService(String baseUrl, String apiKey, String apiSecret) {
		this(RestClient.builder().baseUrl(baseUrl).build(), new CoinbaseJwt(apiKey, apiSecret),
				URI.create(baseUrl).getHost());
	}

	CoinbaseTradingService(RestClient restClient, CoinbaseJwt jwt, String host) {
		this.restClient = restClient;
		this.jwt = jwt;
		this.host = host;
	}

	@Override
	public BigDecimal getConversionRate(String fromCurrency, String toCurrency) {
		try {
			String from = CURRENCY_MAPPING.get(fromCurrency);
			String to = CURRENCY_MAPPING.get(toCurrency);
			if (from == null || to == null) {
				log.warn("No Coinbase currency for {} -> {}", fromCurrency, toCurrency);
				return null;
			}
			if (from.equals(to)) {
				return BigDecimal.ONE;
			}
			BigDecimal direct = productPrice(from + "-" + to);
			if (direct != null) {
				return direct.setScale(RATE_SCALE, RoundingMode.HALF_UP);
			}
			BigDecimal inverse = productPrice(to + "-" + from);
			if (inverse != null && inverse.signum() > 0) {
				return BigDecimal.ONE.divide(inverse, RATE_SCALE, RoundingMode.HALF_UP);
			}
			BigDecimal fromUsd = usdPrice(from);
			BigDecimal toUsd = usdPrice(to);
			if (fromUsd == null || toUsd == null || toUsd.signum() <= 0) {
				log.warn("Unable to price {} in {}", fromCurrency, toCurrency);
				return null;
			}
			return fromUsd.divide(toUsd, RATE_SCALE, RoundingMode.HALF_UP);
		} catch (RuntimeException ex) {
			log.warn("Unable to price {} in {}", fromCurrency, toCurrency, ex);
			return null;
		}
	}

	@Override
	public BigDecimal sell(String fromAsset, String toAsset, BigDecimal amount) {
		try {
			String from = CURRENCY_MAPPING.get(fromAsset);
			String to = CURRENCY_MAPPING.get(toAsset);
			if (from == null || to == null) {
				log.error("No Coinbase currency for {} -> {}", fromAsset, toAsset);
				return null;
			}
			if (from.equals(to)) {
				return amount;
			}
			// Fill-or-kill: liquidation books the full base amount, so a partial fill must
			// not happen.
			String direct = from + "-" + to;
			if (productListed(direct)) {
				return placeOrder(direct, "SELL", amount, true);
			}
			String inverse = to + "-" + from;
			if (productListed(inverse)) {
				return placeOrder(inverse, "BUY", amount, false);
			}
			return null;
		} catch (RuntimeException ex) {
			log.error("Coinbase sell failed for {} -> {}", fromAsset, toAsset, ex);
			return null;
		}
	}

	@Override
	public boolean withdraw(String asset, BigDecimal amount, String address) {
		try {
			String currency = CURRENCY_MAPPING.get(asset);
			if (currency == null) {
				log.error("No Coinbase currency for collateral asset {}", asset);
				return false;
			}
			String accountId = accountId(currency);
			if (accountId == null) {
				return false;
			}
			Map<String, String> body = new LinkedHashMap<>();
			body.put("type", "send");
			body.put("to", address);
			body.put("amount", amount.toPlainString());
			body.put("currency", currency);
			body.put("idem", UUID.randomUUID().toString());
			String path = "/v2/accounts/" + accountId + "/transactions";
			String token = jwt.sign("POST", host, path);
			Boolean sent = restClient.post().uri(path).header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON).body(body)
					.exchange((request, response) -> {
						if (response.getStatusCode().isError()) {
							log.error("Coinbase collateral send failed: {} {}",
									response.getStatusCode(), readBody(response));
							return false;
						}
						readBody(response);
						return true;
					});
			return Boolean.TRUE.equals(sent);
		} catch (RuntimeException ex) {
			log.error("Coinbase collateral send failed for {}", asset, ex);
			return false;
		}
	}

	private String accountId(String currency) {
		try {
			String path = "/v2/accounts";
			String token = jwt.sign("GET", host, path);
			JsonNode accounts = restClient.get().uri(path + "?limit=100")
					.header("Authorization", "Bearer " + token)
					.exchange((request, response) -> {
						if (response.getStatusCode().isError()) {
							log.error("Coinbase account lookup failed: {} {}",
									response.getStatusCode(), readBody(response));
							return null;
						}
						return readJson(response);
					});
			if (accounts == null) {
				return null;
			}
			for (JsonNode account : accounts.path("data")) {
				if (currency.equals(account.path("currency").path("code").asText())) {
					String id = account.path("id").asText("");
					if (!id.isBlank()) {
						return id;
					}
				}
			}
			log.error("Coinbase account not found for {}", currency);
			return null;
		} catch (RuntimeException ex) {
			log.error("Coinbase account lookup failed", ex);
			return null;
		}
	}

	private BigDecimal placeOrder(String productId, String side, BigDecimal amount,
			boolean sellBase) {
		Map<String, String> size = new LinkedHashMap<>();
		if (sellBase) {
			size.put("base_size", amount.toPlainString());
		} else {
			size.put("quote_size", amount.toPlainString());
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("client_order_id", UUID.randomUUID().toString());
		body.put("product_id", productId);
		body.put("side", side);
		body.put("order_configuration", Map.of("market_market_fok", size));

		JsonNode created = postJson(BROKERAGE + "/orders", body);
		if (created == null || !created.path("success").asBoolean(false)) {
			log.error("Error creating coinbase order: {}",
					created == null ? "empty response" : coinbaseMessage(created));
			return null;
		}
		String orderId = created.path("success_response").path("order_id").asText("");
		if (orderId.isBlank()) {
			log.error("Coinbase order response did not include an order id");
			return null;
		}
		return filledProceeds(orderId, sellBase);
	}

	private BigDecimal filledProceeds(String orderId, boolean sellBase) {
		JsonNode root = getJson(BROKERAGE + "/orders/historical/" + orderId);
		if (root == null) {
			log.error("Coinbase order {} could not be read", orderId);
			return null;
		}
		JsonNode order = root.path("order");
		String status = order.path("status").asText("");
		if (!"FILLED".equals(status)) {
			log.error("Coinbase order {} ended with status {}", orderId, status);
		}
		if (sellBase) {
			BigDecimal filledValue = decimalField(order, "filled_value", orderId);
			BigDecimal fees = decimalField(order, "total_fees", orderId);
			if (filledValue == null || fees == null) {
				return null;
			}
			return filledValue.subtract(fees);
		}
		return decimalField(order, "filled_size", orderId);
	}

	private BigDecimal usdPrice(String symbol) {
		if ("USD".equals(symbol)) {
			return BigDecimal.ONE;
		}
		return productPrice(symbol + "-USD");
	}

	private BigDecimal productPrice(String productId) {
		try {
			return restClient.get().uri(BROKERAGE + "/market/products/{productId}", productId)
					.exchange((request, response) -> {
						if (response.getStatusCode().isError()) {
							log.warn("Unable to get {} price", productId);
							readBody(response);
							return null;
						}
						JsonNode body = readJson(response);
						if (body == null) {
							log.warn("Unable to get {} price", productId);
							return null;
						}
						JsonNode price = body.get("price");
						if (price == null || price.asText("").isBlank()) {
							log.warn("Unable to get {} price", productId);
							return null;
						}
						return new BigDecimal(price.asText());
					});
		} catch (RuntimeException ex) {
			log.warn("Unable to get {} price", productId, ex);
			return null;
		}
	}

	private boolean productListed(String productId) {
		try {
			Boolean listed = restClient.get()
					.uri(BROKERAGE + "/market/products/{productId}", productId)
					.exchange((request, response) -> {
						readBody(response);
						if (response.getStatusCode().isError()) {
							log.warn("{} is not listed", productId);
							return false;
						}
						return true;
					});
			return Boolean.TRUE.equals(listed);
		} catch (RuntimeException ex) {
			log.warn("{} is not listed", productId, ex);
			return false;
		}
	}

	private JsonNode postJson(String path, Object body) {
		String token = jwt.sign("POST", host, path);
		return restClient.post().uri(path).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).body(body)
				.exchange((request, response) -> readJson(response));
	}

	private JsonNode getJson(String path) {
		String token = jwt.sign("GET", host, path);
		return restClient.get().uri(path).header("Authorization", "Bearer " + token)
				.exchange((request, response) -> readJson(response));
	}

	private String readBody(ClientHttpResponse response) {
		try {
			return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			log.error("Coinbase response could not be read", ex);
			return "";
		}
	}

	private JsonNode readJson(ClientHttpResponse response) {
		String body = readBody(response);
		if (body.isBlank()) {
			return null;
		}
		try {
			return MAPPER.readTree(body);
		} catch (IOException ex) {
			log.error("Coinbase response could not be read", ex);
			return null;
		}
	}

	private static BigDecimal decimalField(JsonNode order, String field, String orderId) {
		JsonNode node = order.get(field);
		if (node == null || node.asText("").isBlank()) {
			log.error("Coinbase order {} is missing {}", orderId, field);
			return null;
		}
		try {
			return new BigDecimal(node.asText());
		} catch (NumberFormatException ex) {
			log.error("Coinbase order {} has an invalid {}", orderId, field);
			return null;
		}
	}

	private static String coinbaseMessage(JsonNode node) {
		JsonNode error = node.path("error_response");
		String message = error.path("message").asText("");
		String details = error.path("error_details").asText("");
		if (!message.isBlank() && !details.isBlank()) {
			return message + ": " + details;
		}
		if (!message.isBlank()) {
			return message;
		}
		if (!node.path("message").asText("").isBlank()) {
			return node.path("message").asText();
		}
		return "Coinbase order was rejected";
	}

}
