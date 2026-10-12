package com.jejo.satchel.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.jejo.satchel.dto.WalletResponse;
import com.jejo.satchel.model.DepositWallet;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface WalletMapper {

	WalletMapper INSTANCE = Mappers.getMapper(WalletMapper.class);

	@Mapping(target = "daysSinceOpened", expression = "java(wallet.daysSinceOpened())")
	WalletResponse toWalletResponse(DepositWallet wallet);

	List<WalletResponse> toWalletResponses(List<DepositWallet> wallets);

}
