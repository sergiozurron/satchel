package com.jejo.satchel.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jejo.satchel.exception.TradingOrderException;

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
		String from = CURRENCY_MAPPING.get(fromCurrency);
		String to = CURRENCY_MAPPING.get(toCurrency);
		if (from.equals(to)) {
			return BigDecimal.ONE;
		}
		BigDecimal direct = productPrice(from + "-" + to);
		if (direct != null) {
			return direct.setScale(RATE_SCALE, RoundingMode.HALF_UP);
		}
		BigDecimal inverse = productPrice(to + "-" + from);
		if (inverse != null) {
			return BigDecimal.ONE.divide(inverse, RATE_SCALE, RoundingMode.HALF_UP);
		}
		return usdPrice(from).divide(usdPrice(to), RATE_SCALE, RoundingMode.HALF_UP);
	}

	@Override
	public BigDecimal sell(String fromAsset, String toAsset, BigDecimal amount) {
		String from = CURRENCY_MAPPING.get(fromAsset);
		String to = CURRENCY_MAPPING.get(toAsset);
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
	}

	@Override
	public void withdraw(String asset, BigDecimal amount, String address) {
		String currency = CURRENCY_MAPPING.get(asset);
		if (currency == null) {
			throw new TradingOrderException("No Coinbase currency for collateral asset " + asset);
		}
		String accountId = accountId(currency);
		Map<String, String> body = new LinkedHashMap<>();
		body.put("type", "send");
		body.put("to", address);
		body.put("amount", amount.toPlainString());
		body.put("currency", currency);
		body.put("idem", UUID.randomUUID().toString());
		String path = "/v2/accounts/" + accountId + "/transactions";
		String token = jwt.sign("POST", host, path);
		restClient.post().uri(path).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
				.onStatus(HttpStatusCode::isError, (request, response) -> {
					throw new TradingOrderException("Coinbase collateral send failed");
				}).toBodilessEntity();
	}

	private String accountId(String currency) {
		String path = "/v2/accounts";
		String token = jwt.sign("GET", host, path);
		JsonNode accounts = restClient.get().uri(path + "?limit=100")
				.header("Authorization", "Bearer " + token).retrieve()
				.onStatus(HttpStatusCode::isError, (request, response) -> {
					throw new TradingOrderException("Coinbase account lookup failed");
				}).body(JsonNode.class);
		if (accounts == null) {
			throw new TradingOrderException("Coinbase account lookup failed");
		}
		for (JsonNode account : accounts.path("data")) {
			if (currency.equals(account.path("currency").path("code").asText())) {
				String id = account.path("id").asText("");
				if (!id.isBlank()) {
					return id;
				}
			}
		}
		throw new TradingOrderException("Coinbase account not found for " + currency);
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
		if (!created.path("success").asBoolean(false)) {
			log.error("Error creating coinbase order: {}", coinbaseMessage(created));
		}
		String orderId = created.path("success_response").path("order_id").asText("");
		if (orderId.isBlank()) {
			log.error("{}", "Coinbase order response did not include an order id");
		}
		return filledProceeds(orderId, sellBase);
	}

	private BigDecimal filledProceeds(String orderId, boolean sellBase) {
		String path = BROKERAGE + "/orders/historical/" + orderId;
		JsonNode order = getJson(path).path("order");
		String status = order.path("status").asText("");
		if (!"FILLED".equals(status)) {
			log.error("Coinbase order {} ended with status {}", orderId, status);
		}
		if (sellBase) {
			return requiredDecimal(order, "filled_value", orderId)
					.subtract(requiredDecimal(order, "total_fees", orderId));
		}
		return requiredDecimal(order, "filled_size", orderId);
	}

	private BigDecimal usdPrice(String symbol) {
		if ("USD".equals(symbol)) {
			return BigDecimal.ONE;
		}
		return productPrice(symbol + "-USD");
	}

	private BigDecimal productPrice(String productId) {
		return restClient.get().uri(BROKERAGE + "/market/products/{productId}", productId)
				.exchange((request, response) -> {
					if (response.getStatusCode().isError()) {
						log.warn("Unable to get {} price", productId);
						return null;
					}
					JsonNode price = MAPPER.readTree(response.getBody()).get("price");
					return new BigDecimal(price.asText());
				});
	}

	private boolean productListed(String productId) {
		return restClient.get().uri(BROKERAGE + "/market/products/{productId}", productId)
				.exchange((request, response) -> {
					response.getBody().readAllBytes();
					if (response.getStatusCode().isError()) {
						log.warn("{} is not listed", productId);
						return false;
					}
					return true;
				});
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

	private JsonNode readJson(ClientHttpResponse response) {
		try {
			String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
			return MAPPER.readTree(body);
		} catch (IOException ex) {
		}
		return null;
	}

	private static BigDecimal requiredDecimal(JsonNode order, String field, String orderId) {
		JsonNode node = order.get(field);
		return new BigDecimal(node.asText());
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
