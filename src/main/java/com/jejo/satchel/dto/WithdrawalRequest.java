package com.jejo.satchel.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WithdrawalRequest {

	@NotBlank(message = "Destination address is required")
	private String destinationAddress;
	@Min(value = 0, message = "Amount must be greater than zero")
	private BigDecimal amount;
}
