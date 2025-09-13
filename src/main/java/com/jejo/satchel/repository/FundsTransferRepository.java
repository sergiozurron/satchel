package com.jejo.satchel.repository;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jejo.satchel.model.Account;
import com.jejo.satchel.model.FundsTransfer;

@Repository
public interface FundsTransferRepository extends JpaRepository<FundsTransfer, Long> {

	public Optional<FundsTransfer> findByTransactionIdAndAccount(String transactionId, Account account);
	public Optional<FundsTransfer> findOneByAmount(BigDecimal amount);
	public Optional<FundsTransfer> findByTransactionId(String transactionId);
	public boolean existsByTransactionIdAndIsCompleted(String transactionId, Boolean isCompleted);
	public boolean existsByTransactionId(String transactionId);
}
