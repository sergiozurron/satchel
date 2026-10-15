package com.jejo.satchel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jejo.satchel.model.Loan;

public interface LoanRepository extends JpaRepository<Loan, Long> {
	@Query("select loan from Loan loan where loan.isPaid = :isPaid")
	public List<Loan> findAllByIsPaid(@Param("isPaid") boolean isPaid);
	public List<Loan> findAllByUserIdOrderByGrantedAtDesc(Long userId);
	public Optional<Loan> findByIdAndUserId(Long loanId, Long userId);
}
