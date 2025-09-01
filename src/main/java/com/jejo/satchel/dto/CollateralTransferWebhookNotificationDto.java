package com.jejo.satchel.dto;

import lombok.Data;

@Data
public class CollateralTransferWebhookNotificationDto {

	private String destinationAddress;
	private AmountInfo amountInfo;
	
	public class AmountInfo {
		private Double amount;
		
		public Double getAmount() {
			return amount;
		}
	}
}
