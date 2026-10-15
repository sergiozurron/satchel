package com.jejo.satchel.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "loans")
public class Loan {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false)
	private String loanAssetId; // Loan asset ID
	@Column(nullable = false, precision = 38, scale = 7)
	private BigDecimal returnedAmount;
	@Column(nullable = false, precision = 38, scale = 7)
	private BigDecimal amount; // Granted amount
	@Column(nullable = false)
	private String collateralAssetId; // Collateral asset ID
	@Column(nullable = false, precision = 38, scale = 7)
	private BigDecimal collateralAmount; // Collateral amount locked at origination
	@Column(nullable = false, precision = 38, scale = 7)
	private BigDecimal currentCollateral; // Collateral still pledged after liquidations and
											// repayments
	@Column(nullable = false, precision = 10, scale = 6)
	private BigDecimal interestRate; // Yearly interest rate in percentage
	@Column(nullable = false)
	private BigDecimal accruedInterest; // Accrued interest
	@Column(nullable = false)
	private LocalDateTime grantedAt;
	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;

	public BigDecimal getReturnedShare() {
		return returnedAmount.divide(amount.add(accruedInterest), 6, RoundingMode.FLOOR);
	}

	public BigDecimal getOutstandingAmount() {
		return amount.add(accruedInterest).subtract(returnedAmount);
	}

	public void returnAmount(BigDecimal amount) {
		this.returnedAmount = this.returnedAmount.add(amount);
	}

	public BigDecimal releaseCollateral(BigDecimal repayment) {
		BigDecimal outstanding = getOutstandingAmount();
		BigDecimal released = currentCollateral.multiply(repayment).divide(outstanding, 7,
				RoundingMode.DOWN);
		currentCollateral = currentCollateral.subtract(released);
		return released;
	}

	public void liquidate(BigDecimal collateralSold, BigDecimal proceeds) {
		currentCollateral = currentCollateral.subtract(collateralSold);
		returnAmount(proceeds);
	}

	public boolean isPaid() {
		return getOutstandingAmount().compareTo(BigDecimal.ZERO) <= 0;
	}

}
