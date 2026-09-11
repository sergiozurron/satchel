package com.jejo.satchel.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class WalletResponse {
    
    private Long id;
    private String address;
    private String assetId;
    private BigDecimal balance;
    private BigDecimal lockedBalance;
    private BigDecimal availableBalance;
    private BigDecimal accruedInterest;
    private LocalDateTime openedAt;
    private Long daysSinceOpened;
}
