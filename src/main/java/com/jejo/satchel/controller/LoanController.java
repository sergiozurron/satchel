package com.jejo.satchel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jejo.satchel.dto.CustomLoanRequest;
import com.jejo.satchel.service.LoanService;

@RequestMapping("/api/v1/loans")
@RestController
public class LoanController {

	private final LoanService loanService;
	
	public LoanController(LoanService loanService) {
		this.loanService = loanService;
	}
	
	@PostMapping
	public ResponseEntity<Void> handleLoanRequest(@RequestBody CustomLoanRequest loanRequest){
		loanService.processLoanRequest(loanRequest);
		return ResponseEntity.ok(null);
	}
	
}
