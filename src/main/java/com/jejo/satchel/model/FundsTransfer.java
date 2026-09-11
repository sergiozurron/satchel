package com.jejo.satchel.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "funds_transfers")
public class FundsTransfer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false, precision = 38, scale = 7)
	private BigDecimal amount; // Positive for deposits, negative for withdrawals
	@Column(nullable = false)
	private LocalDateTime timestamp;
	private String transactionId;
	@Column(nullable = false)
	private String counterpartyAddress; // Address of the other party in the transfer
	@Column(nullable = false)
	private Boolean isCompleted;
	@ManyToOne
	@JoinColumn(name = "account_id")
	private DepositWallet account;
}
