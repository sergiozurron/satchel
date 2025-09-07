package com.jejo.satchel.dto;

import lombok.Data;

@Data
public class WebhookNotification<T> {

	private String eventType;
	private T data;
}
