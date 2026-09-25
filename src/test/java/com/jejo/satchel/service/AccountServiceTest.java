package com.jejo.satchel.service;

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
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.FundsTransfer;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.FundsTransferRepository;
import com.jejo.satchel.util.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

	@Mock
	private CurrentUserProvider currentUserProvider;
	@Mock
	private AssetCustodianService assetCustodianService;
	@Mock
	private DepositWalletRepository depositWalletRepository;
	@Mock
	private FundsTransferRepository fundsTransferRepository;
	@InjectMocks
	private AccountService accountService;

	private TransactionDetails txDetails;
    private DepositWallet destinationAccount;
    private DepositWallet sourceAccount;
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
        txDetails.setStatus(transactionStatusCompleted);
        txDetails.setSubStatus(transactionSubstatusConfirmed);
        
        AmountInfo amountInfo = new AmountInfo();
        amountInfo.setAmount("100.00");
        txDetails.setAmountInfo(amountInfo);
        
        destinationAccount = new DepositWallet();
        destinationAccount.setAddress("destAddr");
        destinationAccount.setAssetId("USDC");
        destinationAccount.setBalance(BigDecimal.ZERO);

        sourceAccount = new DepositWallet();
        sourceAccount.setAddress("sourceAddr");
        sourceAccount.setAssetId("USDC");
        sourceAccount.setBalance(BigDecimal.ZERO);
    }
	
	@Test
    void processTransactionUpdate_shouldDoNothing_WhenDestinationIsOmnibusAddress() {
        txDetails.setDestinationAddress(omnibusAddress);

        accountService.processTransactionUpdate(txDetails);

        verify(depositWalletRepository, never()).findByAddressAndAssetId(any(), any());
        verify(fundsTransferRepository, never()).save(any());
    }

    @Test
    void processTransactionUpdate_shouldDoNothing_WhenStatusNotCompleted() {
        txDetails.setStatus("PENDING");

        accountService.processTransactionUpdate(txDetails);

        verify(depositWalletRepository, never()).findByAddressAndAssetId(any(), any());
        verify(fundsTransferRepository, never()).save(any());
    }

    @Test
    void processTransactionUpdate_shouldDoNothing_WhenSubStatusNotConfirmed() {
        txDetails.setSubStatus("PENDING");

        accountService.processTransactionUpdate(txDetails);

        verify(depositWalletRepository, never()).findByAddressAndAssetId(any(), any());
        verify(fundsTransferRepository, never()).save(any());
    }

}
