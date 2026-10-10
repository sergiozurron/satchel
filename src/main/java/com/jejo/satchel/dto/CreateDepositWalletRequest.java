package com.jejo.satchel.dto;

import com.jejo.satchel.validator.ValidAssetId;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data 
public class CreateDepositWalletRequest {
    @NotBlank(message = "Asset ID is required")
    @ValidAssetId
    private String assetId;
}
