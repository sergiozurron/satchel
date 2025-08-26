package com.jejo.satchel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.jejo.satchel.dto.TransferWebhookNotificationDto;
import com.jejo.satchel.service.TransferService;

@RestController("/api/v1")
public class TransferController {

	private final TransferService transferService;

	public TransferController(TransferService transferFacade) {
		this.transferService = transferFacade;
	}

	@PostMapping("/transfers/new")
	public ResponseEntity<Void> handleNewTransferWebhook(@RequestHeader("X-Signature-SHA256") String webhookSignature,
			@RequestBody TransferWebhookNotificationDto transferRequest, @RequestBody String rawPayload) {
		transferService.processNewTransfer(webhookSignature, transferRequest, rawPayload);
		return ResponseEntity.ok(null);
	}
	
	@PostMapping("/transfers/confirm")
	public ResponseEntity<Void> handleTransferConfirmation(@RequestHeader("X-Signature-SHA256") String webhookSignature,
			@RequestBody TransferWebhookNotificationDto transferRequest, @RequestBody String rawPayload) {
		transferService.processTransferConfirmation(webhookSignature, transferRequest, rawPayload);
		return ResponseEntity.ok(null);
	}

}
