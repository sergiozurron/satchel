package com.jejo.satchel.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VaultAccountBalanceUpdate {
	
	private Long vaultAccountId;
	private String assetId;
	private BigDecimal total; // New total balance
}
