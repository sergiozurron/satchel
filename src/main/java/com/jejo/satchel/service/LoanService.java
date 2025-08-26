package com.jejo.satchel.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.jejo.satchel.dto.LoanRequest;
import com.jejo.satchel.model.Loan;
import com.jejo.satchel.repository.LoanRepository;
import com.jejo.satchel.util.CurrentUserProvider;

@Service
public class LoanService {

	private final CurrentUserProvider currentUserProvider;
	private final LoanRepository loanRepository;

	public LoanService(CurrentUserProvider currentUserProvider, LoanRepository loanRepository) {
		this.currentUserProvider = currentUserProvider;
		this.loanRepository = loanRepository;
	}

	public void createLoan(LoanRequest loanRequest) {
		// Set status to ACTIVE if there is sufficient funds, otherwise PENDING
//		System.out.println("Unlocked USDC: " + bitcoinService.convertUsdToBtc(loanRequest.getCollateralAmount() * (loanRequest.getLtv() / 100)));
		Loan loan = Loan.builder().collateralAmount(loanRequest.getCollateralAmount()).ltv(loanRequest.getLtv())
				.amount(0.0) // Initial amount is 0, will be updated later
				.interestRate(8.0) // Default interest rate, can be parameterized
				.accruedInterest(0.0) // Initial accrued interest is 0
				.requestedAt(LocalDateTime.now()).user(currentUserProvider.getCurrentUser()).build();
		loanRepository.save(loan);
	}

}
