package com.jejo.satchel.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.jejo.satchel.dto.LoanResponse;
import com.jejo.satchel.model.Loan;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface LoanMapper {

	LoanMapper INSTANCE = Mappers.getMapper(LoanMapper.class);

	LoanResponse toLoanResponse(Loan loan);

	List<LoanResponse> toLoanResponses(List<Loan> loans);

}
