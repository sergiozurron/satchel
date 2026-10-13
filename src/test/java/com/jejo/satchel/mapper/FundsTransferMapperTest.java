package com.jejo.satchel.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.jejo.satchel.dto.FundsTransferResponse;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.FundsTransfer;
import com.jejo.satchel.model.User;

class FundsTransferMapperTest {

	private final FundsTransferMapper fundsTransferMapper = Mappers.getMapper(FundsTransferMapper.class);

	@Test
	void toFundsTransferResponse_mapsEveryField() {
		LocalDateTime timestamp = LocalDateTime.of(2026, 6, 2, 15, 0);
		DepositWallet wallet = DepositWallet.builder().id(8L).address("0x1111111111111111")
				.assetId("USDC_ETH_TEST5_AN74").user(User.builder().id(1L).password("secret").build()).build();
		FundsTransfer transfer = FundsTransfer.builder().id(4L).amount(new BigDecimal("-25.50"))
				.timestamp(timestamp).transactionId("tx-pending").counterpartyAddress("0xexternal")
				.isCompleted(false).depositWallet(wallet).build();

		FundsTransferResponse response = fundsTransferMapper.toFundsTransferResponse(transfer);

		assertThat(response.getId()).isEqualTo(4L);
		assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("-25.50"));
		assertThat(response.getTimestamp()).isEqualTo(timestamp);
		assertThat(response.getTransactionId()).isEqualTo("tx-pending");
		assertThat(response.getCounterpartyAddress()).isEqualTo("0xexternal");
		assertThat(response.getIsCompleted()).isFalse();
		assertThat(response.getWalletId()).isEqualTo(8L);
		assertThat(response.getAssetId()).isEqualTo("USDC_ETH_TEST5_AN74");
	}

	@Test
	void toFundsTransferResponse_mapsNullTransactionId() {
		DepositWallet wallet = DepositWallet.builder().id(3L).assetId("ETH_TEST5").build();
		FundsTransfer transfer = FundsTransfer.builder().id(9L).amount(new BigDecimal("100"))
				.timestamp(LocalDateTime.of(2026, 1, 1, 8, 0)).transactionId(null)
				.counterpartyAddress("0xfaucet").isCompleted(true).depositWallet(wallet).build();

		FundsTransferResponse response = fundsTransferMapper.toFundsTransferResponse(transfer);

		assertThat(response.getTransactionId()).isNull();
		assertThat(response.getIsCompleted()).isTrue();
		assertThat(response.getWalletId()).isEqualTo(3L);
		assertThat(response.getAssetId()).isEqualTo("ETH_TEST5");
	}

}
