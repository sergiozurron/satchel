package com.jejo.satchel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CoinbaseTradingServiceTest {

	private static final String BASE_URL = "https://api.coinbase.com";

	private MockRestServiceServer server;
	private CoinbaseTradingService service;

	@BeforeEach
	void setUp() throws Exception {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		service = new CoinbaseTradingService(builder.build(),
				new CoinbaseJwt("organizations/test/apiKeys/test", ecPem()), "api.coinbase.com");
	}

	@Test
	void withdraw_returnsFalseBeforeAnyRequest_whenTheAssetIsUnmapped() {
		assertThat(service.withdraw("NOT_A_REAL_ASSET", BigDecimal.ONE, "0xdest")).isFalse();

		server.verify();
	}

	@Test
	void withdraw_returnsTrueWhenTheMappedCurrencyIsSent() {
		server.expect(requestTo(BASE_URL + "/v2/accounts?limit=100")).andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", startsWith("Bearer ")))
				.andRespond(withSuccess(account("acct-eth", "ETH"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(BASE_URL + "/v2/accounts/acct-eth/transactions"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", startsWith("Bearer ")))
				.andExpect(content().json(
						"{\"type\":\"send\",\"to\":\"0xdest\",\"amount\":\"1.25\",\"currency\":\"ETH\"}",
						JsonCompareMode.LENIENT))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		assertThat(service.withdraw("ETH_TEST5", new BigDecimal("1.25"), "0xdest")).isTrue();

		server.verify();
	}

	@Test
	void withdraw_returnsFalseWhenTheCurrencyAccountIsMissing() {
		server.expect(requestTo(BASE_URL + "/v2/accounts?limit=100")).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(account("acct-btc", "BTC"), MediaType.APPLICATION_JSON));

		assertThat(service.withdraw("ETH_TEST5", BigDecimal.ONE, "0xdest")).isFalse();

		server.verify();
	}

	@Test
	void withdraw_returnsFalseWhenSendIsRejected_andDoesNotRetry() {
		server.expect(requestTo(BASE_URL + "/v2/accounts?limit=100")).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(account("acct-eth", "ETH"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(Matchers.containsString("/transactions")))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"message\":\"rejected\"}"));

		assertThat(service.withdraw("ETH_TEST5", BigDecimal.ONE, "0xdest")).isFalse();

		server.verify();
	}

	private static String account(String id, String currency) {
		return "{\"data\":[{\"id\":\"" + id + "\",\"currency\":{\"code\":\"" + currency + "\"}}]}";
	}

	private static String ecPem() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(new ECGenParameterSpec("secp256r1"));
		String body = Base64.getMimeEncoder(64, new byte[] { '\n' })
				.encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
		return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
	}

}
