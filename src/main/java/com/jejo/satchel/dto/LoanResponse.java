package com.jejo.satchel.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
	private BigDecimal currentCollateral;
	private BigDecimal interestRate;
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private boolean isPaid;
	private BigDecimal accruedInterest;
	private LocalDateTime grantedAt;

	@JsonProperty("isPaid")
	public boolean isPaid() {
		return isPaid;
	}

	@JsonProperty("isPaid")
	public void setPaid(boolean isPaid) {
		this.isPaid = isPaid;
	}

}
