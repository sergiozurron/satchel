package com.jejo.satchel.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "accounts")
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false)
	private String address;
	@Column(nullable = false)
	private String coin;
	@Column(nullable = false, precision = 38, scale = 6)
	private BigDecimal balance;
	@Column(nullable = false, precision = 38, scale = 6)
	private BigDecimal lockedBalance;
	@Column(nullable = false)
	private LocalDateTime openedAt;
	@Column(nullable = false)
	private Long vaultAccountId;
	@Enumerated(EnumType.STRING)
	@JdbcType(value = PostgreSQLEnumJdbcType.class)
	@Column(nullable = false)
	private AccountType type;
	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;
	
	public Long daysSinceOpened() {
		return ChronoUnit.DAYS.between(openedAt.toLocalDate(), LocalDateTime.now().toLocalDate());
	}
	
	public BigDecimal availableBalance() {
		return balance.subtract(lockedBalance);
	}
	
	public void deposit(BigDecimal amount) {
		this.balance = this.balance.add(amount);
	}

	public void withdraw(BigDecimal amount) {
		this.balance = this.balance.subtract(amount);
	}
	
}
