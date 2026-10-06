package com.jejo.satchel.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RestMailService implements MailService {

	@Value("${mailtrap.api.token}")
	private String EMAIL_API_TOKEN;
	@Value("${satchel.email.verification-url}")
	private String VERIFICATION_URL;
	
	@Async
	public void sendActivationEmail(String username, String recipient, String activationToken) {
		RestClient emailRestClient = RestClient.builder().baseUrl("https://send.api.mailtrap.io/api/send")
		.defaultHeader("Authorization", "Bearer " + EMAIL_API_TOKEN) // replace with actual API key
		.build();
		String activationLink = VERIFICATION_URL + "?token=" + activationToken;
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
