package com.jejo.satchel.model;

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
	private Double amount; // Outstanding loan amount
	@Column(nullable = false)
	private Double collateralAmount; // Collateral amount in BTC
	@Column(nullable = false)
	private Integer ltv; // Loan-to-value ratio (percentage)
	@Column(nullable = false)
	private Double interestRate; // Yearly interest rate in percentage
	@Builder.Default
	@Column(nullable = false)
	private Integer term = 30; // Default term is 30 days
	@Builder.Default
	@Enumerated(EnumType.STRING)
	@JdbcType(value = PostgreSQLEnumJdbcType.class)
	@Column(nullable = false)
	private LoanStatus status = LoanStatus.PENDING; // Default status is PENDING
	@Column(nullable = false)
	private Double accruedInterest; // Accrued interest
	@Column(nullable = false)
	private LocalDateTime requestedAt;
	private LocalDateTime grantedAt; // When the loan was granted
	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;
	
	public boolean isDue() {
		if (status == LoanStatus.ACTIVE) {
			LocalDateTime dueDate = requestedAt.plusDays(term);
			return LocalDateTime.now().isAfter(dueDate);
		}
		return false;
	}

}
