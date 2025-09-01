package com.jejo.satchel.model;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
@Table(name = "deposit_accounts")
public class DepositAccount {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false, unique = true)
	private String address;
	@Column(nullable = false)
	private String coin;
	@Column(nullable = false)
	private Double balance;
	@Column(nullable = false)
	private Double averageBalance;
	@Column(nullable = false)
	private LocalDateTime openedAt;
	@OneToOne
	@JoinColumn(name = "user_id")
	private User user;
	
	public void deposit(Double amount) {
		this.balance += amount;
	}
	
	public Long daysSinceOpened() {
		return ChronoUnit.DAYS.between(openedAt.toLocalDate(), LocalDateTime.now().toLocalDate());
	}

}
