package com.jejo.satchel.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "deposit_wallets")
public class DepositWallet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false)
	private String address;
	@Column(nullable = false)
	private String assetId;
	@Column(nullable = false, precision = 38, scale = 7)
	private BigDecimal balance;
	@Column(nullable = false, precision = 38, scale = 7)
	private BigDecimal lockedBalance;
	@Column(nullable = false)
	private LocalDateTime openedAt;
	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;
	
	public Long daysSinceOpened() {
		return ChronoUnit.DAYS.between(openedAt.toLocalDate(), LocalDateTime.now().toLocalDate());
	}
	
	public BigDecimal getAvailableBalance() {
		return balance.subtract(lockedBalance);
	}
	
	public void deposit(BigDecimal amount) {
		this.balance = this.balance.add(amount);
	}

	public void withdraw(BigDecimal amount) {
		this.balance = this.balance.subtract(amount);
	}
	
}
