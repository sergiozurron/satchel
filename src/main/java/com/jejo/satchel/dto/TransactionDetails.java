package com.jejo.satchel.dto;

import com.fireblocks.sdk.model.AmountInfo;
import com.fireblocks.sdk.model.DestinationTransferPeerPathResponse;
import com.fireblocks.sdk.model.SourceTransferPeerPathResponse;

import lombok.Data;

@Data
public class TransactionDetails {

	private String id;
	private String assetId;
	private String status;
	private String subStatus;
	private SourceTransferPeerPathResponse source;
	private DestinationTransferPeerPathResponse destination;
	private String sourceAddress;
	private String destinationAddress;
	private AmountInfo amountInfo;
}
