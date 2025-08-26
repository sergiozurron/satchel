package com.jejo.satchel.dto;

import com.jejo.satchel.validator.ValidLtv;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class LoanRequest {
	
	@NotNull(message = "LTV is required")
    @ValidLtv
    private Integer ltv;

    @NotNull(message = "Collateral amount is required")
    @Positive(message = "Collateral amount must be positive")
    private Double collateralAmount;
    
    public Double getRawLtv() {
		return ltv == null ? null : ltv / 100.0;
    }
}
