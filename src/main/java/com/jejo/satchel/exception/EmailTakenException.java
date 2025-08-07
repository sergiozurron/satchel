package com.jejo.satchel.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EmailTakenException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public EmailTakenException(String email) {
		super("Email is already taken: " + email);
	}
}
