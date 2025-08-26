package com.jejo.satchel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jejo.satchel.dto.LoanRequest;
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
	 public ResponseEntity<Void> createLoan(@Valid @RequestBody LoanRequest loanRequest) {
	     loanService.createLoan(loanRequest);
	     return ResponseEntity.ok().build();
	 }

}
