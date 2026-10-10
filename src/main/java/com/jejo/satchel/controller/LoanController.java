package com.jejo.satchel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jejo.satchel.dto.CreateLoanRequest;
import com.jejo.satchel.dto.LoanRepaymentRequest;
import com.jejo.satchel.service.LoanService;

import jakarta.validation.Valid;

@RequestMapping("/api/v1/loans")
@RestController
public class LoanController {

	private final LoanService loanService;
	
	public LoanController(LoanService loanService) {
		this.loanService = loanService;
	}
	
	@PostMapping
	public ResponseEntity<Void> handleLoanRequest(@Valid @RequestBody CreateLoanRequest loanRequest){
		loanService.processLoanRequest(loanRequest);
		return ResponseEntity.ok(null);
	}
	
	@PutMapping
	public ResponseEntity<Void> repayLoan(@Valid @RequestBody LoanRepaymentRequest repaymentRequest) {
		loanService.repayLoan(repaymentRequest.getLoanId(), repaymentRequest.getAmount());
		return ResponseEntity.ok(null);
	}
	
}
