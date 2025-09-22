package com.jejo.satchel.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jejo.satchel.model.Loan;
import com.jejo.satchel.model.LoanStatus;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

	public List<Loan> findAllByGrantedAtAndStatus(LocalDateTime grantedAt, LoanStatus status);
}
