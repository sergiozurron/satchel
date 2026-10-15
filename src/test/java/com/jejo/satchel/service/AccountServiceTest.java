package com.jejo.satchel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fireblocks.sdk.model.AmountInfo;
import com.jejo.satchel.dto.TransactionDetails;
import com.jejo.satchel.dto.WithdrawalRequest;
import com.jejo.satchel.exception.DepositWalletNotFoundByAssetIdException;
import com.jejo.satchel.exception.InsufficientFundsException;
import com.jejo.satchel.exception.SelfTransferException;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.FundsTransfer;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.FundsTransferRepository;
import com.jejo.satchel.util.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

	@Mock
	private CurrentUserProvider currentUserProvider;
	@Mock
	private FireblocksService assetCustodianService;
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

	private static final Long USER_ID = 1L;
	private static final String ASSET_ID = "USDC";

	@Test
	void initiateWithdrawal_throwsDepositWalletNotFound_whenUserHasNoWalletForAsset() {
		stubCurrentUser();
		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, ASSET_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> accountService.initiateWithdrawal(withdrawalRequest("0xdest", BigDecimal.ONE)))
				.isInstanceOf(DepositWalletNotFoundByAssetIdException.class);

		verify(depositWalletRepository, never()).findByAddressAndAssetId(any(), any());
		verify(assetCustodianService, never()).getPoolSize(any());
		verifyNoWithdrawalSideEffects();
	}

	@Test
	void initiateWithdrawal_throwsInsufficientFunds_whenAmountExceedsAvailableBalance() {
		DepositWallet source = stubSourceWallet(new BigDecimal("100"), BigDecimal.ZERO);

		assertThatThrownBy(() -> accountService.initiateWithdrawal(withdrawalRequest("0xdest", new BigDecimal("150"))))
				.isInstanceOf(InsufficientFundsException.class);

		assertThat(source.getBalance()).isEqualByComparingTo("100");
		verify(depositWalletRepository, never()).findByAddressAndAssetId(any(), any());
		verify(assetCustodianService, never()).getPoolSize(any());
		verifyNoWithdrawalSideEffects();
	}

	@Test
	void initiateWithdrawal_throwsInsufficientFunds_whenLockedBalanceReducesAvailableBelowAmount() {
		DepositWallet source = stubSourceWallet(new BigDecimal("100"), new BigDecimal("41"));

		assertThatThrownBy(() -> accountService.initiateWithdrawal(withdrawalRequest("0xdest", new BigDecimal("60"))))
				.isInstanceOf(InsufficientFundsException.class);

		assertThat(source.getBalance()).isEqualByComparingTo("100");
		verify(depositWalletRepository, never()).findByAddressAndAssetId(any(), any());
		verify(assetCustodianService, never()).getPoolSize(any());
		verifyNoWithdrawalSideEffects();
	}

	@Test
	void initiateWithdrawal_throwsSelfTransfer_whenDestinationIsOwnWallet() {
		User user = stubCurrentUser();
		DepositWallet ownWallet = wallet("0xown", user, new BigDecimal("100"), BigDecimal.ZERO);
		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, ASSET_ID)).thenReturn(Optional.of(ownWallet));
		when(depositWalletRepository.findByAddressAndAssetId("0xown", ASSET_ID)).thenReturn(Optional.of(ownWallet));

		assertThatThrownBy(() -> accountService.initiateWithdrawal(withdrawalRequest("0xown", new BigDecimal("10"))))
				.isInstanceOf(SelfTransferException.class);

		assertThat(ownWallet.getBalance()).isEqualByComparingTo("100");
		verify(assetCustodianService, never()).getPoolSize(any());
		verifyNoWithdrawalSideEffects();
	}

	@Test
	void initiateWithdrawal_movesBalancesAndRecordsCompletedTransfers_whenDestinationIsAnotherUser() {
		User user = stubCurrentUser();
		User otherUser = User.builder().id(2L).build();
		DepositWallet source = wallet("0xsource", user, new BigDecimal("100"), BigDecimal.ZERO);
		DepositWallet destination = wallet("0xdest", otherUser, new BigDecimal("10"), BigDecimal.ZERO);
		BigDecimal amount = new BigDecimal("25");
		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, ASSET_ID)).thenReturn(Optional.of(source));
		when(depositWalletRepository.findByAddressAndAssetId("0xdest", ASSET_ID))
				.thenReturn(Optional.of(destination));

		accountService.initiateWithdrawal(withdrawalRequest("0xdest", amount));

		assertThat(source.getBalance()).isEqualByComparingTo("75");
		assertThat(destination.getBalance()).isEqualByComparingTo("35");
		verify(depositWalletRepository).save(source);
		verify(depositWalletRepository).save(destination);
		verify(assetCustodianService, never()).getPoolSize(any());
		verify(assetCustodianService, never()).createTransaction(any(), any(), any(), any());

		ArgumentCaptor<FundsTransfer> captor = ArgumentCaptor.forClass(FundsTransfer.class);
		verify(fundsTransferRepository, times(2)).save(captor.capture());
		List<FundsTransfer> saved = captor.getAllValues();

		assertThat(saved.get(0).getDepositWallet()).isSameAs(source);
		assertThat(saved.get(0).getAmount()).isEqualByComparingTo(amount.negate());
		assertThat(saved.get(0).getCounterpartyAddress()).isEqualTo("0xdest");
		assertThat(saved.get(0).getIsCompleted()).isTrue();
		assertThat(saved.get(0).getTimestamp()).isNotNull();

		assertThat(saved.get(1).getDepositWallet()).isSameAs(destination);
		assertThat(saved.get(1).getAmount()).isEqualByComparingTo(amount);
		assertThat(saved.get(1).getCounterpartyAddress()).isEqualTo("0xsource");
		assertThat(saved.get(1).getIsCompleted()).isTrue();
		assertThat(saved.get(1).getTimestamp()).isNotNull();
	}

	@Test
	void initiateWithdrawal_recordsPendingWithdrawalWithoutDebiting_whenDestinationIsExternal() {
		User user = stubCurrentUser();
		DepositWallet source = wallet("0xsource", user, new BigDecimal("100"), BigDecimal.ZERO);
		BigDecimal amount = new BigDecimal("25");
		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, ASSET_ID)).thenReturn(Optional.of(source));
		when(depositWalletRepository.findByAddressAndAssetId("0xexternal", ASSET_ID)).thenReturn(Optional.empty());
		when(assetCustodianService.getPoolSize(ASSET_ID)).thenReturn(amount);

		accountService.initiateWithdrawal(withdrawalRequest("0xexternal", amount));

		assertThat(source.getBalance()).isEqualByComparingTo("100");
		verify(depositWalletRepository, never()).save(any());
		verify(assetCustodianService).getPoolSize(ASSET_ID);
		verify(assetCustodianService).createTransaction(eq(ASSET_ID), eq(amount), eq(USER_ID), eq("0xexternal"));

		ArgumentCaptor<FundsTransfer> captor = ArgumentCaptor.forClass(FundsTransfer.class);
		verify(fundsTransferRepository).save(captor.capture());
		FundsTransfer pending = captor.getValue();
		assertThat(pending.getDepositWallet()).isSameAs(source);
		assertThat(pending.getAmount()).isEqualByComparingTo(amount.negate());
		assertThat(pending.getCounterpartyAddress()).isEqualTo("0xexternal");
		assertThat(pending.getIsCompleted()).isFalse();
		assertThat(pending.getTransactionId()).isNull();
		assertThat(pending.getTimestamp()).isNotNull();
	}

	@Test
	void initiateWithdrawal_throwsInsufficientFunds_whenPoolIsBelowAmount() {
		DepositWallet source = stubSourceWallet(new BigDecimal("100"), BigDecimal.ZERO);
		when(depositWalletRepository.findByAddressAndAssetId("0xexternal", ASSET_ID)).thenReturn(Optional.empty());
		when(assetCustodianService.getPoolSize(ASSET_ID)).thenReturn(new BigDecimal("24.99"));

		assertThatThrownBy(() -> accountService.initiateWithdrawal(withdrawalRequest("0xexternal", new BigDecimal("25"))))
				.isInstanceOf(InsufficientFundsException.class);

		assertThat(source.getBalance()).isEqualByComparingTo("100");
		verify(assetCustodianService).getPoolSize(ASSET_ID);
		verifyNoWithdrawalSideEffects();
	}

	@Test
	void getAllUserFundsTransfers_shouldPreserveRepositoryOrder() {
		stubCurrentUser();
		FundsTransfer newer = FundsTransfer.builder().id(2L).amount(new BigDecimal("-10"))
				.timestamp(LocalDateTime.of(2026, 3, 2, 12, 0)).isCompleted(false).build();
		FundsTransfer older = FundsTransfer.builder().id(1L).amount(new BigDecimal("40"))
				.timestamp(LocalDateTime.of(2026, 1, 1, 8, 0)).isCompleted(true).build();
		when(fundsTransferRepository.findAllByDepositWallet_User_IdOrderByTimestampDesc(USER_ID))
				.thenReturn(List.of(newer, older));

		List<FundsTransfer> result = accountService.getAllUserFundsTransfers();

		assertThat(result).containsExactly(newer, older);
		verify(fundsTransferRepository).findAllByDepositWallet_User_IdOrderByTimestampDesc(USER_ID);
	}

	@Test
	void getAllUserFundsTransfers_shouldReturnEmptyList() {
		stubCurrentUser();
		when(fundsTransferRepository.findAllByDepositWallet_User_IdOrderByTimestampDesc(USER_ID))
				.thenReturn(List.of());

		List<FundsTransfer> result = accountService.getAllUserFundsTransfers();

		assertThat(result).isNotNull();
		assertThat(result).isEmpty();
		verify(fundsTransferRepository).findAllByDepositWallet_User_IdOrderByTimestampDesc(USER_ID);
	}

	@Test
	void getAllUserFundsTransfers_shouldQueryOnlyTheCurrentUser() {
		User other = User.builder().id(99L).build();
		when(currentUserProvider.getCurrentUser()).thenReturn(other);
		when(fundsTransferRepository.findAllByDepositWallet_User_IdOrderByTimestampDesc(99L)).thenReturn(List.of());

		accountService.getAllUserFundsTransfers();

		verify(fundsTransferRepository).findAllByDepositWallet_User_IdOrderByTimestampDesc(99L);
		verify(fundsTransferRepository, never()).findAllByDepositWallet_User_IdOrderByTimestampDesc(USER_ID);
	}

	private User stubCurrentUser() {
		User user = User.builder().id(USER_ID).build();
		when(currentUserProvider.getCurrentUser()).thenReturn(user);
		return user;
	}

	private DepositWallet stubSourceWallet(BigDecimal balance, BigDecimal lockedBalance) {
		User user = stubCurrentUser();
		DepositWallet source = wallet("0xsource", user, balance, lockedBalance);
		when(depositWalletRepository.findByUserIdAndAssetId(USER_ID, ASSET_ID)).thenReturn(Optional.of(source));
		return source;
	}

	private DepositWallet wallet(String address, User user, BigDecimal balance, BigDecimal lockedBalance) {
		DepositWallet wallet = new DepositWallet();
		wallet.setAddress(address);
		wallet.setAssetId(ASSET_ID);
		wallet.setUser(user);
		wallet.setBalance(balance);
		wallet.setLockedBalance(lockedBalance);
		return wallet;
	}

	private WithdrawalRequest withdrawalRequest(String destination, BigDecimal amount) {
		WithdrawalRequest request = new WithdrawalRequest();
		request.setAssetId(ASSET_ID);
		request.setDestinationAddress(destination);
		request.setAmount(amount);
		return request;
	}

	private void verifyNoWithdrawalSideEffects() {
		verify(depositWalletRepository, never()).save(any());
		verify(fundsTransferRepository, never()).save(any());
		verify(assetCustodianService, never()).createTransaction(any(), any(), any(), any());
	}

}
