package com.jejo.satchel.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "transactions")
public class Transaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false)
	private String coin;
	@Column(nullable = false)
	private Double amount;
	@Column(nullable = false)
	private String counterpartyAddress;
	@Column(nullable = false, unique = true)
	private String confirmationWebhookId;
	@Enumerated(EnumType.STRING)
	@JdbcType(value = PostgreSQLEnumJdbcType.class)
	@Column(nullable = false)
	private TransactionStatus status;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@ManyToOne
	@JoinColumn(name = "account_id", nullable = false)
	private DepositAccount account;
}
