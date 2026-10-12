package com.jejo.satchel.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.jejo.satchel.model.LoanStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanResponse {

	private Long id;
	private String loanAssetId;
	private BigDecimal amount;
	private BigDecimal returnedAmount;
	private BigDecimal outstandingAmount;
	private String collateralAssetId;
	private BigDecimal collateralAmount;
	private BigDecimal interestRate;
	private LoanStatus status;
	private BigDecimal accruedInterest;
	private LocalDateTime grantedAt;

}
