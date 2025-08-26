package com.jejo.satchel.dto;

import java.util.List;

import lombok.Data;

@Data
public class BitGoGetTransferResponseDto {

	private String coin; // e.g. "usdt"
	private List<BitGoGetTransferResponseEntryDto> entries;
}
