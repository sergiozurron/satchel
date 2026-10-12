package com.jejo.satchel.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jejo.satchel.dto.CreateLoanRequest;
import com.jejo.satchel.dto.LoanRepaymentRequest;
import com.jejo.satchel.dto.LoanResponse;
import com.jejo.satchel.dto.Response;
import com.jejo.satchel.model.Loan;
import com.jejo.satchel.service.LoanService;

import jakarta.validation.Valid;

@RequestMapping("/api/v1/loans")
@RestController
public class LoanController {

	private final LoanService loanService;
	
	public LoanController(LoanService loanService) {
		this.loanService = loanService;
	}

	@GetMapping
	public ResponseEntity<List<LoanResponse>> getAllUserLoans() {
		return ResponseEntity.ok(loanService.getAllUserLoans());
	}
	
	@PostMapping
	public ResponseEntity<Response> handleLoanRequest(@Valid @RequestBody CreateLoanRequest loanRequest){
		Long loanId = loanService.processLoanRequest(loanRequest);
		return ResponseEntity.ok(new Response("Created active loan with id: " + loanId));
	}
	
	@PutMapping
	public ResponseEntity<LoanResponse> repayLoan(@Valid @RequestBody LoanRepaymentRequest repaymentRequest) {
		Loan updatedLoan = loanService.repayLoan(repaymentRequest.getLoanId(), repaymentRequest.getAmount());
		return ResponseEntity.ok(loanService.mapToLoanResponse(updatedLoan));
	}
	
}
