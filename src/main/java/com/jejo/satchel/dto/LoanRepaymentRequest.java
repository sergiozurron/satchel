package com.jejo.satchel.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanRepaymentRequest {
	
	@NotNull(message = "Loan ID is required")
	private Long loanId;
	
	@NotNull(message = "Repayment amount is required")
	@Positive(message = "Repayment amount must be positive")
	private BigDecimal amount;

}
