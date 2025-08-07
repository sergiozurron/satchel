package com.jejo.satchel.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EmailNotSentException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private String recipient;
}
