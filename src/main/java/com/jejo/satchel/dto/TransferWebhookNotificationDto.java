package com.jejo.satchel.dto;

import lombok.Data;

@Data
public class TransferWebhookNotificationDto {
	
	private String webhook; // Webhook ID
	private String transfer; // Transfer ID
	private String wallet;
	private String coin;
}
