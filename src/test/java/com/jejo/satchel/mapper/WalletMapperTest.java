package com.jejo.satchel.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.jejo.satchel.dto.WalletResponse;
import com.jejo.satchel.model.DepositWallet;
import com.jejo.satchel.model.User;

class WalletMapperTest {

	private final WalletMapper walletMapper = Mappers.getMapper(WalletMapper.class);

	@Test
	void toWalletResponse_mapsEveryField() {
		LocalDateTime openedAt = LocalDateTime.now().minusDays(7);
		DepositWallet wallet = DepositWallet.builder().id(5L).address("0x5555555555555555")
				.assetId("USDC_ETH_TEST5_AN74").balance(new BigDecimal("250.75"))
				.lockedBalance(new BigDecimal("50.25")).accruedInterest(new BigDecimal("5.00"))
				.openedAt(openedAt).user(User.builder().id(1L).build()).build();

		WalletResponse response = walletMapper.toWalletResponse(wallet);

		assertThat(response.getId()).isEqualTo(5L);
		assertThat(response.getAddress()).isEqualTo("0x5555555555555555");
		assertThat(response.getAssetId()).isEqualTo("USDC_ETH_TEST5_AN74");
		assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("250.75"));
		assertThat(response.getLockedBalance()).isEqualByComparingTo(new BigDecimal("50.25"));
		assertThat(response.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("200.50"));
		assertThat(response.getAccruedInterest()).isEqualByComparingTo(new BigDecimal("5.00"));
		assertThat(response.getOpenedAt()).isEqualTo(openedAt);
		assertThat(response.getDaysSinceOpened()).isEqualTo(7L);
	}

	@Test
	void toWalletResponse_mapsAvailableBalance() {
		DepositWallet wallet = DepositWallet.builder().id(10L).address("0xabcdabcdabcdabcd")
				.assetId("ETH_TEST5").balance(new BigDecimal("50.00"))
				.lockedBalance(new BigDecimal("15.00")).accruedInterest(BigDecimal.ZERO)
				.openedAt(LocalDateTime.now().minusDays(3)).build();

		WalletResponse response = walletMapper.toWalletResponse(wallet);

		assertThat(response.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("35.00"));
	}

}
