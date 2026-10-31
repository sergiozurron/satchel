package com.jejo.satchel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.jejo.satchel.service.AssetCustodianService;
import com.jejo.satchel.service.AssetTradingService;
import com.jejo.satchel.service.CoinbaseTradingService;
import com.jejo.satchel.service.CoingeckoTradingService;
import com.jejo.satchel.service.CurrentUserProvider;
import com.jejo.satchel.service.FireblocksService;
import com.jejo.satchel.service.SecurityContextUserProvider;

@Configuration
public class ServiceConfig {

	@Bean
	AssetCustodianService assetCustodianService(
			@Value("${satchel.custodian.api.key}") String apiKey,
			@Value("${satchel.custodian.api.secret}") String secretKey) {
		return new FireblocksService(apiKey, secretKey);
	}
	
	@Bean
	@ConditionalOnProperty(name = "satchel.trading.provider", havingValue = "coingecko", matchIfMissing = true)
	AssetTradingService coingeckoTradingService() {
		return new CoingeckoTradingService();
	}

	@Bean
	@ConditionalOnProperty(name = "satchel.trading.provider", havingValue = "coinbase")
	AssetTradingService coinbaseTradingService(
			@Value("${coinbase.api.base-url}") String baseUrl,
			@Value("${coinbase.api.key-name}") String apiKey,
			@Value("${coinbase.api.secret}") String apiSecret) {
		return new CoinbaseTradingService(baseUrl, apiKey, apiSecret);
	}
	
	@Bean
	CurrentUserProvider currentUserProvider() {
		return new SecurityContextUserProvider();
	}

}
