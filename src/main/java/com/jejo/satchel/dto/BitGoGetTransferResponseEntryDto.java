package com.jejo.satchel.dto;

import lombok.Data;

@Data
public class BitGoGetTransferResponseEntryDto {

	private String address;
	private String valueString;
	private String label;
	private Boolean isPayGo;
}
