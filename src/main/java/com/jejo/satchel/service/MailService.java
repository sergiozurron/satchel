package com.jejo.satchel.service;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MailService {

	private final RestClient emailRestClient;

	public MailService(RestClient emailRestClient) {
		this.emailRestClient = emailRestClient;
	}

	@Async
	public void sendActivationEmail(String username, String recipient, String activationToken) {
		String activationLink = "http://localhost:8080/api/v1/auth/email-verification?token=" + activationToken;
		try {
			emailRestClient.post().contentType(MediaType.APPLICATION_JSON).body(
					"{\"from\":{\"email\":\"hello@demomailtrap.com\",\"name\":\"Mailtrap Test\"},\"to\":[{\"email\":\""
							+ recipient
							+ "\"}],\"template_uuid\":\"270cbe53-1cbf-4a92-bdc9-8074348f5b16\",\"template_variables\":{\"firstName\":\""
							+ username + "\",\"activationLink\":\"" + activationLink + "\"}}")
					.retrieve().toBodilessEntity(); // or toEntity(...) if expecting a response

			log.info("Email sent successfully to {}", recipient);
		} catch (HttpClientErrorException e) {
			log.error("HTTP error while sending email to {}: {} {}", recipient, e.getStatusCode(),
					e.getResponseBodyAsString());
		} catch (RestClientException e) {
			log.error("Error while sending email to {}: {}", recipient, e.getMessage());
		}
	}

}
