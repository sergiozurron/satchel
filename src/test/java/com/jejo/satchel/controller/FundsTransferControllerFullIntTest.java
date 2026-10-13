package com.jejo.satchel.controller;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.jejo.satchel.repository.DepositWalletRepository;
import com.jejo.satchel.repository.FundsTransferRepository;
import com.jejo.satchel.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
public class FundsTransferControllerFullIntTest {

	@Autowired
	private FundsTransferRepository fundsTransferRepository;
	@Autowired
	private DepositWalletRepository depositWalletRepository;
	@Autowired
	private UserRepository userRepository;

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
