package com.jejo.satchel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jejo.satchel.dto.TransactionDetails;
import com.jejo.satchel.dto.WebhookNotification;
import com.jejo.satchel.dto.WithdrawalRequest;
import com.jejo.satchel.service.AccountService;

@RequestMapping("/api/v1/funds_transfer")
@RestController
public class FundsTransferController {
	
	private final AccountService accountService;

	public FundsTransferController(AccountService accountService) {
		this.accountService = accountService;
	}

	@PostMapping("/withdrawal")
	public ResponseEntity<Void> initiateWithdrawal(@RequestBody WithdrawalRequest withdrawalRequest) {
		accountService.initiateWithdrawal(withdrawalRequest);
		return ResponseEntity.ok(null);
	}

	@PostMapping("/funds_transfer")
	public ResponseEntity<Void> handleTransactionStatusUpdatedWebhook(
			@RequestBody WebhookNotification<TransactionDetails> notification) {
		accountService.processTransactionUpdate(notification.getData());
		return ResponseEntity.ok(null);
	}
	
}
