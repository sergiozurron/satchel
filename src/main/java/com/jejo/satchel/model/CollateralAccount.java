package com.jejo.satchel.model;

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
@Table(name = "collateral_accounts")
public class CollateralAccount {

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
	private Double lockedBalance;
	@OneToOne
	@JoinColumn(name = "user_id", nullable = false)
	private User user;
	
	public void deposit(Double amount) {
		this.balance += amount;
	}
	
	public void lockAmount(Double amount) {
		this.lockedBalance += amount;
	}
	
	public double availableBalance() {
		return this.balance - this.lockedBalance;
	}
	
}
