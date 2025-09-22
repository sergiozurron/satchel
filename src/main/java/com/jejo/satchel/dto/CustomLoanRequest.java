package com.jejo.satchel.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CustomLoanRequest {
	
	@NotNull(message = "LTV ratio is required")
	@Min(value = 0, message = "LTV ratio must be at least 0")
	@Max(value = 1, message = "LTV ratio must be at most 1")
	private BigDecimal ltv;
	@NotNull(message = "Term is required")
	@Positive(message = "Term must be positive")
	@Max(value = 8760, message = "Term must be at most 12 months")
	private Integer term;
	@NotNull(message = "Interest rate is required")
	@Positive(message = "Interest rate must be positive")
	private BigDecimal interestRate;
    @NotNull(message = "Collateral amount is required")
    @Positive(message = "Collateral amount must be positive")
    private BigDecimal collateralAmount;
    @NotBlank(message = "Destination address is required")
    private String destinationAddress;
}
