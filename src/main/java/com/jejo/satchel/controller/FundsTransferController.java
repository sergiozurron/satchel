package com.jejo.satchel.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jejo.satchel.dto.FundsTransferResponse;
import com.jejo.satchel.dto.TransactionDetails;
import com.jejo.satchel.dto.WebhookNotification;
import com.jejo.satchel.dto.WithdrawalRequest;
import com.jejo.satchel.mapper.FundsTransferMapper;
import com.jejo.satchel.model.FundsTransfer;
import com.jejo.satchel.service.AccountService;

import jakarta.validation.Valid;

@RequestMapping("/api/v1/funds_transfer")
@RestController
public class FundsTransferController {
	
	private final AccountService accountService;
	private final FundsTransferMapper fundsTransferMapper;

	public FundsTransferController(AccountService accountService, FundsTransferMapper fundsTransferMapper) {
		this.accountService = accountService;
		this.fundsTransferMapper = fundsTransferMapper;
	}

	@GetMapping
	public ResponseEntity<List<FundsTransferResponse>> getAllUserFundsTransfers() {
		return ResponseEntity.ok(fundsTransferMapper.toFundsTransferResponses(
				accountService.getAllUserFundsTransfers()));
	}

	@PostMapping("/withdrawal")
	public ResponseEntity<FundsTransferResponse> initiateWithdrawal(
			@Valid @RequestBody WithdrawalRequest withdrawalRequest) {
		FundsTransfer created = accountService.initiateWithdrawal(withdrawalRequest);
		return ResponseEntity.ok(fundsTransferMapper.toFundsTransferResponse(created));
	}

	@PostMapping
	public ResponseEntity<Void> handleTransactionStatusUpdatedWebhook(
			@RequestBody WebhookNotification<TransactionDetails> notification) {
		accountService.processTransactionUpdate(notification.getData());
		return ResponseEntity.ok(null);
	}
	
}
