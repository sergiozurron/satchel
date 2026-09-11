package com.jejo.satchel.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CustomLoanRequest {
	
	@NotBlank(message = "Loan asset ID is required")
	private String loanAssetId;
	@NotNull(message = "LTV ratio is required")
	@Min(value = 0, message = "LTV ratio must be at least 0")
	@Max(value = 1, message = "LTV ratio must be at most 1")
	private BigDecimal ltv;
    @NotNull(message = "Collateral amount is required")
    @Positive(message = "Collateral amount must be positive")
    private BigDecimal collateralAmount;
	@NotBlank (message = "Collateral asset ID is required")
	@Pattern(regexp = "ETH_TEST5", message = "Unsupported collateral asset. Only ETH sepolia is allowed for now")
	private String collateralAssetId; // Only eth sepolia allowed for now
    @NotBlank(message = "Destination address is required")
    private String destinationAddress;
}
