package com.jejo.satchel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@AllArgsConstructor
public class ErrorResponse extends Response {
	
	@JsonProperty("error")
	private String type;
	
	public ErrorResponse(String type, String message) {
		super(message);
		this.type = type;
	}
}
