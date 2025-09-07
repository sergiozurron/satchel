package com.jejo.satchel.controller;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.jejo.satchel.dto.ErrorResponse;
import com.jejo.satchel.exception.AccountsAlreadyCreatedException;
import com.jejo.satchel.exception.AssetCustodianApiException;
import com.jejo.satchel.exception.EmailTakenException;
import com.jejo.satchel.exception.EmailVerificationTokenExpiredException;
import com.jejo.satchel.exception.EmailVerificationTokenNotFoundException;
import com.jejo.satchel.exception.EmailVerificationTokenUsedException;
import com.jejo.satchel.exception.UnverifiedWebhookException;
import com.jejo.satchel.exception.UserAlreadyVerifiedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	public static final String ERROR_EMAIL_TAKEN = "email_taken";
	public static final String ERROR_EMAIL_VERIFICATION_TOKEN_EXPIRED = "evt_expired";
	public static final String ERROR_EMAIL_VERIFICATION_TOKEN_NOT_FOUND = "evt_not_found";
	public static final String ERROR_EMAIL_VERIFICATION_TOKEN_USED = "evt_used";
	public static final String ERROR_USER_ALREADY_VERIFIED = "user_already_verified";
	public static final String ERROR_UNAUTHORIZED = "unauthorized";
	public static final String ERROR_INTERNAL_SERVER_ERROR = "internal_server_error";
	public static final String ERROR_UNVERIFIED_WEBHOOK = "unverified_webhook";
	public static final String ERROR_ACCOUNTS_ALREADY_CREATED = "accounts_already_created";

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Set<ErrorResponse>> handleSyntacticalValidationException(MethodArgumentNotValidException ex) {
		Set<ErrorResponse> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(fieldError -> new ErrorResponse(fieldError.getCode(), fieldError.getDefaultMessage()))
				.collect(Collectors.toSet());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
	}

	@ExceptionHandler(EmailTakenException.class)
	public ResponseEntity<ErrorResponse> handleEmailTakenException(EmailTakenException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ERROR_EMAIL_TAKEN, ex.getMessage()));
	}
	
	@ExceptionHandler(EmailVerificationTokenNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleEmailVerificationTokenNotFoundException(EmailVerificationTokenNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse(ERROR_EMAIL_VERIFICATION_TOKEN_NOT_FOUND, ex.getMessage()));
	}
	
	@ExceptionHandler(EmailVerificationTokenExpiredException.class)
	public ResponseEntity<ErrorResponse> handleEmailVerificationTokenExpiredException(EmailVerificationTokenExpiredException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(ERROR_EMAIL_VERIFICATION_TOKEN_EXPIRED, ex.getMessage()));
	}
	
	@ExceptionHandler(EmailVerificationTokenUsedException.class)
	public ResponseEntity<ErrorResponse> handleEmailVerificationTokenUsedException(EmailVerificationTokenUsedException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(ERROR_EMAIL_VERIFICATION_TOKEN_USED, ex.getMessage()));
	}
	
	@ExceptionHandler(UserAlreadyVerifiedException.class)
	public ResponseEntity<ErrorResponse> handleUserAlreadyVerifiedException(UserAlreadyVerifiedException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(ERROR_USER_ALREADY_VERIFIED, ex.getMessage()));
	}
	
	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ErrorResponse(ERROR_UNAUTHORIZED, ex.getMessage()));
	}
	
	@ExceptionHandler(UnverifiedWebhookException.class)
	public ResponseEntity<ErrorResponse> handleUnverifiedWebhookException(UnverifiedWebhookException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(ERROR_UNVERIFIED_WEBHOOK, ex.getMessage()));
	}
	
	@ExceptionHandler(AccountsAlreadyCreatedException.class)
	public ResponseEntity<ErrorResponse> handleAccountsAlreadyCreatedException(AccountsAlreadyCreatedException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(ERROR_ACCOUNTS_ALREADY_CREATED, ex.getMessage()));
	}
	
	@ExceptionHandler(AssetCustodianApiException.class)
	public ResponseEntity<ErrorResponse> handleAssetCustodianApiException(AssetCustodianApiException ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse(ERROR_INTERNAL_SERVER_ERROR, "Asset custodian API error: " + ex.getMessage()));
	}
	
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse(ERROR_INTERNAL_SERVER_ERROR, "An unexpected error occurred: " + ex.getMessage()));
	}
	
}
