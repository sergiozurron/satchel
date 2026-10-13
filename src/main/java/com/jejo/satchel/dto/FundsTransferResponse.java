package com.jejo.satchel.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundsTransferResponse {

	private Long id;
	private BigDecimal amount;
	private LocalDateTime timestamp;
	private String transactionId;
	private String counterpartyAddress;
	@JsonProperty("isCompleted")
	private Boolean isCompleted;
	private Long walletId;
	private String assetId;

}
