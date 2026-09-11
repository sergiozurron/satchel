package com.jejo.satchel.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jejo.satchel.model.Loan;
import com.jejo.satchel.model.LoanStatus;

public interface LoanRepository extends JpaRepository<Loan, Long> {
	public List<Loan> findAllByStatus(LoanStatus status);
	public List<Loan> findAllByUserIdAndStatusOrderByInterestRateDesc(Long userId, LoanStatus status);
	public List<Loan> findAllByGrantedAtAndStatus(LocalDateTime grantedAt, LoanStatus status);
}
