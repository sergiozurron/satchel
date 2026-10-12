package com.jejo.satchel.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.jejo.satchel.dto.LoanResponse;
import com.jejo.satchel.model.Loan;
import com.jejo.satchel.model.LoanStatus;
import com.jejo.satchel.model.User;

class LoanMapperTest {

	private final LoanMapper loanMapper = Mappers.getMapper(LoanMapper.class);

	@Test
	void toLoanResponses_mapsEveryFieldAndPreservesOrder() {
		User user = User.builder().id(1L).build();
		LocalDateTime newerGrantedAt = LocalDateTime.of(2026, 3, 2, 12, 0);
		LocalDateTime olderGrantedAt = LocalDateTime.of(2026, 1, 1, 8, 0);

		Loan newer = Loan.builder().id(2L).loanAssetId("USDC").collateralAssetId("ETH")
				.returnedAmount(BigDecimal.ZERO).amount(new BigDecimal("500.0000000"))
				.collateralAmount(new BigDecimal("0.5000000"))
				.interestRate(new BigDecimal("0.050000")).status(LoanStatus.ACTIVE)
				.accruedInterest(new BigDecimal("5.0000000")).grantedAt(newerGrantedAt).user(user)
				.build();

		Loan older = Loan.builder().id(1L).loanAssetId("ETH_TEST5").collateralAssetId("AVAXTEST")
				.returnedAmount(new BigDecimal("10.0000000")).amount(new BigDecimal("100.0000000"))
				.collateralAmount(new BigDecimal("1.0000000"))
				.interestRate(new BigDecimal("0.080000")).status(LoanStatus.PAID)
				.accruedInterest(new BigDecimal("1.0000000")).grantedAt(olderGrantedAt).user(user)
				.build();

		List<LoanResponse> result = loanMapper.toLoanResponses(List.of(newer, older));

		assertThat(result).hasSize(2);
		LoanResponse first = result.get(0);
		assertThat(first.getId()).isEqualTo(2L);
		assertThat(first.getLoanAssetId()).isEqualTo("USDC");
		assertThat(first.getAmount()).isEqualByComparingTo("500");
		assertThat(first.getReturnedAmount()).isEqualByComparingTo("0");
		assertThat(first.getOutstandingAmount()).isEqualByComparingTo("505");
		assertThat(first.getCollateralAssetId()).isEqualTo("ETH");
		assertThat(first.getCollateralAmount()).isEqualByComparingTo("0.5");
		assertThat(first.getInterestRate()).isEqualByComparingTo("0.05");
		assertThat(first.getStatus()).isEqualTo(LoanStatus.ACTIVE);
		assertThat(first.getAccruedInterest()).isEqualByComparingTo("5");
		assertThat(first.getGrantedAt()).isEqualTo(newerGrantedAt);

		assertThat(result.get(1).getId()).isEqualTo(1L);
		assertThat(result.get(1).getStatus()).isEqualTo(LoanStatus.PAID);
		assertThat(result.get(1).getOutstandingAmount()).isEqualByComparingTo("91");
	}

}
