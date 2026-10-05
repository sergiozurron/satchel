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
public class FundsTransferControllerFullIntTest {

	@Autowired
	private FundsTransferRepository fundsTransferRepository;
	@Autowired
	private DepositWalletRepository depositWalletRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private AssetCustodianService assetCustodianService;

	private User user;

	@BeforeEach
	void setUp() {
		Long random = System.currentTimeMillis() % 100000;
		user = userRepository.save(User.builder().firstName("John").lastName("Doe")
				.email("email@email" + random).password("pass").vaultAccountId("53").verified(true).build());
	}

	@AfterEach
	void tearDown() {
		fundsTransferRepository.deleteAll();
		depositWalletRepository.deleteAll();
		userRepository.deleteAll();
	}

	// Requires manual steps to trigger webhook from Fireblocks

//	@Test
//	void handleTransactionStatusUpdatedWebhook_shouldReturnOkAndDoNothing_WhenDestinationAddressCorrespondsToOmnibusVault()
//			throws Exception {
//		String coin = "USDC_ETH_TEST5_AN74";
//		String sourceAddress = "0xCa3d51259D32Cf715cDcfFAa16D065ECf6CBC4Ec";
//		DepositWallet sourceAccount = depositWalletRepository.save(DepositWallet.builder().user(user)
//				.address(sourceAddress).assetId(coin).balance(BigDecimal.ONE)
//				.lockedBalance(BigDecimal.ZERO).openedAt(LocalDateTime.now())
//				.accruedInterest(BigDecimal.ZERO)
//				.build());
//
//		// Trigger webhook notification
//		String txId = assetCustodianService.createTransaction(coin,
//				new SourceTransferPeerPath().id("53").type(TransferPeerPathType.VAULT_ACCOUNT),
//				new DestinationTransferPeerPath().id("51").type(TransferPeerPathType.VAULT_ACCOUNT),
//				new BigDecimal("0.001"));
//
//		Thread.sleep(60000); // Wait for async processing (increase if needed)
//
//		sourceAccount = depositWalletRepository.findById(sourceAccount.getId()).orElse(null);
//		assertThat(sourceAccount.getBalance()).isEqualByComparingTo(BigDecimal.ONE);
//		FundsTransfer fundsTransfer = fundsTransferRepository
//				.findByTransactionIdAndDepositWallet(txId, sourceAccount).orElse(null);
//		assertThat(fundsTransfer).isNull();
//	}

}
