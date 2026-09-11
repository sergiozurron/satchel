package com.jejo.satchel.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanRepaymentResponse {
	
	private Long loanId;
	private BigDecimal repaymentAmount;
	private BigDecimal outstandingAmount;
	private BigDecimal totalRepaidAmount;
	private String loanStatus;
	private LocalDateTime repaymentDateTime;
	private String message;

}
