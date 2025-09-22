package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import com.jejo.satchel.dto.CustomLoanRequest;
import com.jejo.satchel.exception.ExcesiveEquivalentAmountException;
import com.jejo.satchel.exception.InsufficientCollateralException;
import com.jejo.satchel.model.AccountType;
import com.jejo.satchel.model.Loan;
import com.jejo.satchel.model.LoanStatus;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.AccountRepository;
import com.jejo.satchel.repository.LoanRepository;
import com.jejo.satchel.util.CurrentUserProvider;

import jakarta.transaction.Transactional;

@Service
public class LoanService {

	@Value("${custodian.account.collateral.coin}")
	public String collateralCoin;

	private final CurrentUserProvider currentUserProvider;
	private final LoanRepository loanRepository;
	private final AccountRepository accountRepository;
	private final AssetCustodianService assetCustodianService;
	private final BitcoinPriceService bitcoinPriceService;

	private final TaskScheduler taskScheduler;

	public LoanService(CurrentUserProvider currentUserProvider, LoanRepository loanRepository,
			AccountRepository accountRepository, AssetCustodianService assetCustodianService,
			BitcoinPriceService bitcoinPriceService, TaskScheduler taskScheduler) {
		this.currentUserProvider = currentUserProvider;
		this.loanRepository = loanRepository;
		this.accountRepository = accountRepository;
		this.assetCustodianService = assetCustodianService;
		this.bitcoinPriceService = bitcoinPriceService;
		this.taskScheduler = taskScheduler;
	}

	@Transactional
	public void processLoanRequest(CustomLoanRequest loanRequest) {
		User currentUser = currentUserProvider.getCurrentUser();
		accountRepository.findByUserIdAndType(currentUser.getId(), AccountType.COLLATERAL)
				.ifPresent(account -> {
					if (loanRequest.getCollateralAmount()
							.compareTo(account.getAvailableBalance()) > 0) {
						throw new InsufficientCollateralException();
					}
					BigDecimal loanAmount = bitcoinPriceService
							.convertBtcToUsdc(loanRequest.getCollateralAmount())
							.multiply(loanRequest.getLtv());
					BigDecimal omnibusBalance = assetCustodianService.getOmnibusBalance();
					if (omnibusBalance.compareTo(loanAmount) < 0) {
						throw new ExcesiveEquivalentAmountException(omnibusBalance);
					}
					account.setLockedBalance(loanRequest.getCollateralAmount());
					accountRepository.save(account);
					LocalDateTime grantedAt = LocalDateTime.now();
					loanRepository.save(Loan.builder().returnedAmount(BigDecimal.ZERO)
							.amount(loanAmount).collateralAmount(loanRequest.getCollateralAmount())
							.ltv(loanRequest.getLtv()).interestRate(loanRequest.getInterestRate())
							.term(loanRequest.getTerm()).status(LoanStatus.ACTIVE)
							.accruedInterest(loanAmount.multiply(loanRequest.getInterestRate()))
							.grantedAt(grantedAt).user(currentUser).build());
					scheduleLoanClosingAt(grantedAt, loanRequest.getTerm());
					assetCustodianService.createTransactionFromOmnibus(
							assetCustodianService.omnibusCoin, loanRequest.getDestinationAddress(),
							loanAmount);
				});
	}

	void scheduleLoanClosingAt(LocalDateTime loanGrantedAtDateTime, Integer loanTerm) {
		taskScheduler.schedule(() -> {
			loanRepository.findAllByGrantedAtAndStatus(loanGrantedAtDateTime, LoanStatus.ACTIVE)
					.forEach(loan -> {
						loan.setStatus(LoanStatus.CLOSED);
					});
		}, loanGrantedAtDateTime.plusHours(loanTerm).atZone(ZoneId.systemDefault()).toInstant());
	}

}
