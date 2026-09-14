package com.jejo.satchel.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.jejo.satchel.dto.SignupRequest;
import com.jejo.satchel.model.User;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface AuthMapper {
	
	AuthMapper INSTANCE = Mappers.getMapper(AuthMapper.class);
	
	User toUserEntity(SignupRequest signupRequest);
}
