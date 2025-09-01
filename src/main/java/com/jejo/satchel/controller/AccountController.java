package com.jejo.satchel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jejo.satchel.service.AccountService;

@RequestMapping("/api/v1/accounts")
@RestController
public class AccountController {

	private final AccountService accountService;
	
	public AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@PostMapping
	public ResponseEntity<Void> createAccount() {
		accountService.createUserAccounts();
		return ResponseEntity.ok(null);
	}
}
