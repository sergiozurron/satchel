package com.jejo.satchel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jejo.satchel.dto.Response;

@RestController
@RequestMapping("/api/v1")
public class HomeController {

	@GetMapping
	public ResponseEntity<Response> home() {
		return ResponseEntity.ok(new Response("Welcome to the Satchel API."));
	}

}
