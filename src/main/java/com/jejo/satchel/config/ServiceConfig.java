package com.jejo.satchel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.jejo.satchel.service.AssetCustodianService;
import com.jejo.satchel.service.CoingeckoConversionRateService;
import com.jejo.satchel.service.ConversionRateService;
import com.jejo.satchel.service.FireblocksService;

@Configuration
public class ServiceConfig {

	@Bean
	AssetCustodianService assetCustodianService(
			@Value("${satchel.custodian.api.key}") String apiKey,
			@Value("${satchel.custodian.api.secret}") String secretKey) {
		return new FireblocksService(apiKey, secretKey);
	}
	
	@Bean
	ConversionRateService conversionRateService() {
		return new CoingeckoConversionRateService();
	}

}
