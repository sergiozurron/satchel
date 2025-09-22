package com.jejo.satchel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class BitcoinPriceConfig {

	@Bean
	RestClient bitcoinPriceRestClient() {
		return RestClient.builder().baseUrl("https://api.coingecko.com/api/v3/simple/price")
				.build();
	}

}
