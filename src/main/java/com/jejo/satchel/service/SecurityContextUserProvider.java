package com.jejo.satchel.service;

import org.springframework.security.core.context.SecurityContextHolder;

import com.jejo.satchel.model.User;

public class SecurityContextUserProvider implements CurrentUserProvider {
	
	public User getCurrentUser() {
		return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();		
	}

}
