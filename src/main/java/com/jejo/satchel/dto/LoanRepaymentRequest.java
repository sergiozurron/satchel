package com.jejo.satchel.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
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
	@DecimalMin(value = "0.0001", inclusive = false, message = "Repayment amount must be greater than 0")
	private BigDecimal amount;

}
