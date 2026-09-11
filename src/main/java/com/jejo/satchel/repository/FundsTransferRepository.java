package com.jejo.satchel.repository;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.FundsTransfer;

public interface FundsTransferRepository extends JpaRepository<FundsTransfer, Long> {

	public Optional<FundsTransfer> findByTransactionIdAndAccount(String transactionId, DepositWallet account);
	public Optional<FundsTransfer> findOneByAmount(BigDecimal amount);
	public Optional<FundsTransfer> findByTransactionId(String transactionId);
	public boolean existsByTransactionIdAndIsCompleted(String transactionId, Boolean isCompleted);
	public boolean existsByTransactionId(String transactionId);
}
