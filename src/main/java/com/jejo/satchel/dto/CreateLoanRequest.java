package com.jejo.satchel.dto;

import java.math.BigDecimal;

import com.jejo.satchel.validator.ValidAssetId;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateLoanRequest {
	
	@NotBlank(message = "Loan asset ID is required")
	@ValidAssetId
	private String loanAssetId;
    @NotNull(message = "Collateral amount is required")
    @Positive(message = "Collateral amount must be positive")
    private BigDecimal collateralAmount;
	@NotBlank (message = "Collateral asset ID is required")
	@ValidAssetId
	private String collateralAssetId;

}
