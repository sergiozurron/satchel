package com.jejo.satchel.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jejo.satchel.dto.BitGoCreateWebhookResponseDto;
import com.jejo.satchel.dto.BitGoGetTransferResponseDto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BitGoService {

	@Value("${bitgo.api.base-url}")
	private String baseUrl;

	@Value("${bitgo.api.token}")
	private String apiToken;

	@Value("${bitgo.coin}")
	private String coin;

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	public BitGoService(ObjectMapper objectMapper) {
		restClient = RestClient.builder().baseUrl(baseUrl)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
		this.objectMapper = objectMapper;
	}
	// Do manualy or at application startup
//    public String createWallet(String label, String passphrase) {
//        String url = baseUrl + "/" + coin + "/wallet/generate";
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Authorization", "Bearer " + apiToken);
//        headers.setContentType(MediaType.APPLICATION_JSON);
//
//        String requestBody = "{ \"label\": \"" + label + "\", \"passphrase\": \"" + passphrase + "\" }";
//        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
//
//        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
//        if (response.getStatusCode() == HttpStatus.OK) {
//            // Parse JSON for wallet ID, e.g., using Jackson ObjectMapper
//            // Save wallet ID in your database for the pool
//            return response.getBody(); // Contains wallet details
//        } else {
//            throw new RuntimeException("Wallet creation failed: " + response.getStatusCode());
//        }
//    }

	public String generateDepositAddress(String walletId, String userLabel) {
		String url = "/" + coin + "/wallet/" + walletId + "/address/new";
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + apiToken);
		headers.setContentType(MediaType.APPLICATION_JSON);

		String requestBody = "{ \"label\": \"" + userLabel + "\" }"; // Associate with user
		HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
		
		// Throws RestClientException if status code is 4xx or 5xx
		ResponseEntity<String> response = restClient.post().uri(url).body(entity).retrieve().toEntity(String.class);
		log.info("Response from BitGo: {}", response.getBody());
		String address = null;
		try {
			return objectMapper.readTree(response.getBody()).get("address").asText();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return address;
	}

	public boolean verifyWebhookNotification(String webhook, String webhookSignature, String rawPayload) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + apiToken);
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("X-Signature-SHA256", webhookSignature);

		String requestBody = "{ \"signature\": \"" + webhookSignature + "\", \"notificationPayload\": \"" + rawPayload
				+ "\" }";
		HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

		// Throws RestClientException if status code is 4xx or 5xx
		ResponseEntity<String> response = restClient.post().uri("/webhook/" + webhook + "/verify").body(entity)
				.retrieve().toEntity(String.class);
		log.info("Response from BitGo verify webhook: {}", response.getBody());
		boolean isValid = false;
		try {
			isValid = objectMapper.readTree(response.getBody()).get("valid").asBoolean();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return isValid;
	}

	public BitGoGetTransferResponseDto getTransfer(String coin2, String wallet, String transfer) {
		// TODO Auto-generated method stub
		return null;
	}

	public String createTransactionConfirmationWebhook(String coin) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		String requestBody = "{ \"type\": \"block\", \"url\": \"http://localhost:8080/api/v1/transfers/confirm\", \"numConfirmations\": 12 }";
		HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
		ResponseEntity<BitGoCreateWebhookResponseDto> response = restClient.post().uri("/" + coin + "/webhook")
				.body(entity).retrieve().toEntity(BitGoCreateWebhookResponseDto.class);

		return response.getBody().getId(); // Return the webhook ID
	}

//    public String getWalletBalance(String walletId) {
//		String url = baseUrl + "/" + coin + "/wallet/" + walletId + "/balance";
//		HttpHeaders headers = new HttpHeaders();
//		headers.set("Authorization", "Bearer " + apiToken);
//		headers.setContentType(MediaType.APPLICATION_JSON);
//
//		HttpEntity<String> entity = new HttpEntity<>(headers);
//		ResponseEntity<String> response = restClient.get().uri(url)
//				.headers(httpHeaders -> httpHeaders.addAll(headers))
//				.retrieve()
//				.toEntity(String.class);
//		if (response.getStatusCode() == HttpStatus.OK) {
//			return response.getBody(); // e.g., {"balance": 1000000}
//		} else {
//			throw new RuntimeException("Failed to fetch wallet balance: " + response.getStatusCode());
//		}
//    }

//    public void setupWebhook(String walletId, String webhookUrl) {
//        String url = baseUrl + "/webhooks";
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Authorization", "Bearer " + apiToken);
//        headers.setContentType(MediaType.APPLICATION_JSON);
//
//        String requestBody = "{ \"type\": \"transfer\", \"url\": \"" + webhookUrl + "\", \"token\": \"your-secret\", \"walletId\": \"" + walletId + "\" }";
//        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
//        restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
//    }

}