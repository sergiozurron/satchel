package com.jejo.satchel.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data 
public class CreateDepositWalletRequest {
    @Pattern(regexp = "USDC_ETH_TEST5_AN74|ETH_TEST5", message = "Unsupported coin. Only USDC and ETH sepolia are allowed for now")
    private String assetId;
}
