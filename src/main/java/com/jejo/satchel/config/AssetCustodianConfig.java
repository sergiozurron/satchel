package com.jejo.satchel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fireblocks.sdk.BasePath;
import com.fireblocks.sdk.ConfigurationOptions;
import com.fireblocks.sdk.Fireblocks;

@Configuration
public class AssetCustodianConfig {

	@Value("${custodian.api.key}")
	private String apiKey;
	@Value("${custodian.api.secret}")
	private String secretKey;
	
	@Bean
	Fireblocks custodianRestClient() {
		ConfigurationOptions configurationOptions = new ConfigurationOptions().basePath(BasePath.Sandbox)
				.apiKey(apiKey).secretKey(secretKey);
		return new Fireblocks(configurationOptions);
	}

}
