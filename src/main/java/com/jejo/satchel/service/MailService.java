package com.jejo.satchel.service;

public interface MailService {
	public void sendActivationEmail(String username, String recipient, String activationToken);
}
