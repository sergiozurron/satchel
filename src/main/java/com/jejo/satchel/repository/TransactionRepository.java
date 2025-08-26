package com.jejo.satchel.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jejo.satchel.model.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
	
	public Optional<Transaction> findByConfirmationWebhookId(String confirmationWebhookId);
}
