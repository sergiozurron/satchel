package com.jejo.satchel.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PoolLiquidityResponse {

	private String assetId;
	private BigDecimal availableLiquidity;
}
