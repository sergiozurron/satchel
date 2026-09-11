package com.jejo.satchel.controller;

import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jejo.satchel.dto.CustomLoanRequest;
import com.jejo.satchel.dto.LoanRepaymentRequest;
import com.jejo.satchel.dto.LoanRepaymentResponse;
import com.jejo.satchel.dto.TransactionDetails;
import com.jejo.satchel.dto.WebhookNotification;
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
	
	@PostMapping
	public ResponseEntity<Void> handleLoanRequest(@RequestBody CustomLoanRequest loanRequest){
		loanService.processLoanRequest(loanRequest);
		return ResponseEntity.ok(null);
	}
	
	@PostMapping("/repayment")
	public ResponseEntity<LoanRepaymentResponse> repayLoan(@Valid @RequestBody LoanRepaymentRequest repaymentRequest) {
		Loan loan = loanService.repayLoan(repaymentRequest.getLoanId(), repaymentRequest.getAmount());
		
		LoanRepaymentResponse response = LoanRepaymentResponse.builder()
				.loanId(loan.getId())
				.repaymentAmount(repaymentRequest.getAmount())
				.outstandingAmount(loan.getOutstandingAmount())
				.totalRepaidAmount(loan.getReturnedAmount())
				.loanStatus(loan.getStatus().toString())
				.repaymentDateTime(LocalDateTime.now())
				.message(loan.getStatus().toString().equals("PAID") 
					? "Loan repaid successfully" 
					: "Partial repayment successful")
				.build();
		
		return ResponseEntity.ok(response);
	}
	
}
