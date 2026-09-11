package com.jejo.satchel.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fireblocks.sdk.model.DestinationTransferPeerPath;
import com.fireblocks.sdk.model.SourceTransferPeerPath;
import com.fireblocks.sdk.model.TransferPeerPathType;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.FundsTransfer;
import com.jejo.satchel.model.User;
import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.FundsTransferRepository;
import com.jejo.satchel.repository.UserRepository;
import com.jejo.satchel.service.AssetCustodianService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class AccountControllerFullIntTest {

	@Autowired
	private FundsTransferRepository fundsTransferRepository;
	@Autowired
	private DepositWalletRepository accountRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private AssetCustodianService assetCustodianService;

	private User user;

	@BeforeEach
	void setUp() {
		Long random = System.currentTimeMillis() % 100000;
		user = userRepository.save(User.builder().firstName("John").lastName("Doe")
				.email("email@email" + random).password("pass").verified(true).build());
	}

	@AfterEach
	void tearDown() {
		fundsTransferRepository.deleteAll();
		accountRepository.deleteAll();
		userRepository.deleteAll();
	}

	// Requires manual steps to trigger webhook from Fireblocks
	@Test
	void handleTransactionStatusUpdatedWebhook_shouldReturnOkAndCreditDestinationAccount_WhenDestinationAddressCorrespondsToAccount()
			throws Exception {
		String coin = "USDC_ETH_TEST5_AN74";
		String destinationAddress = "0xCa3d51259D32Cf715cDcfFAa16D065ECf6CBC4Ec";
		DepositWallet destinationAccount = accountRepository.save(DepositWallet.builder().user(user)
				.address(destinationAddress).assetId(coin)
				.balance(BigDecimal.ZERO).lockedBalance(BigDecimal.ZERO)
				.openedAt(LocalDateTime.now()).build());

		// Trigger webhook notification
		String txId = assetCustodianService.createTransaction(coin,
				new SourceTransferPeerPath().id("50").type(TransferPeerPathType.VAULT_ACCOUNT),
				new DestinationTransferPeerPath().id("53").type(TransferPeerPathType.VAULT_ACCOUNT),
				new BigDecimal("0.001"));

		Thread.sleep(60000); // Wait for async processing (increase if needed)

		destinationAccount = accountRepository.findById(destinationAccount.getId()).orElse(null);
		assertThat(destinationAccount).isNotNull();
		assertThat(destinationAccount.getBalance()).isEqualByComparingTo(new BigDecimal("0.001"));
		FundsTransfer fundsTransfer = fundsTransferRepository
				.findByTransactionIdAndAccount(txId, destinationAccount).orElse(null);
		assertThat(fundsTransfer).isNotNull();
		assertThat(fundsTransfer.getAmount()).isEqualByComparingTo(new BigDecimal("0.001"));
	}

	@Test
	void handleTransactionStatusUpdatedWebhook_shouldReturnOkAndDebitSourceAccount_WhenSourceAddressCorrespondsToAccount()
			throws Exception {
		String coin = "USDC_ETH_TEST5_AN74";
		String sourceAddress = "0xCa3d51259D32Cf715cDcfFAa16D065ECf6CBC4Ec";
		DepositWallet sourceAccount = accountRepository.save(DepositWallet.builder().user(user)
				.address(sourceAddress).assetId(coin).balance(BigDecimal.ONE)
				.lockedBalance(BigDecimal.ZERO).openedAt(LocalDateTime.now())
				.build());

		// Trigger webhook notification
		String txId = assetCustodianService.createTransaction(coin,
				new SourceTransferPeerPath().id("53").type(TransferPeerPathType.VAULT_ACCOUNT),
				new DestinationTransferPeerPath().id("50").type(TransferPeerPathType.VAULT_ACCOUNT),
				new BigDecimal("0.001"));

		Thread.sleep(60000); // Wait for async processing (increase if needed)

		sourceAccount = accountRepository.findById(sourceAccount.getId()).orElse(null);
		assertThat(sourceAccount).isNotNull();
		assertThat(sourceAccount.getBalance()).isEqualByComparingTo(new BigDecimal("0.999"));
		FundsTransfer fundsTransfer = fundsTransferRepository
				.findByTransactionIdAndAccount(txId, sourceAccount).orElse(null);
		assertThat(fundsTransfer).isNotNull();
		assertThat(fundsTransfer.getAmount()).isEqualByComparingTo(new BigDecimal("-0.001"));
	}

	@Test
	void handleTransactionStatusUpdatedWebhook_shouldReturnOkAndCreditBothAccounts_WhenBothAddressesCorrespondToAccounts()
			throws Exception {
		User secondUser = userRepository.save(User.builder().firstName("Jane").lastName("Doe")
				.email("email2@email" + System.currentTimeMillis() % 100000).password("pass")
				.verified(true).build());
		String coin = "USDC_ETH_TEST5_AN74";
		String sourceAddress = "0xCa3d51259D32Cf715cDcfFAa16D065ECf6CBC4Ec";
		String destinationAddress = "0x8DAb822DB3E88E65a14caEcf473f70AfAD7f675e";
		DepositWallet sourceAccount = accountRepository.save(DepositWallet.builder().user(user)
				.address(sourceAddress).assetId(coin).balance(BigDecimal.ONE)
				.lockedBalance(BigDecimal.ZERO).openedAt(LocalDateTime.now())
				.build());
		DepositWallet destinationAccount = accountRepository.save(DepositWallet.builder().user(secondUser)
				.address(destinationAddress).assetId(coin)
				.balance(BigDecimal.ZERO).lockedBalance(BigDecimal.ZERO)
				.openedAt(LocalDateTime.now()).build());

		// Trigger webhook notification
		String txId = assetCustodianService.createTransaction(coin,
				new SourceTransferPeerPath().id("53").type(TransferPeerPathType.VAULT_ACCOUNT),
				new DestinationTransferPeerPath().id("54").type(TransferPeerPathType.VAULT_ACCOUNT),
				new BigDecimal("0.001"));

		Thread.sleep(60000); // Wait for async processing (increase if needed)

		sourceAccount = accountRepository.findById(sourceAccount.getId()).orElse(null);
		assertThat(sourceAccount).isNotNull();
		assertThat(sourceAccount.getBalance()).isEqualByComparingTo(new BigDecimal("0.999"));
		destinationAccount = accountRepository.findById(destinationAccount.getId()).orElse(null);
		assertThat(destinationAccount).isNotNull();
		assertThat(destinationAccount.getBalance()).isEqualByComparingTo(new BigDecimal("0.001"));
		FundsTransfer fundsTransfer = fundsTransferRepository
				.findByTransactionIdAndAccount(txId, sourceAccount).orElse(null);
		assertThat(fundsTransfer).isNotNull();
		assertThat(fundsTransfer.getAmount()).isEqualByComparingTo(new BigDecimal("-0.001"));
		fundsTransfer = fundsTransferRepository
				.findByTransactionIdAndAccount(txId, destinationAccount).orElse(null);
		assertThat(fundsTransfer).isNotNull();
		assertThat(fundsTransfer.getAmount()).isEqualByComparingTo(new BigDecimal("0.001"));
	}

	@Test
	void handleTransactionStatusUpdatedWebhook_shouldReturnOkAndDoNothing_WhenDestinationAddressCorrespondsToOmnibusVault()
			throws Exception {
		String coin = "USDC_ETH_TEST5_AN74";
		String sourceAddress = "0xCa3d51259D32Cf715cDcfFAa16D065ECf6CBC4Ec";
		DepositWallet sourceAccount = accountRepository.save(DepositWallet.builder().user(user)
				.address(sourceAddress).assetId(coin).balance(BigDecimal.ONE)
				.lockedBalance(BigDecimal.ONE).openedAt(LocalDateTime.now())
				.build());

		// Trigger webhook notification
		String txId = assetCustodianService.createTransaction(coin,
				new SourceTransferPeerPath().id("53").type(TransferPeerPathType.VAULT_ACCOUNT),
				new DestinationTransferPeerPath().id("51").type(TransferPeerPathType.VAULT_ACCOUNT),
				new BigDecimal("0.001"));

		Thread.sleep(60000); // Wait for async processing (increase if needed)

		sourceAccount = accountRepository.findById(sourceAccount.getId()).orElse(null);
		assertThat(sourceAccount.getBalance()).isEqualByComparingTo(BigDecimal.ONE);
		FundsTransfer fundsTransfer = fundsTransferRepository
				.findByTransactionIdAndAccount(txId, sourceAccount).orElse(null);
		assertThat(fundsTransfer).isNull();
	}

}
