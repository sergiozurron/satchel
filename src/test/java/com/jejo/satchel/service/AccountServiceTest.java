package com.jejo.satchel.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fireblocks.sdk.model.AmountInfo;
import com.jejo.satchel.dto.TransactionDetails;
import com.jejo.satchel.exception.AccountsAlreadyCreatedException;
import com.jejo.satchel.model.Account;
import com.jejo.satchel.model.FundsTransfer;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.AccountRepository;
import com.jejo.satchel.repository.FundsTransferRepository;
import com.jejo.satchel.util.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

	@Mock
	private CurrentUserProvider currentUserProvider;
	@Mock
	private AssetCustodianService assetCustodianService;
	@Mock
	private AccountRepository accountRepository;
	@Mock
	private FundsTransferRepository fundsTransferRepository;
	@InjectMocks
	private AccountService accountService;

	private TransactionDetails txDetails;
    private Account destinationAccount;
    private Account sourceAccount;
    private final String omnibusAddress = "omnibus123";
    private final String transactionStatusCompleted = "COMPLETED";
    private final String transactionSubstatusConfirmed = "CONFIRMED";

    @BeforeEach
    void setUp() {
    	ReflectionTestUtils.setField(accountService, "omnibusAddress", omnibusAddress);
    	ReflectionTestUtils.setField(accountService, "transactionStatusCompleted", transactionStatusCompleted);
    	ReflectionTestUtils.setField(accountService, "transactionSubstatusConfirmed", transactionSubstatusConfirmed);
        txDetails = new TransactionDetails();
        txDetails.setId("tx123");
        txDetails.setDestinationAddress("destAddr");
        txDetails.setSourceAddress("sourceAddr");
        txDetails.setAssetId("USDC");
        AmountInfo amountInfo = new AmountInfo();
        amountInfo.setAmount("100.00");
        txDetails.setAmountInfo(amountInfo);
        txDetails.setStatus(transactionStatusCompleted);
        txDetails.setSubStatus(transactionSubstatusConfirmed);

        destinationAccount = new Account();
        destinationAccount.setAddress("destAddr");
        destinationAccount.setCoin("USDC");
        destinationAccount.setBalance(BigDecimal.ZERO);

        sourceAccount = new Account();
        sourceAccount.setAddress("sourceAddr");
        sourceAccount.setCoin("USDC");
        sourceAccount.setBalance(BigDecimal.ZERO);
    }
	
	@Test
	void createUserAccounts_ShouldCreateAccounts_WhenUserHasNoAccounts() {
		// Given
		User user = User.builder().id(1L).build();
		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(accountRepository.existsByUserId(user.getId())).thenReturn(false);
		when(assetCustodianService.createVaultAccount(accountService.collateralPrefix + user.getEmail())).thenReturn(1L);
		when(assetCustodianService.createVaultAccount(accountService.depositPrefix + user.getEmail())).thenReturn(2L);
		when(assetCustodianService.createWallet(any(), any())).thenReturn("");
		// When
		accountService.createUserAccounts();

		// Then
		verify(accountRepository, times(2)).save(any());
	}
	
	@Test
	void createUserAccounts_ShouldThrowException_WhenUserAlreadyHasAccounts() {
		// Given
		User user = User.builder().id(1L).build();
		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		when(accountRepository.existsByUserId(user.getId())).thenReturn(true);

		// When & Then
		assertThatThrownBy(() -> accountService.createUserAccounts())
			.isInstanceOf(AccountsAlreadyCreatedException.class);
	}
	
	@Test
    void processTransactionUpdate_shouldDoNothing_WhenDestinationIsOmnibusAddress() {
        txDetails.setDestinationAddress(omnibusAddress);

        accountService.processTransactionUpdate(txDetails);

        verify(accountRepository, never()).findByAddressAndCoin(any(), any());
        verify(fundsTransferRepository, never()).save(any());
    }

    @Test
    void processTransactionUpdate_shouldDoNothing_WhenStatusNotCompleted() {
        txDetails.setStatus("PENDING");

        accountService.processTransactionUpdate(txDetails);

        verify(accountRepository, never()).findByAddressAndCoin(any(), any());
        verify(fundsTransferRepository, never()).save(any());
    }

    @Test
    void processTransactionUpdate_shouldDoNothing_WhenSubStatusNotConfirmed() {
        txDetails.setSubStatus("PENDING");

        accountService.processTransactionUpdate(txDetails);

        verify(accountRepository, never()).findByAddressAndCoin(any(), any());
        verify(fundsTransferRepository, never()).save(any());
    }

    @Test
    void processTransactionUpdate_shouldDoNothing_WhenTransactionIdExists() {
        when(fundsTransferRepository.existsByTransactionId(txDetails.getId())).thenReturn(true);

        accountService.processTransactionUpdate(txDetails);

        verify(accountRepository, never()).findByAddressAndCoin(any(), any());
        verify(fundsTransferRepository, never()).save(any());
    }

    @Test
    void processTransactionUpdate_ShouldProcessBothAccounts_WhenValid() {
        when(accountRepository.findByAddressAndCoin("destAddr", "USDC")).thenReturn(Optional.of(destinationAccount));
        when(accountRepository.findByAddressAndCoin("sourceAddr", "USDC")).thenReturn(Optional.of(sourceAccount));
        when(fundsTransferRepository.save(any(FundsTransfer.class))).thenReturn(new FundsTransfer());
        when(accountRepository.save(any(Account.class))).thenReturn(new Account());

        accountService.processTransactionUpdate(txDetails);

        verify(accountRepository, times(1)).findByAddressAndCoin("destAddr", "USDC");
        verify(accountRepository, times(1)).findByAddressAndCoin("sourceAddr", "USDC");
        verify(fundsTransferRepository, times(2)).save(any(FundsTransfer.class));
        verify(accountRepository, times(2)).save(any(Account.class));
    }

    @Test
    void processTransactionUpdate_ShouldNotProcessIfDestinationAccountNotFound() {
        when(accountRepository.findByAddressAndCoin("destAddr", "USDC")).thenReturn(Optional.empty());
        when(accountRepository.findByAddressAndCoin("sourceAddr", "USDC")).thenReturn(Optional.of(sourceAccount));
        when(fundsTransferRepository.save(any(FundsTransfer.class))).thenReturn(new FundsTransfer());
        when(accountRepository.save(any(Account.class))).thenReturn(sourceAccount);

        accountService.processTransactionUpdate(txDetails);

        verify(accountRepository, times(1)).findByAddressAndCoin("destAddr", "USDC");
        verify(fundsTransferRepository, times(1)).save(any(FundsTransfer.class)); // Only source account processed
        verify(accountRepository, times(1)).save(sourceAccount);
    }

    @Test
    void processTransactionUpdate_ShouldNotProcessIfSourceAccountNotFound() {
        when(accountRepository.findByAddressAndCoin("destAddr", "USDC")).thenReturn(Optional.of(destinationAccount));
        when(accountRepository.findByAddressAndCoin("sourceAddr", "USDC")).thenReturn(Optional.empty());
        when(fundsTransferRepository.save(any(FundsTransfer.class))).thenReturn(new FundsTransfer());
        when(accountRepository.save(any(Account.class))).thenReturn(destinationAccount);

        accountService.processTransactionUpdate(txDetails);

        verify(accountRepository, times(1)).findByAddressAndCoin("sourceAddr", "USDC");
        verify(fundsTransferRepository, times(1)).save(any(FundsTransfer.class)); // Only destination account processed
        verify(accountRepository, times(1)).save(destinationAccount);
    }
	
}
