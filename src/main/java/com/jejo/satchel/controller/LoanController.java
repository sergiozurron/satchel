package com.jejo.satchel.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jejo.satchel.dto.CreateLoanRequest;
import com.jejo.satchel.dto.LoanRepaymentRequest;
import com.jejo.satchel.dto.LoanResponse;
import com.jejo.satchel.mapper.LoanMapper;
import com.jejo.satchel.model.Loan;
import com.jejo.satchel.service.LoanService;

import jakarta.validation.Valid;

@RequestMapping("/api/v1/loans")
@RestController
public class LoanController {

	private final LoanService loanService;
	private final LoanMapper loanMapper;
	
	public LoanController(LoanService loanService, LoanMapper loanMapper) {
		this.loanService = loanService;
		this.loanMapper = loanMapper;
	}

	@GetMapping
	public ResponseEntity<List<LoanResponse>> getAllUserLoans() {
		return ResponseEntity.ok(loanMapper.toLoanResponses(loanService.getAllUserLoans()));
	}

	@GetMapping("/{id}")
	public ResponseEntity<LoanResponse> getUserLoan(@PathVariable Long id) {
		return ResponseEntity.ok(loanMapper.toLoanResponse(loanService.getUserLoan(id)));
	}
	
	@PostMapping
	public ResponseEntity<LoanResponse> handleLoanRequest(@Valid @RequestBody CreateLoanRequest loanRequest) {
		Loan createdLoan = loanService.processLoanRequest(loanRequest);
		return ResponseEntity.ok(loanMapper.toLoanResponse(createdLoan));
	}
	
	@PatchMapping("/{id}")
	public ResponseEntity<LoanResponse> repayLoan(@PathVariable Long id,
			@Valid @RequestBody LoanRepaymentRequest repaymentRequest) {
		Loan updatedLoan = loanService.repayLoan(id, repaymentRequest.getAmount());
		return ResponseEntity.ok(loanMapper.toLoanResponse(updatedLoan));
	}
	
}
