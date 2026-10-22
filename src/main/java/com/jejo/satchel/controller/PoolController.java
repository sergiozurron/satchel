package com.jejo.satchel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jejo.satchel.dto.PoolLiquidityResponse;
import com.jejo.satchel.service.AssetCustodianService;
import com.jejo.satchel.validator.ValidAssetId;

@Validated
@RequestMapping("/api/v1/pool")
@RestController
public class PoolController {

	private final AssetCustodianService assetCustodianService;

	public PoolController(AssetCustodianService assetCustodianService) {
		this.assetCustodianService = assetCustodianService;
	}

	@GetMapping("/{assetId}")
	public ResponseEntity<PoolLiquidityResponse> getPoolLiquidity(
			@ValidAssetId @PathVariable String assetId) {
		return ResponseEntity.ok(new PoolLiquidityResponse(assetId,
				assetCustodianService.getPoolSize(assetId)));
	}

}
