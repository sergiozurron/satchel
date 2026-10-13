package com.jejo.satchel.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.jejo.satchel.dto.FundsTransferResponse;
import com.jejo.satchel.model.FundsTransfer;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface FundsTransferMapper {

	FundsTransferMapper INSTANCE = Mappers.getMapper(FundsTransferMapper.class);

	@Mapping(target = "walletId", source = "depositWallet.id")
	@Mapping(target = "assetId", source = "depositWallet.assetId")
	FundsTransferResponse toFundsTransferResponse(FundsTransfer fundsTransfer);

	List<FundsTransferResponse> toFundsTransferResponses(List<FundsTransfer> fundsTransfers);

}
