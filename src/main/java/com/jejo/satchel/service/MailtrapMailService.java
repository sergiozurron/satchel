package com.jejo.satchel.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;

import io.mailtrap.client.MailtrapClient;
import io.mailtrap.config.MailtrapConfig;
import io.mailtrap.factory.MailtrapClientFactory;
import io.mailtrap.model.request.emails.Address;
import io.mailtrap.model.request.emails.MailtrapMail;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MailtrapMailService implements MailService {

	@Value("${satchel.email.sender}")
	private String SENDER_EMAIL;
	@Value("${satchel.email.verification-url}")
	private String VERIFICATION_URL;
	@Value("${mailtrap.api.token}")
	private String TOKEN;
	@Value("${mailtrap.api.verification-template}")
	private String VERIFICATION_TEMPLATE;

	@Override
	public void sendActivationEmail(String username, String recipient, String verificationToken) {
		final MailtrapConfig config = new MailtrapConfig.Builder().token(TOKEN).build();

		final MailtrapClient client = MailtrapClientFactory.createMailtrapClient(config);

		final MailtrapMail mail = MailtrapMail.builder().from(new Address(SENDER_EMAIL))
				.to(List.of(new Address(recipient))).templateUuid(VERIFICATION_TEMPLATE)
				.templateVariables(
						Map.of("activationLink", VERIFICATION_URL + "/api/v1/auth/email-verification?token=" + verificationToken))
				.build();

		try {
			System.out.println(client.send(mail));
			log.info("Email sent successfully to {}", recipient);
		} catch (Exception e) {
			System.out.println("Caught exception : " + e);
			log.error("Error while sending email to {}: {}", recipient, e.getMessage());
		}
	}

}
