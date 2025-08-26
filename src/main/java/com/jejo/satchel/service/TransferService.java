package com.jejo.satchel.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.jejo.satchel.dto.BitGoGetTransferResponseDto;
import com.jejo.satchel.dto.BitGoGetTransferResponseEntryDto;
import com.jejo.satchel.dto.TransferWebhookNotificationDto;
import com.jejo.satchel.exception.UnverifiedWebhookException;
import com.jejo.satchel.model.Account;
import com.jejo.satchel.model.Transaction;
import com.jejo.satchel.model.TransactionStatus;
import com.jejo.satchel.repository.AccountRepository;
import com.jejo.satchel.repository.TransactionRepository;

import jakarta.transaction.Transactional;

@Service
public class TransferService {

	private final BitGoService bitGoService;
	private final AccountRepository accountRepository;
	private final TransactionRepository transactionRepository;

	public TransferService(BitGoService bitGoService, AccountRepository accountRepository,
			TransactionRepository transactionRepository) {
		this.bitGoService = bitGoService;
		this.accountRepository = accountRepository;
		this.transactionRepository = transactionRepository;
	}

	public void processNewTransfer(String webhookSignature, TransferWebhookNotificationDto transferRequest,
			String rawPayload) {
		// Verify the webhook notification
		if (!bitGoService.verifyWebhookNotification(transferRequest.getWebhook(), webhookSignature, rawPayload)) {
			throw new UnverifiedWebhookException(transferRequest.getWebhook());
		}
		// Get the transfer details
		BitGoGetTransferResponseDto getTransferResponse = bitGoService.getTransfer(transferRequest.getCoin(),
				transferRequest.getWallet(), transferRequest.getTransfer());
		// Create transaction confirmation webhook
		String confirmationWebhookId = bitGoService.createTransactionConfirmationWebhook(transferRequest.getCoin());
		// Save the transaction
		BitGoGetTransferResponseEntryDto localEntry = getTransferResponse.getEntries().get(0);
		BitGoGetTransferResponseEntryDto externalEntry = getTransferResponse.getEntries().get(1);
		String counterpartyAddress = externalEntry.getAddress();
		if (localEntry.getIsPayGo() == null || !localEntry.getIsPayGo()) {
			counterpartyAddress = localEntry.getAddress();
			localEntry = externalEntry;
		}
		Account account = accountRepository.findByAddress(localEntry.getAddress()).get();
		Transaction transaction = Transaction.builder().coin(getTransferResponse.getCoin())
				.amount(Double.parseDouble(localEntry.getValueString()) / 1000000)
				.counterpartyAddress(counterpartyAddress).confirmationWebhookId(confirmationWebhookId)
				.status(TransactionStatus.PENDING).account(account).createdAt(LocalDateTime.now()).build();
		transactionRepository.save(transaction);
	}

	@Transactional
	public void processTransferConfirmation(String webhookSignature, TransferWebhookNotificationDto transferRequest,
			String rawPayload) {
		// Verify the webhook notification
		if (!bitGoService.verifyWebhookNotification(transferRequest.getWebhook(), webhookSignature, rawPayload)) {
			throw new UnverifiedWebhookException(transferRequest.getWebhook());
		}
		// Update the transaction status to confirmed and credit the account
		String confirmationWebhookId = transferRequest.getWebhook();
		transactionRepository.findByConfirmationWebhookId(confirmationWebhookId).ifPresent(transaction -> {
			transaction.setStatus(TransactionStatus.CONFIRMED);
			Account account = transaction.getAccount();
			account.deposit(transaction.getAmount());
			accountRepository.save(account);
			transactionRepository.save(transaction);
		});
	}

}
