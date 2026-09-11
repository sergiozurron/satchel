package com.jejo.satchel.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
	private BigDecimal collateralAmount; // Collateral amount
	@Column(nullable = false, precision = 6, scale = 4)
	private BigDecimal ltv; // Loan-to-value ratio (percentage)
	@Column(nullable = false, precision = 10, scale = 6)
	private BigDecimal interestRate; // Yearly interest rate in percentage
	@Enumerated(EnumType.STRING)
	@JdbcType(value = PostgreSQLEnumJdbcType.class)
	@Column(nullable = false)
	private LoanStatus status;
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
		return this.amount.subtract(this.returnedAmount);
	}
	
	public void returnAmount(BigDecimal amount) {
		this.returnedAmount = this.returnedAmount.add(amount);
	}
	
	public boolean isPaidOut() {
		return getOutstandingAmount().compareTo(BigDecimal.ZERO) == 0;
	}

}
